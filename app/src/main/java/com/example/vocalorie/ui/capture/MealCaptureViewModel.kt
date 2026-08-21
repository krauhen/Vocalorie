package com.example.vocalorie.ui.capture

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.vocalorie.AppContainer
import com.example.vocalorie.ai.EstimationProgress
import com.example.vocalorie.ai.KoogNutritionAgent
import com.example.vocalorie.ai.NutritionAgentException
import com.example.vocalorie.ai.NutritionEstimator
import com.example.vocalorie.ai.TipRewordingAgent
import com.example.vocalorie.data.CachedMealMatch
import com.example.vocalorie.data.repository.ActivityRepository
import com.example.vocalorie.data.repository.BackupRepository
import com.example.vocalorie.data.repository.MealCacheRepository
import com.example.vocalorie.data.repository.MealRepository
import com.example.vocalorie.data.repository.SecretRepository
import com.example.vocalorie.data.repository.ThemeSettingsRepository
import com.example.vocalorie.data.repository.ThemeSettingsSnapshot
import com.example.vocalorie.data.toEditableDraft
import com.example.vocalorie.model.ActivityDraftValidation
import com.example.vocalorie.model.EditableActivityDraft
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.NutritionGoals
import com.example.vocalorie.model.NutritionGoalsParseResult
import com.example.vocalorie.model.SavedActivity
import com.example.vocalorie.model.SavedMeal
import com.example.vocalorie.model.validate
import com.example.vocalorie.settings.NutritionSettingsStore
import com.example.vocalorie.settings.ToolSettings
import com.example.vocalorie.ui.entries.EntriesTab
import com.example.vocalorie.ui.entries.dayOffsetAfterDayChange
import com.example.vocalorie.ui.entries.filterActivitiesForDay
import com.example.vocalorie.ui.entries.filterMealsForDay
import com.example.vocalorie.ui.entries.selectedDayTimestampMillis
import com.example.vocalorie.ui.entries.stats.dayScoreTips
import com.example.vocalorie.ui.entries.stats.validateRewordedTips
import com.example.vocalorie.ui.entries.toDailyNutritionTotals
import com.example.vocalorie.ui.settings.SettingsEvent
import com.example.vocalorie.ui.settings.ThemeColorSlot
import com.example.vocalorie.ui.voice.GalleryImageAttachment
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * The capture flow's state holder: it owns [MealCaptureUiState] and every operation that changes it.
 *
 * What moved in here, and why it matters:
 * - Seven repository-shaped functions that used to be declared *inside* the screen composable,
 *   closing over `LocalContext` and thirty-five mutable slots. They are repository calls now.
 * - Every write, which used to run on `rememberCoroutineScope()` — so rotating the device cancelled
 *   an OpenAI request the user was paying for and threw away the reviewed draft. On [viewModelScope]
 *   the request outlives the composable and its result still lands.
 * - Nine manual "re-read every table" refreshes, replaced by observing the two repository `Flow`s.
 *
 * What deliberately stayed outside: the business rules. [planEstimate], [NutritionGoals.parse] and
 * [validate] are pure functions this class *calls*; folding them in would mean constructing a whole
 * view model and six repositories to test a percentage calculation.
 *
 * [clock] and [zone] are injected for the same reason the time-window helpers already inject them —
 * nothing here reads the ambient clock, so every timestamped path is testable.
 */
class MealCaptureViewModel(
    private val mealRepository: MealRepository,
    private val activityRepository: ActivityRepository,
    private val mealCacheRepository: MealCacheRepository,
    private val themeSettingsRepository: ThemeSettingsRepository,
    private val secretRepository: SecretRepository,
    private val backupRepository: BackupRepository,
    private val nutritionEstimator: NutritionEstimator,
    private val tipRewordingAgent: TipRewordingAgent,
    initialSettings: ThemeSettingsSnapshot,
    initialRuntimeApiKey: String = "",
    private val clock: () -> Instant = Instant::now,
    val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MealCaptureUiState(
            mealThemeColors = initialSettings.mealColors,
            activityThemeColors = initialSettings.activityColors,
            baseCaloriesBurned = initialSettings.baseCaloriesBurned,
            kcalPerStep = initialSettings.kcalPerStep,
            nutritionGoals = initialSettings.nutritionGoals,
            tipRotationSeconds = initialSettings.tipRotationSeconds,
            now = clock(),
            runtimeApiKey = initialRuntimeApiKey,
        ),
    )

    val uiState: StateFlow<MealCaptureUiState> = _uiState.asStateFlow()

    private val state: MealCaptureUiState get() = _uiState.value

    /**
     * The running estimate, kept so a second Estimate tap during the cache lookup cannot fire a
     * duplicate — and a billable — request.
     */
    private var estimateJob: Job? = null

    init {
        viewModelScope.launch {
            mealRepository.observeMeals().collect { meals -> update { it.copy(savedMeals = meals) } }
        }
        viewModelScope.launch {
            activityRepository.observeActivities().collect { activities ->
                update { it.copy(savedActivities = activities) }
            }
        }
        viewModelScope.launch { loadThemeSettings() }
        viewModelScope.launch { loadSavedKeyLabel() }
        viewModelScope.launch { loadToolSettings() }
    }

    // --- Settings-derived state -------------------------------------------------------------

    private suspend fun loadThemeSettings() {
        val snapshot = themeSettingsRepository.snapshot()
        update {
            it.copy(
                mealThemeColors = snapshot.mealColors,
                activityThemeColors = snapshot.activityColors,
                baseCaloriesBurned = snapshot.baseCaloriesBurned,
                kcalPerStep = snapshot.kcalPerStep,
                nutritionGoals = snapshot.nutritionGoals,
                tipRotationSeconds = snapshot.tipRotationSeconds,
            )
        }
    }

    private suspend fun loadSavedKeyLabel() {
        val status = secretRepository.openAiKeyStatus()
        update { it.copy(savedKeyLabel = status.label) }
    }

    private suspend fun loadToolSettings(): ToolSettings {
        val status = secretRepository.toolSettingsStatus()
        update { it.copy(toolSettings = status.settings, braveKeyLabel = status.braveKeyLabel) }
        return status.settings
    }

    // --- Clock -----------------------------------------------------------------------------

    /**
     * Re-reads the wall clock and nothing else.
     *
     * This is all pull-to-refresh has to do now: entry data arrives from the database's own change
     * notification, so the only thing a manual refresh can still change is which entries count as
     * already passed.
     */
    fun refreshNow() {
        advanceNow()
    }

    // Also called by `newEntryTimestampMillis()`, so a save crossing midnight re-anchors the
    // selected-day offset too — that is intentional: the entry lands on the day it was actually
    // saved on rather than on a stale offset computed before midnight.
    private fun advanceNow(): Instant {
        val previous = state.now
        val reading = clock()
        val daysPassed = ChronoUnit.DAYS.between(LocalDate.ofInstant(previous, zone), LocalDate.ofInstant(reading, zone))
        update {
            it.copy(
                now = reading,
                selectedDayOffset = if (daysPassed == 0L) it.selectedDayOffset else dayOffsetAfterDayChange(it.selectedDayOffset, daysPassed),
            )
        }
        return reading
    }

    /**
     * Timestamp for a new entry: the currently-viewed day at the current wall-clock time (offset 0 =
     * today ≈ now). Editing an existing entry keeps its own stored timestamp.
     */
    private fun newEntryTimestampMillis(): Long =
        selectedDayTimestampMillis(state.selectedDayOffset, advanceNow(), zone)

    // --- Routing ---------------------------------------------------------------------------

    fun openSettings() = update { it.copy(showSettings = true) }

    fun closeSettings() = update { it.copy(showSettings = false) }

    fun selectTab(tab: EntriesTab) = update { it.copy(selectedTab = tab) }

    fun selectDayOffset(dayOffset: Int) = update { it.copy(selectedDayOffset = dayOffset) }

    /** The system back press, closing exactly one thing, outermost first. */
    fun onBack() {
        val current = state
        when {
            current.showSettings -> update { it.copy(showSettings = false) }
            current.approvalMatch != null -> update { it.copy(approvalMatch = null, pendingEstimateRequest = null) }
            current.showActivityOverlay -> update {
                it.copy(showActivityOverlay = false, selectedActivity = null, selectedActivityDraft = null)
            }
            current.selectedMeal != null -> update { it.copy(selectedMeal = null, selectedDraft = null) }
        }
    }

    // --- New-meal capture ------------------------------------------------------------------

    fun onQueryChange(query: String) = update { it.copy(query = query) }

    fun onDraftChange(draft: EditableMealDraft) = update { it.copy(draft = draft) }

    fun onSearchQueryChange(searchQuery: String) = update { it.copy(searchQuery = searchQuery) }

    fun onImagesChange(images: List<GalleryImageAttachment>) = update {
        it.copy(
            draft = null,
            error = null,
            diagnostic = null,
            groundingWarning = null,
            saveMessage = null,
            attachedImages = images,
        )
    }

    fun resetNewMealEstimate() {
        update {
            it.copy(
                query = "",
                searchQuery = "",
                draft = null,
                error = null,
                diagnostic = null,
                groundingWarning = null,
                saveMessage = null,
                attachedImages = emptyList(),
                resetSignal = it.resetSignal + 1,
            )
        }
    }

    /** Reuse a meal picked from the history search: offered for approval, never re-estimated. */
    fun onSearchMealClick(meal: SavedMeal) = update {
        it.copy(
            error = null,
            diagnostic = null,
            groundingWarning = null,
            saveMessage = null,
            searchQuery = "",
            approvalMatch = CachedMealMatch(meal = meal, draft = meal.toEditableDraft()),
            pendingEstimateRequest = null,
        )
    }

    /**
     * Tapping Estimate.
     *
     * The cache is consulted *first*, by a keyed lookup, and the model is only reached when nothing
     * is cached under the request's key. That ordering is the whole point: the cache read used to be
     * a synchronous scan of a table held in Compose state, so a tap before that state had loaded
     * missed a warm cache and paid for an estimate the app already had the answer to.
     */
    fun onEstimate() {
        if (estimateJob?.isActive == true) return
        update {
            it.copy(error = null, diagnostic = null, groundingWarning = null, saveMessage = null, draft = null)
        }
        estimateJob = viewModelScope.launch {
            val current = state
            val cachedMatch = mealCacheRepository.findCachedMeal(
                estimateRequestQuery(current.query, current.attachedImages),
            )
            when (val plan = planEstimate(current.query, current.attachedImages, cachedMatch)) {
                is EstimatePlan.ServeFromCache -> update {
                    it.copy(approvalMatch = plan.match, pendingEstimateRequest = plan.pendingRequest)
                }
                is EstimatePlan.MissingInput -> update { it.copy(error = plan.message) }
                is EstimatePlan.RunEstimate -> runEstimate(plan.request)
            }
        }
    }

    private suspend fun runEstimate(request: EstimateRequest) {
        update { it.copy(isLoading = true) }
        try {
            val keyForEstimate = secretRepository.openAiApiKey() ?: state.runtimeApiKey
            if (keyForEstimate.isBlank()) {
                update { it.copy(error = "Enter an OpenAI API key or save one in Settings.") }
                return
            }
            loadSavedKeyLabel()
            val settingsForEstimate = loadToolSettings()
            val outcome = nutritionEstimator.estimate(
                openAiApiKey = keyForEstimate,
                query = request.requestQuery,
                toolSettings = settingsForEstimate,
                imageAttachments = request.imageAttachments,
                onProgress = { step -> update { it.copy(estimationProgress = step) } },
            )
            val estimated = outcome.result.toEditableDraft()
                .copy(query = request.finalDraftQuery.ifBlank { request.requestQuery })
            val resolved = mealCacheRepository.resolveItemsFromCache(estimated)
            update { current ->
                current.copy(
                    draft = resolved,
                    groundingWarning = outcome.groundingFailureMessage,
                    diagnostic = outcome.groundingFailureDiagnostic
                        ?.let { listOfNotNull(current.diagnostic, it).joinToString("\n\n") }
                        ?: current.diagnostic,
                )
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (throwable: NutritionAgentException) {
            update { current ->
                current.copy(
                    error = throwable.message ?: ESTIMATE_FAILED_MESSAGE,
                    diagnostic = listOfNotNull(throwable.diagnostic, current.estimationProgress?.toDiagnosticNote())
                        .joinToString("\n\n"),
                )
            }
        } catch (throwable: Throwable) {
            update { current ->
                current.copy(
                    error = throwable.message ?: ESTIMATE_FAILED_MESSAGE,
                    diagnostic = listOfNotNull(current.diagnostic, current.estimationProgress?.toDiagnosticNote())
                        .joinToString("\n\n")
                        .ifBlank { current.diagnostic },
                )
            }
        } finally {
            update { it.copy(isLoading = false, estimationProgress = null, pendingEstimateRequest = null) }
        }
    }

    fun saveNewMeal(mealDraft: EditableMealDraft) {
        update { it.copy(error = null, diagnostic = null, groundingWarning = null, saveMessage = null) }
        if (mealDraft.query.isBlank()) {
            update { it.copy(error = BLANK_MEAL_DESCRIPTION_MESSAGE) }
            return
        }
        viewModelScope.launch {
            update { it.copy(isSaving = true) }
            try {
                val savedId = mealRepository.saveReviewedMeal(mealDraft, newEntryTimestampMillis())
                val saved = mealRepository.meals().firstOrNull { it.id == savedId }
                update {
                    it.copy(
                        selectedMeal = saved,
                        selectedDraft = saved?.toEditableDraft(),
                        draft = null,
                        query = "",
                        attachedImages = emptyList(),
                        saveMessage = "Saved meal to local entries.",
                    )
                }
            } catch (throwable: Throwable) {
                update { it.copy(error = throwable.message ?: "Could not save meal locally.") }
            } finally {
                update { it.copy(isSaving = false) }
            }
        }
    }

    // --- Cached-meal approval --------------------------------------------------------------

    fun approveCachedMeal() {
        val match = state.approvalMatch ?: return
        update {
            it.copy(
                query = match.draft.query.ifBlank { match.meal.query },
                draft = match.draft,
                error = null,
                diagnostic = null,
                groundingWarning = null,
                saveMessage = null,
                approvalMatch = null,
                pendingEstimateRequest = null,
            )
        }
    }

    /** Declining the cached draft runs the estimate that was held back, if there was one. */
    fun rejectCachedMeal() {
        val request = state.pendingEstimateRequest
        update { it.copy(approvalMatch = null, pendingEstimateRequest = null) }
        if (request != null) {
            update { it.copy(draft = null) }
            estimateJob = viewModelScope.launch { runEstimate(request) }
        }
    }

    fun dismissApproval() = update { it.copy(approvalMatch = null, pendingEstimateRequest = null) }

    // --- Saved-meal detail overlay ---------------------------------------------------------

    fun openMeal(meal: SavedMeal) = update {
        it.copy(selectedMeal = meal, selectedDraft = meal.toEditableDraft(), error = null, saveMessage = null)
    }

    fun onSelectedDraftChange(draft: EditableMealDraft) = update { it.copy(selectedDraft = draft) }

    fun saveSelectedMeal(mealDraft: EditableMealDraft, onSaved: () -> Unit) {
        val meal = state.selectedMeal ?: return
        update { it.copy(error = null, saveMessage = null) }
        if (mealDraft.query.isBlank()) {
            update { it.copy(error = BLANK_MEAL_DESCRIPTION_MESSAGE) }
            return
        }
        viewModelScope.launch {
            update { it.copy(isSaving = true) }
            try {
                mealRepository.updateReviewedMeal(
                    id = meal.id,
                    draft = mealDraft,
                    createdAtEpochMillis = mealDraft.createdAtEpochMillis ?: meal.createdAtEpochMillis,
                )
                val updated = mealRepository.meals().firstOrNull { it.id == meal.id }
                update {
                    it.copy(
                        selectedMeal = updated,
                        selectedDraft = updated?.toEditableDraft(),
                        saveMessage = "Updated saved meal.",
                    )
                }
                onSaved()
            } catch (throwable: Throwable) {
                update { it.copy(error = throwable.message ?: "Could not update meal locally.") }
            } finally {
                update { it.copy(isSaving = false) }
            }
        }
    }

    fun deleteSelectedMeal() {
        val meal = state.selectedMeal ?: return
        viewModelScope.launch {
            update { it.copy(isSaving = true) }
            try {
                mealRepository.deleteMeal(meal.id)
                update {
                    it.copy(selectedMeal = null, selectedDraft = null, saveMessage = "Deleted saved meal.")
                }
            } catch (throwable: Throwable) {
                update { it.copy(error = throwable.message ?: "Could not delete meal locally.") }
            } finally {
                update { it.copy(isSaving = false) }
            }
        }
    }

    fun dismissSelectedMeal() = update { it.copy(selectedMeal = null, selectedDraft = null) }

    // --- Activity editor overlay -----------------------------------------------------------

    fun openActivityEditor(activity: SavedActivity?) {
        val draft = activity?.toEditableDraft() ?: EditableActivityDraft(
            type = null,
            title = "",
            description = "",
            caloriesBurnedKcal = "",
            durationMinutes = "",
            steps = "",
            createdAtEpochMillis = newEntryTimestampMillis(),
        )
        update {
            it.copy(
                selectedActivity = activity,
                selectedActivityDraft = draft,
                showActivityOverlay = true,
                activityError = null,
                activityMessage = null,
            )
        }
    }

    fun onActivityDraftChange(draft: EditableActivityDraft) = update { it.copy(selectedActivityDraft = draft) }

    fun dismissActivityEditor() = update {
        it.copy(
            showActivityOverlay = false,
            selectedActivity = null,
            selectedActivityDraft = null,
            activityError = null,
            activityMessage = null,
        )
    }

    fun saveActivity(activityDraft: EditableActivityDraft, onSaved: () -> Unit) {
        update { it.copy(activityError = null, activityMessage = null) }
        when (val validated = activityDraft.validate(state.kcalPerStep)) {
            is ActivityDraftValidation.Invalid -> update { it.copy(activityError = validated.message) }
            is ActivityDraftValidation.Valid -> viewModelScope.launch {
                update { it.copy(isSaving = true) }
                try {
                    val existing = state.selectedActivity
                    val createdAtEpochMillis = activityDraft.createdAtEpochMillis ?: clock().toEpochMilli()
                    if (existing == null) {
                        activityRepository.saveActivity(activityDraft, validated, createdAtEpochMillis)
                        update { it.copy(activityMessage = "Saved activity to local entries.") }
                        dismissActivityEditor()
                    } else {
                        activityRepository.updateActivity(existing.id, activityDraft, validated, createdAtEpochMillis)
                        val updated = activityRepository.activities().firstOrNull { it.id == existing.id }
                        update {
                            it.copy(
                                selectedActivity = updated,
                                selectedActivityDraft = updated?.toEditableDraft(),
                                activityMessage = "Updated activity.",
                            )
                        }
                        onSaved()
                    }
                } catch (throwable: Throwable) {
                    update { it.copy(activityError = throwable.message ?: "Could not save activity locally.") }
                } finally {
                    update { it.copy(isSaving = false) }
                }
            }
        }
    }

    fun deleteActivity() {
        viewModelScope.launch {
            update { it.copy(isSaving = true) }
            try {
                state.selectedActivity?.let { activityRepository.deleteActivity(it.id) }
                dismissActivityEditor()
                update { it.copy(activityMessage = "Deleted activity.") }
            } catch (throwable: Throwable) {
                update { it.copy(activityError = throwable.message ?: "Could not delete activity locally.") }
            } finally {
                update { it.copy(isSaving = false) }
            }
        }
    }

    // --- Day-score tips ------------------------------------------------------------------

    /**
     * Asks the model to reword the current rule tips, and keeps them unchanged on anything else.
     *
     * Explicit only (design D4) — never called on a recomposition or a data change. Every failure
     * mode is silent by design: nothing was promised, so nothing visibly fails. A reply is accepted
     * wholesale or not at all.
     */
    fun rewordDayScoreTips() {
        val ruleTips = state.dayScoreTips
        if (ruleTips.isEmpty() || state.tipsRewordingInFlight) return
        update { it.copy(tipsRewordingInFlight = true) }
        viewModelScope.launch {
            try {
                val key = secretRepository.openAiApiKey() ?: state.runtimeApiKey
                val reply = runCatching {
                    tipRewordingAgent.reword(
                        openAiApiKey = key,
                        toolSettings = state.toolSettings,
                        tips = ruleTips.map { it.text },
                        dayContext = dayContextForRewording(),
                    )
                }.getOrNull().orEmpty()
                update { it.copy(dayScoreTips = validateRewordedTips(ruleTips, reply)) }
            } finally {
                update { it.copy(tipsRewordingInFlight = false) }
            }
        }
    }

    /** The day's numbers, so the model rewords with the same facts the rule tips were derived from. */
    private fun dayContextForRewording(): String {
        val current = state
        val totals = filterMealsForDay(current.savedMeals, 0, current.now, zone).toDailyNutritionTotals()
        val goals = current.nutritionGoals
        val targets = goals.macroTargets()
        return "%.0f of %d kcal, protein %.0f/%.0f g, carbs %.0f/%.0f g, fat %.0f/%.0f g".format(
            totals.caloriesKcal, goals.calorieGoalKcal,
            totals.proteinG, targets.proteinG,
            totals.carbsG, targets.carbsG,
            totals.fatG, targets.fatG,
        )
    }

    // --- Settings events -------------------------------------------------------------------

    /**
     * The settings screen's single entry point. Exhaustive over [SettingsEvent], so adding a control
     * to that screen cannot compile until this decides what the control does.
     */
    fun onSettingsEvent(event: SettingsEvent) {
        when (event) {
            is SettingsEvent.SaveColor -> saveThemeColor(event.slot, event.color)
            is SettingsEvent.SaveBaseCaloriesBurned -> saveBaseCaloriesBurned(event.input)
            is SettingsEvent.SaveTipRotationSeconds -> saveTipRotationSeconds(event.input)
            is SettingsEvent.SaveKcalPer1000Steps -> saveKcalPerStep(event.input)
            is SettingsEvent.SaveNutritionGoals ->
                saveNutritionGoals(event.calorieGoal, event.proteinPercent, event.carbsPercent)
            is SettingsEvent.RuntimeApiKeyChanged -> onRuntimeApiKeyChange(event.apiKey)
            is SettingsEvent.SaveOpenAiKey -> saveOpenAiKey(event.key)
            SettingsEvent.ClearOpenAiKey -> clearOpenAiKey()
            is SettingsEvent.SaveBraveKey -> saveBraveKey(event.key)
            SettingsEvent.ClearBraveKey -> clearBraveKey()
            is SettingsEvent.SaveMaxResearchToolCalls -> saveMaxResearchToolCalls(event.input)
            is SettingsEvent.SaveMaxAgentIterations -> saveMaxAgentIterations(event.input)
            is SettingsEvent.SaveOpenAiModelChoice -> saveOpenAiModelChoice(event.choiceName)
            is SettingsEvent.SaveSystemPrompt -> saveSystemPrompt(event.prompt)
            SettingsEvent.ResetSystemPrompt -> resetSystemPrompt()
            // Both need an Activity Result launcher, which only exists in composition. The host
            // intercepts them and calls back in with the picked document via export/importBackup.
            SettingsEvent.ExportData, SettingsEvent.ImportData -> Unit
        }
    }

    // --- Appearance settings ---------------------------------------------------------------

    /**
     * Persists one palette entry and re-reads the snapshot, so the surrounding theme follows the
     * edit. One function over a named slot, rather than eleven `(Color) -> Unit` overloads that only
     * their argument position told apart.
     */
    fun saveThemeColor(slot: ThemeColorSlot, color: Color) {
        viewModelScope.launch {
            when (slot) {
                ThemeColorSlot.BACKGROUND -> themeSettingsRepository.saveBackground(color)
                ThemeColorSlot.SURFACE -> themeSettingsRepository.saveSurface(color)
                ThemeColorSlot.SURFACE_VARIANT -> themeSettingsRepository.saveSurfaceVariant(color)
                ThemeColorSlot.MEAL_PRIMARY -> themeSettingsRepository.savePrimary(color)
                ThemeColorSlot.MEAL_SECONDARY -> themeSettingsRepository.saveSecondary(color)
                ThemeColorSlot.MEAL_ACCENT -> themeSettingsRepository.saveAccent(color)
                ThemeColorSlot.MEAL_OUTLINE -> themeSettingsRepository.saveOutline(color)
                ThemeColorSlot.ACTIVITY_PRIMARY -> themeSettingsRepository.saveActivityPrimary(color)
                ThemeColorSlot.ACTIVITY_SECONDARY -> themeSettingsRepository.saveActivitySecondary(color)
                ThemeColorSlot.ACTIVITY_ACCENT -> themeSettingsRepository.saveActivityAccent(color)
                ThemeColorSlot.ACTIVITY_OUTLINE -> themeSettingsRepository.saveActivityOutline(color)
            }
            loadThemeSettings()
        }
    }

    fun saveBaseCaloriesBurned(input: String) {
        update { it.copy(settingsMessage = null) }
        val caloriesBurned = input.trim().toIntOrNull()
        if (caloriesBurned == null || caloriesBurned <= 0) {
            update { it.copy(settingsMessage = "Enter a whole number greater than 0 for base calories burned per day.") }
            return
        }
        viewModelScope.launch {
            runCatching { themeSettingsRepository.saveBaseCaloriesBurned(caloriesBurned) }
                .onSuccess { update { it.copy(settingsMessage = "Saved base calories burned per day.") } }
                .onFailure { throwable ->
                    update {
                        it.copy(
                            settingsMessage = throwable.message ?: "Could not save base calories burned per day.",
                        )
                    }
                }
            loadThemeSettings()
        }
    }

    /** Seconds between tip rotations: `0` turns rotation off, otherwise 2-60. */
    fun saveTipRotationSeconds(input: String) {
        update { it.copy(settingsMessage = null) }
        val seconds = input.trim().toIntOrNull()
        if (seconds == null || (seconds != 0 && seconds !in NutritionSettingsStore.TIP_ROTATION_SECONDS_RANGE)) {
            update { it.copy(settingsMessage = INVALID_TIP_ROTATION_MESSAGE) }
            return
        }
        viewModelScope.launch {
            runCatching { themeSettingsRepository.saveTipRotationSeconds(seconds) }
                .onSuccess { update { it.copy(settingsMessage = "Saved tip rotation interval.") } }
                .onFailure { throwable ->
                    update { it.copy(settingsMessage = throwable.message ?: "Could not save the tip rotation interval.") }
                }
            loadThemeSettings()
        }
    }

    fun saveKcalPerStep(input: String) {
        update { it.copy(settingsMessage = null) }
        val per1000 = input.trim().replace(',', '.').toDoubleOrNull()
        if (per1000 == null || per1000 <= 0.0) {
            update {
                it.copy(settingsMessage = "Enter a number greater than 0 for calories burned per 1,000 steps.")
            }
            return
        }
        viewModelScope.launch {
            runCatching { themeSettingsRepository.saveKcalPerStep(per1000 / 1000.0) }
                .onSuccess { update { it.copy(settingsMessage = "Saved calories burned per 1,000 steps.") } }
                .onFailure { throwable ->
                    update {
                        it.copy(
                            settingsMessage = throwable.message ?: "Could not save calories burned per 1,000 steps.",
                        )
                    }
                }
            loadThemeSettings()
        }
    }

    fun saveNutritionGoals(calorieGoalInput: String, proteinInput: String, carbsInput: String) {
        update { it.copy(settingsMessage = null) }
        when (val parsed = NutritionGoals.parse(calorieGoalInput, proteinInput, carbsInput)) {
            is NutritionGoalsParseResult.Invalid -> update { it.copy(settingsMessage = parsed.message) }
            is NutritionGoalsParseResult.Parsed -> viewModelScope.launch {
                runCatching { themeSettingsRepository.saveNutritionGoals(parsed.goals) }
                    .onSuccess { update { it.copy(settingsMessage = "Saved nutrition goals.") } }
                    .onFailure { throwable ->
                        update { it.copy(settingsMessage = throwable.message ?: "Could not save nutrition goals.") }
                    }
                loadThemeSettings()
            }
        }
    }

    // --- Keys and tool settings ------------------------------------------------------------

    fun onRuntimeApiKeyChange(apiKey: String) = update { it.copy(runtimeApiKey = apiKey) }

    fun saveOpenAiKey(newKey: String) {
        update { it.copy(settingsMessage = null) }
        viewModelScope.launch {
            runCatching { secretRepository.saveOpenAiApiKey(newKey) }
                .onSuccess {
                    loadSavedKeyLabel()
                    update { it.copy(settingsMessage = "Saved API key locally.") }
                }
                .onFailure { throwable ->
                    update { it.copy(settingsMessage = throwable.message ?: "Could not save API key.") }
                }
        }
    }

    fun clearOpenAiKey() {
        viewModelScope.launch {
            secretRepository.clearOpenAiApiKey()
            loadSavedKeyLabel()
            update { it.copy(settingsMessage = "Removed saved API key.") }
        }
    }

    fun saveBraveKey(newKey: String) {
        update { it.copy(settingsMessage = null) }
        viewModelScope.launch {
            runCatching { secretRepository.saveBraveApiKey(newKey) }
                .onSuccess {
                    loadToolSettings()
                    update { it.copy(settingsMessage = "Saved Brave API key locally.") }
                }
                .onFailure { throwable ->
                    update { it.copy(settingsMessage = throwable.message ?: "Could not save Brave API key.") }
                }
        }
    }

    fun clearBraveKey() {
        viewModelScope.launch {
            secretRepository.clearBraveApiKey()
            loadToolSettings()
            update { it.copy(settingsMessage = "Removed saved Brave API key.") }
        }
    }

    fun saveMaxResearchToolCalls(input: String) {
        update { it.copy(settingsMessage = null) }
        val maxCalls = input.trim().toIntOrNull()
        if (maxCalls == null || maxCalls !in ToolSettings.maxResearchToolCallsRange) {
            update {
                it.copy(
                    settingsMessage = "Enter max research tool calls from " +
                        "${ToolSettings.MIN_MAX_RESEARCH_TOOL_CALLS} to ${ToolSettings.MAX_MAX_RESEARCH_TOOL_CALLS}.",
                )
            }
            return
        }
        viewModelScope.launch {
            runCatching { secretRepository.saveMaxResearchToolCalls(maxCalls) }
                .onSuccess {
                    loadToolSettings()
                    update { it.copy(settingsMessage = "Saved max research tool calls.") }
                }
                .onFailure { throwable ->
                    update { it.copy(settingsMessage = throwable.message ?: "Could not save max research tool calls.") }
                }
        }
    }

    fun saveMaxAgentIterations(input: String) {
        update { it.copy(settingsMessage = null) }
        val iterations = input.trim().toIntOrNull()
        if (iterations == null || iterations !in ToolSettings.maxAgentIterationsRange) {
            update {
                it.copy(
                    settingsMessage = "Enter max agent iterations from " +
                        "${ToolSettings.MIN_MAX_AGENT_ITERATIONS} to ${ToolSettings.MAX_MAX_AGENT_ITERATIONS}.",
                )
            }
            return
        }
        viewModelScope.launch {
            runCatching { secretRepository.saveMaxAgentIterations(iterations) }
                .onSuccess {
                    loadToolSettings()
                    update { it.copy(settingsMessage = "Saved max agent iterations.") }
                }
                .onFailure { throwable ->
                    update { it.copy(settingsMessage = throwable.message ?: "Could not save max agent iterations.") }
                }
        }
    }

    fun saveOpenAiModelChoice(choiceName: String) {
        update { it.copy(settingsMessage = null) }
        viewModelScope.launch {
            runCatching { secretRepository.saveOpenAiModelChoice(choiceName) }
                .onSuccess {
                    loadToolSettings()
                    update { it.copy(settingsMessage = "Saved AI model.") }
                }
                .onFailure { throwable ->
                    update { it.copy(settingsMessage = throwable.message ?: "Could not save AI model.") }
                }
        }
    }

    fun saveSystemPrompt(newPrompt: String) {
        update { it.copy(settingsMessage = null) }
        val missing = KoogNutritionAgent.missingRequiredSystemPromptPhrases(newPrompt)
        viewModelScope.launch {
            runCatching { secretRepository.saveSystemPromptOverride(newPrompt) }
                .onSuccess {
                    loadToolSettings()
                    update {
                        it.copy(
                            settingsMessage = if (missing.isEmpty()) {
                                "Saved system prompt."
                            } else {
                                "Saved system prompt, but it's missing: ${missing.joinToString("; ")}"
                            },
                        )
                    }
                }
                .onFailure { throwable ->
                    update { it.copy(settingsMessage = throwable.message ?: "Could not save system prompt.") }
                }
        }
    }

    fun resetSystemPrompt() {
        viewModelScope.launch {
            secretRepository.clearSystemPromptOverride()
            loadToolSettings()
            update { it.copy(settingsMessage = "Restored default system prompt.") }
        }
    }

    // --- Backup ----------------------------------------------------------------------------

    fun exportBackup(documentUri: String) {
        update { it.copy(settingsMessage = null) }
        viewModelScope.launch {
            try {
                backupRepository.exportTo(documentUri, clock().toEpochMilli())
                update { it.copy(settingsMessage = "Exported your data backup.") }
            } catch (throwable: Throwable) {
                update { it.copy(settingsMessage = throwable.message ?: "Could not export data.") }
            }
        }
    }

    fun importBackup(documentUri: String) {
        update { it.copy(settingsMessage = null) }
        viewModelScope.launch {
            try {
                val result = backupRepository.importFrom(documentUri)
                update {
                    it.copy(settingsMessage = "Imported ${result.imported}, skipped ${result.skipped} already present.")
                }
            } catch (throwable: Throwable) {
                update { it.copy(settingsMessage = throwable.message ?: "Could not import data.") }
            }
        }
    }

    /** A short, technical note naming the step in flight when an estimate failed, for [MealCaptureUiState.diagnostic]. */
    private fun EstimationProgress.toDiagnosticNote(): String = when (this) {
        EstimationProgress.Preparing -> "Failed while preparing the estimate."
        EstimationProgress.SearchingSources -> "Failed while searching for sources."
        is EstimationProgress.ReadingSource -> "Failed while reading $url."
        EstimationProgress.CalculatingNutrition -> "Failed while calculating nutrition."
    }

    private fun update(block: (MealCaptureUiState) -> MealCaptureUiState) {
        _uiState.update { current -> withDayScoreTips(block(current)) }
    }

    /**
     * Re-derives [MealCaptureUiState.dayScoreTips] from whatever the update produced.
     *
     * Applied centrally rather than at each call site so no write can leave a tip list that
     * disagrees with the meals, activities, goals or day it was derived from. Tips exist only for
     * today with at least one logged meal; every other day gets an empty list.
     */
    private fun withDayScoreTips(next: MealCaptureUiState): MealCaptureUiState {
        val tips = if (next.selectedDayOffset != 0) {
            emptyList()
        } else {
            val meals = filterMealsForDay(next.savedMeals, 0, next.now, zone)
            val activities = filterActivitiesForDay(next.savedActivities, 0, next.now, zone)
            dayScoreTips(
                totals = meals.toDailyNutritionTotals(),
                goals = next.nutritionGoals,
                activityBurnedKcal = activities.sumOf { it.caloriesBurnedKcal },
                hasLoggedActivity = activities.isNotEmpty(),
                localTime = LocalTime.ofInstant(next.now, zone),
            )
        }
        // Keep the reworded text when nothing about the ranking moved, so a refresh survives a
        // clock tick or an unrelated state change.
        val kept = next.dayScoreTips
        val unchanged = kept.size == tips.size && kept.zip(tips).all { (a, b) -> a.kind == b.kind }
        return if (unchanged) next else next.copy(dayScoreTips = tips)
    }

    companion object {
        private const val INVALID_TIP_ROTATION_MESSAGE =
            "Enter 0 for no rotation, or a whole number of seconds between 2 and 60."
        private const val ESTIMATE_FAILED_MESSAGE = "Koog nutrition estimate failed."
        private const val BLANK_MEAL_DESCRIPTION_MESSAGE = "Meal description cannot be blank before saving."

        /**
         * Wiring only. The settings slice is read here, on the caller's thread, so the first frame is
         * painted with the user's saved colours and targets rather than the defaults.
         */
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                MealCaptureViewModel(
                    mealRepository = container.mealRepository,
                    activityRepository = container.activityRepository,
                    mealCacheRepository = container.mealCacheRepository,
                    themeSettingsRepository = container.themeSettingsRepository,
                    secretRepository = container.secretRepository,
                    backupRepository = container.backupRepository,
                    nutritionEstimator = container.nutritionEstimator,
                    tipRewordingAgent = container.tipRewordingAgent,
                    initialSettings = container.themeSettingsRepository.currentSnapshot(),
                    initialRuntimeApiKey = container.defaultOpenAiApiKey,
                )
            }
        }
    }
}
