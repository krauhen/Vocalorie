package com.example.vocalorie.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.vocalorie.BuildConfig
import com.example.vocalorie.ai.KoogNutritionAgent
import com.example.vocalorie.ai.NutritionAgentException
import com.example.vocalorie.data.CachedItemEntity
import com.example.vocalorie.data.CachedMealEntity
import com.example.vocalorie.data.CachedMealMatch
import com.example.vocalorie.data.toEditableDraft
import com.example.vocalorie.data.findCachedMealMatch
import com.example.vocalorie.data.toCachedItemEntities
import com.example.vocalorie.data.toCachedMealEntity
import com.example.vocalorie.data.withItemsResolvedFromCache
import com.example.vocalorie.data.VocalorieDatabase
import com.example.vocalorie.data.exportBackupJson
import com.example.vocalorie.data.importBackupJson
import com.example.vocalorie.data.searchSavedMeals
import com.example.vocalorie.data.toEntity
import com.example.vocalorie.data.toSavedActivity
import com.example.vocalorie.data.toSavedMeal
import com.example.vocalorie.model.ActivityType
import com.example.vocalorie.model.EditableActivityDraft
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.NutritionGoals
import com.example.vocalorie.model.SavedActivity
import com.example.vocalorie.model.stepsBurnKcal
import com.example.vocalorie.model.SavedMeal
import com.example.vocalorie.settings.ThemeColors
import com.example.vocalorie.settings.OpenAiApiKeyStore
import com.example.vocalorie.settings.ThemeSettingsStore
import com.example.vocalorie.settings.ToolSettings
import com.example.vocalorie.settings.ToolSettingsStore
import com.example.vocalorie.ui.entries.ActivityEntryOverlay
import com.example.vocalorie.ui.entries.EntriesTab
import com.example.vocalorie.ui.entries.MealEntriesScreen
import com.example.vocalorie.ui.entries.MealEntryOverlay
import com.example.vocalorie.ui.entries.selectedDayTimestampMillis
import com.example.vocalorie.ui.settings.SettingsScreen
import com.example.vocalorie.ui.voice.GalleryImageAttachment
import com.example.vocalorie.ui.voice.VoiceInputOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

@Composable
fun MealCaptureScreen(
    activeThemeColors: ThemeColors,
    onActiveThemeColorsChange: (ThemeColors) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val database = remember { VocalorieDatabase.get(context) }
    val mealDao = remember { database.mealDao() }
    val activityDao = remember { database.activityDao() }
    val cacheDao = remember { database.cacheDao() }
    val apiKeyStore = remember { OpenAiApiKeyStore(context) }
    val toolSettingsStore = remember { ToolSettingsStore(context) }
    val themeSettingsStore = remember { ThemeSettingsStore(context) }
    val scope = rememberCoroutineScope()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var runtimeApiKey by remember { mutableStateOf(BuildConfig.DEFAULT_OPENAI_API_KEY) }
    var query by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var diagnostic by remember { mutableStateOf<String?>(null) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var attachedImages by remember { mutableStateOf<List<GalleryImageAttachment>>(emptyList()) }
    var resetSignal by remember { mutableStateOf(0) }
    var settingsMessage by remember { mutableStateOf<String?>(null) }
    var savedKeyLabel by remember { mutableStateOf<String?>(apiKeyStore.displayLabel()) }
    var toolSettings by remember { mutableStateOf(toolSettingsStore.get()) }
    var braveKeyLabel by remember { mutableStateOf<String?>(toolSettingsStore.savedBraveKeyLabel()) }
    var baseCaloriesBurned by remember { mutableIntStateOf(themeSettingsStore.getBaseCaloriesBurned()) }
    var kcalPerStep by remember { mutableDoubleStateOf(themeSettingsStore.getKcalPerStep()) }
    var nutritionGoals by remember { mutableStateOf(themeSettingsStore.getNutritionGoals()) }
    var selectedTab by rememberSaveable { mutableStateOf(EntriesTab.MEALS) }
    var selectedDayOffset by rememberSaveable { mutableIntStateOf(0) }
    var draft by remember { mutableStateOf<EditableMealDraft?>(null) }
    var savedMeals by remember { mutableStateOf<List<SavedMeal>>(emptyList()) }
    var cachedMeals by remember { mutableStateOf<List<CachedMealEntity>>(emptyList()) }
    var cachedItems by remember { mutableStateOf<List<CachedItemEntity>>(emptyList()) }
    var savedActivities by remember { mutableStateOf<List<SavedActivity>>(emptyList()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedMeal by remember { mutableStateOf<SavedMeal?>(null) }
    var selectedDraft by remember { mutableStateOf<EditableMealDraft?>(null) }
    var selectedActivity by remember { mutableStateOf<SavedActivity?>(null) }
    var selectedActivityDraft by remember { mutableStateOf<EditableActivityDraft?>(null) }
    var showActivityOverlay by remember { mutableStateOf(false) }
    var activityMessage by remember { mutableStateOf<String?>(null) }
    var activityError by remember { mutableStateOf<String?>(null) }
    var approvalMatch by remember { mutableStateOf<CachedMealMatch?>(null) }
    var pendingEstimateRequest by remember { mutableStateOf<EstimateRequest?>(null) }

    suspend fun refreshHistory() {
        savedMeals = withContext(Dispatchers.IO) { mealDao.getAll().map { it.toSavedMeal() } }
    }

    suspend fun refreshCaches() {
        val loaded = withContext(Dispatchers.IO) { cacheDao.allMeals to cacheDao.allItems }
        cachedMeals = loaded.first
        cachedItems = loaded.second
    }

    // Reviewed-save is the only writer of both caches; last-saved-wins per normalized key.
    suspend fun upsertCachesFromReviewedMeal(mealDraft: EditableMealDraft) {
        withContext(Dispatchers.IO) {
            mealDraft.toCachedMealEntity()?.let { cacheDao.upsertMeal(it) }
            val itemEntities = mealDraft.toCachedItemEntities()
            if (itemEntities.isNotEmpty()) cacheDao.upsertItems(itemEntities)
        }
        refreshCaches()
    }

    suspend fun refreshActivities() {
        savedActivities = withContext(Dispatchers.IO) { activityDao.getAll().map { it.toSavedActivity() } }
    }

    fun refreshSavedKeyLabel() { savedKeyLabel = apiKeyStore.displayLabel() }

    fun refreshToolSettings(): ToolSettings {
        val refreshed = toolSettingsStore.get()
        toolSettings = refreshed
        braveKeyLabel = toolSettingsStore.savedBraveKeyLabel()
        return refreshed
    }

    fun refreshThemeState() {
        baseCaloriesBurned = themeSettingsStore.getBaseCaloriesBurned()
        kcalPerStep = themeSettingsStore.getKcalPerStep()
        nutritionGoals = themeSettingsStore.getNutritionGoals()
        onActiveThemeColorsChange(
            when (selectedTab) {
                EntriesTab.MEALS -> themeSettingsStore.get()
                EntriesTab.ACTIVITIES -> themeSettingsStore.getActivityColors()
            },
        )
    }

    fun selectTab(tab: EntriesTab) {
        selectedTab = tab
        refreshThemeState()
    }

    // Timestamp for a new entry: the currently-viewed day at the current wall-clock time
    // (offset 0 = today ≈ now). Editing an existing entry keeps its own stored timestamp.
    fun newEntryTimestampMillis(): Long =
        selectedDayTimestampMillis(selectedDayOffset, Instant.now(), ZoneId.systemDefault())

    fun openActivityEditor(activity: SavedActivity?) {
        selectedActivity = activity
        selectedActivityDraft = activity?.toEditableDraft() ?: EditableActivityDraft(
            type = null,
            title = "",
            description = "",
            caloriesBurnedKcal = "",
            durationMinutes = "",
            steps = "",
            createdAtEpochMillis = newEntryTimestampMillis(),
        )
        showActivityOverlay = true
        activityError = null
        activityMessage = null
    }

    fun dismissActivityEditor() {
        showActivityOverlay = false
        selectedActivity = null
        selectedActivityDraft = null
        activityError = null
        activityMessage = null
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                settingsMessage = null
                try {
                    val json = withContext(Dispatchers.IO) { exportBackupJson(database, System.currentTimeMillis()) }
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                            ?: error("Could not open the chosen file for writing.")
                    }
                    settingsMessage = "Exported your data backup."
                } catch (throwable: Throwable) {
                    settingsMessage = throwable.message ?: "Could not export data."
                }
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                settingsMessage = null
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                            ?: error("Could not open the chosen file for reading.")
                    }
                    val result = withContext(Dispatchers.IO) { importBackupJson(database, json) }
                    refreshHistory()
                    refreshActivities()
                    refreshCaches()
                    settingsMessage = "Imported ${result.imported}, skipped ${result.skipped} already present."
                } catch (throwable: Throwable) {
                    settingsMessage = throwable.message ?: "Could not import data."
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshHistory()
        refreshCaches()
        refreshActivities()
        refreshThemeState()
    }

    fun resetNewMealEstimate() {
        query = ""
        searchQuery = ""
        draft = null
        error = null
        diagnostic = null
        saveMessage = null
        attachedImages = emptyList()
        resetSignal += 1
    }

    fun runEstimate(request: EstimateRequest) {
        scope.launch {
            isLoading = true
            try {
                val keyForEstimate = apiKeyStore.get() ?: runtimeApiKey
                if (keyForEstimate.isBlank()) {
                    error = "Enter an OpenAI API key or save one in Settings."
                    return@launch
                }
                refreshSavedKeyLabel()
                val settingsForEstimate = refreshToolSettings()
                draft = KoogNutritionAgent.estimate(
                    openAiApiKey = keyForEstimate,
                    query = request.requestQuery,
                    toolSettings = settingsForEstimate,
                    imageAttachments = request.imageAttachments,
                ).toEditableDraft()
                    .copy(query = request.finalDraftQuery.ifBlank { request.requestQuery })
                    .withItemsResolvedFromCache(cachedItems)
            } catch (throwable: NutritionAgentException) {
                error = throwable.message ?: "Koog nutrition estimate failed."
                diagnostic = throwable.diagnostic
            } catch (throwable: Throwable) {
                error = throwable.message ?: "Koog nutrition estimate failed."
            } finally {
                isLoading = false
                pendingEstimateRequest = null
            }
        }
    }

    BackHandler(enabled = showSettings || approvalMatch != null || selectedMeal != null || showActivityOverlay) {
        when {
            showSettings -> showSettings = false
            approvalMatch != null -> {
                approvalMatch = null
                pendingEstimateRequest = null
            }
            showActivityOverlay -> {
                showActivityOverlay = false
                selectedActivity = null
                selectedActivityDraft = null
            }
            selectedMeal != null -> {
                selectedMeal = null
                selectedDraft = null
            }
        }
    }

    if (showSettings) {
        SettingsScreen(
            themeColors = themeSettingsStore.get(),
            onSavePrimaryColor = { themeSettingsStore.savePrimary(it); refreshThemeState() },
            onSaveSecondaryColor = { themeSettingsStore.saveSecondary(it); refreshThemeState() },
            onSaveAccentColor = { themeSettingsStore.saveAccent(it); refreshThemeState() },
            onSaveBackgroundColor = { themeSettingsStore.saveBackground(it); refreshThemeState() },
            onSaveSurfaceColor = { themeSettingsStore.saveSurface(it); refreshThemeState() },
            onSaveSurfaceVariantColor = { themeSettingsStore.saveSurfaceVariant(it); refreshThemeState() },
            onSaveOutlineColor = { themeSettingsStore.saveOutline(it); refreshThemeState() },
            activityColors = themeSettingsStore.getActivityColors(),
            onSaveActivityPrimaryColor = { themeSettingsStore.saveActivityPrimary(it); refreshThemeState() },
            onSaveActivitySecondaryColor = { themeSettingsStore.saveActivitySecondary(it); refreshThemeState() },
            onSaveActivityAccentColor = { themeSettingsStore.saveActivityAccent(it); refreshThemeState() },
            onSaveActivityOutlineColor = { themeSettingsStore.saveActivityOutline(it); refreshThemeState() },
            baseCaloriesBurned = themeSettingsStore.getBaseCaloriesBurned(),
            onSaveBaseCaloriesBurned = { input ->
                settingsMessage = null
                val caloriesBurned = input.trim().toIntOrNull()
                if (caloriesBurned == null || caloriesBurned <= 0) {
                    settingsMessage = "Enter a whole number greater than 0 for base calories burned per day."
                } else {
                    runCatching { themeSettingsStore.saveBaseCaloriesBurned(caloriesBurned) }
                        .onSuccess { settingsMessage = "Saved base calories burned per day." }
                        .onFailure { settingsMessage = it.message ?: "Could not save base calories burned per day." }
                    refreshThemeState()
                }
            },
            kcalPerStep = themeSettingsStore.getKcalPerStep(),
            onSaveKcalPerStep = { input ->
                settingsMessage = null
                val per1000 = input.trim().replace(',', '.').toDoubleOrNull()
                if (per1000 == null || per1000 <= 0.0) {
                    settingsMessage = "Enter a number greater than 0 for calories burned per 1,000 steps."
                } else {
                    runCatching { themeSettingsStore.saveKcalPerStep(per1000 / 1000.0) }
                        .onSuccess { settingsMessage = "Saved calories burned per 1,000 steps." }
                        .onFailure { settingsMessage = it.message ?: "Could not save calories burned per 1,000 steps." }
                    refreshThemeState()
                }
            },
            nutritionGoals = nutritionGoals,
            onSaveNutritionGoals = { calorieGoalInput, proteinInput, carbsInput ->
                settingsMessage = null
                val goal = calorieGoalInput.trim().toIntOrNull()
                val protein = proteinInput.trim().toIntOrNull()
                val carbs = carbsInput.trim().toIntOrNull()
                val fat = if (protein != null && carbs != null) 100 - protein - carbs else null
                if (goal == null || goal <= 0) {
                    settingsMessage = "Enter a whole number greater than 0 for the daily calorie goal."
                } else if (protein == null || carbs == null || fat == null || protein < 0 || carbs < 0 || fat < 0) {
                    settingsMessage = "Protein and carbs percentages must be whole numbers that leave a non-negative fat share."
                } else {
                    runCatching {
                        themeSettingsStore.saveNutritionGoals(
                            NutritionGoals(calorieGoalKcal = goal, proteinPercent = protein, carbsPercent = carbs, fatPercent = fat),
                        )
                    }
                        .onSuccess { settingsMessage = "Saved nutrition goals." }
                        .onFailure { settingsMessage = it.message ?: "Could not save nutrition goals." }
                    refreshThemeState()
                }
            },
            savedKeyLabel = savedKeyLabel,
            runtimeApiKey = runtimeApiKey,
            onRuntimeApiKeyChange = { runtimeApiKey = it },
            braveKeyLabel = braveKeyLabel,
            toolSettings = toolSettings,
            message = settingsMessage,
            enabled = !isLoading && !isSaving,
            onSaveKey = { newKey ->
                settingsMessage = null
                runCatching { apiKeyStore.save(newKey) }
                    .onSuccess { refreshSavedKeyLabel(); settingsMessage = "Saved API key locally." }
                    .onFailure { settingsMessage = it.message ?: "Could not save API key." }
            },
            onClearKey = {
                apiKeyStore.clear()
                refreshSavedKeyLabel()
                settingsMessage = "Removed saved API key."
            },
            onSaveBraveKey = { newKey ->
                settingsMessage = null
                runCatching { toolSettingsStore.saveBraveApiKey(newKey) }
                    .onSuccess { refreshToolSettings(); settingsMessage = "Saved Brave API key locally." }
                    .onFailure { settingsMessage = it.message ?: "Could not save Brave API key." }
            },
            onClearBraveKey = {
                toolSettingsStore.clearBraveApiKey()
                refreshToolSettings()
                settingsMessage = "Removed saved Brave API key."
            },
            onSaveMaxResearchToolCalls = { input ->
                settingsMessage = null
                val maxCalls = input.trim().toIntOrNull()
                if (maxCalls == null || maxCalls !in ToolSettings.maxResearchToolCallsRange) {
                    settingsMessage =
                        "Enter max research tool calls from ${ToolSettings.MIN_MAX_RESEARCH_TOOL_CALLS} to ${ToolSettings.MAX_MAX_RESEARCH_TOOL_CALLS}."
                } else {
                    runCatching { toolSettingsStore.saveMaxResearchToolCalls(maxCalls) }
                        .onSuccess { refreshToolSettings(); settingsMessage = "Saved max research tool calls." }
                        .onFailure { settingsMessage = it.message ?: "Could not save max research tool calls." }
                }
            },
            onSaveMaxAgentIterations = { input ->
                settingsMessage = null
                val iterations = input.trim().toIntOrNull()
                if (iterations == null || iterations !in ToolSettings.maxAgentIterationsRange) {
                    settingsMessage =
                        "Enter max agent iterations from ${ToolSettings.MIN_MAX_AGENT_ITERATIONS} to ${ToolSettings.MAX_MAX_AGENT_ITERATIONS}."
                } else {
                    runCatching { toolSettingsStore.saveMaxAgentIterations(iterations) }
                        .onSuccess { refreshToolSettings(); settingsMessage = "Saved max agent iterations." }
                        .onFailure { settingsMessage = it.message ?: "Could not save max agent iterations." }
                }
            },
            onSaveOpenAiModelChoice = { choiceName ->
                settingsMessage = null
                runCatching { toolSettingsStore.saveOpenAiModelChoice(choiceName) }
                    .onSuccess { refreshToolSettings(); settingsMessage = "Saved AI model." }
                    .onFailure { settingsMessage = it.message ?: "Could not save AI model." }
            },
            onSaveSystemPrompt = { newPrompt ->
                settingsMessage = null
                val missing = KoogNutritionAgent.missingRequiredSystemPromptPhrases(newPrompt)
                runCatching { toolSettingsStore.saveSystemPromptOverride(newPrompt) }
                    .onSuccess {
                        refreshToolSettings()
                        settingsMessage = if (missing.isEmpty()) {
                            "Saved system prompt."
                        } else {
                            "Saved system prompt, but it's missing: ${missing.joinToString("; ")}"
                        }
                    }
                    .onFailure { settingsMessage = it.message ?: "Could not save system prompt." }
            },
            onResetSystemPrompt = {
                toolSettingsStore.clearSystemPromptOverride()
                refreshToolSettings()
                settingsMessage = "Restored default system prompt."
            },
            onExportData = { exportBackupLauncher.launch("vocalorie-backup.json") },
            onImportData = { importBackupLauncher.launch(arrayOf("application/json")) },
            onBack = { showSettings = false },
        )
    } else {
        MealEntriesScreen(
            meals = savedMeals,
            activities = savedActivities,
            selectedTab = selectedTab,
            onSelectTab = ::selectTab,
            onOpenMeal = {
                selectedMeal = it
                selectedDraft = it.toEditableDraft()
                error = null
                saveMessage = null
            },
            onOpenActivity = { openActivityEditor(it) },
            onAddActivity = { openActivityEditor(null) },
            onOpenSettings = { showSettings = true },
            onRefresh = {
                refreshHistory()
                refreshActivities()
                refreshCaches()
            },
            selectedDayOffset = selectedDayOffset,
            onSelectedDayOffsetChange = { selectedDayOffset = it },
            baseCaloriesBurned = baseCaloriesBurned,
            goals = nutritionGoals,
            modifier = modifier,
            voiceButton = {
                val searchResults = searchSavedMeals(savedMeals, searchQuery)
                VoiceInputOverlay(
                    query = query,
                    onQueryChange = { query = it },
                    draft = draft,
                    onDraftChange = { draft = it },
                    isLoading = isLoading,
                    isSaving = isSaving,
                    error = error,
                    diagnostic = diagnostic,
                    saveMessage = saveMessage,
                    attachedImages = attachedImages,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    searchResults = searchResults,
                    onSearchMealClick = { meal ->
                        error = null
                        diagnostic = null
                        saveMessage = null
                        searchQuery = ""
                        approvalMatch = CachedMealMatch(meal = meal, draft = meal.toEditableDraft())
                        pendingEstimateRequest = null
                    },
                    resetSignal = resetSignal,
                    onEstimate = {
                        error = null
                        diagnostic = null
                        saveMessage = null
                        draft = null
                        val imageAttachments = attachedImages
                        val promptQuery = query.ifBlank {
                            if (imageAttachments.isNotEmpty()) "Estimate the meal from the attached photo${if (imageAttachments.size > 1) "s" else ""}." else ""
                        }
                        val finalDraftQuery = query.ifBlank {
                            when (imageAttachments.size) {
                                0 -> ""
                                1 -> "Photo: ${imageAttachments.first().label}"
                                else -> "Photos (${imageAttachments.size})"
                            }
                        }
                        val cachedMatch = findCachedMealMatch(cachedMeals, promptQuery)
                        when {
                            cachedMatch != null -> {
                                approvalMatch = cachedMatch
                                pendingEstimateRequest = EstimateRequest(promptQuery, finalDraftQuery, imageAttachments)
                            }
                            promptQuery.isBlank() -> error = "Enter a nutrition query or attach a photo."
                            else -> runEstimate(EstimateRequest(promptQuery, finalDraftQuery, imageAttachments))
                        }
                    },
                    onReset = { resetNewMealEstimate() },
                    onImagesChange = { images ->
                        draft = null
                        error = null
                        diagnostic = null
                        saveMessage = null
                        attachedImages = images
                    },
                    onSave = { mealDraft ->
                        error = null
                        diagnostic = null
                        saveMessage = null
                        if (mealDraft.query.isBlank()) {
                            error = "Meal description cannot be blank before saving."
                        } else {
                            scope.launch {
                                isSaving = true
                                try {
                                    val savedId = withContext(Dispatchers.IO) { mealDao.insert(mealDraft.toEntity(createdAtEpochMillis = newEntryTimestampMillis())) }
                                    refreshHistory()
                                    upsertCachesFromReviewedMeal(mealDraft)
                                    selectedMeal = savedMeals.firstOrNull { it.id == savedId }
                                    selectedDraft = selectedMeal?.toEditableDraft()
                                    draft = null
                                    query = ""
                                    attachedImages = emptyList()
                                    saveMessage = "Saved meal to local entries."
                                } catch (throwable: Throwable) {
                                    error = throwable.message ?: "Could not save meal locally."
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    },
                )
            },
        )
    }

    approvalMatch?.let { match ->
        MealEntryOverlay(
            meal = match.meal,
            draft = match.draft,
            enabled = !isLoading && !isSaving,
            message = null,
            error = null,
            onDraftChange = {},
            onSave = { _, _ -> },
            onDelete = {},
            onDismiss = {
                approvalMatch = null
                pendingEstimateRequest = null
            },
            approvalMessage = "Use this cached meal draft?",
            approvalActionLabel = if (pendingEstimateRequest != null) "Estimate instead" else "Close",
            onApprove = {
                query = match.draft.query.ifBlank { match.meal.query }
                draft = match.draft
                error = null
                diagnostic = null
                saveMessage = null
                approvalMatch = null
                pendingEstimateRequest = null
            },
            onReject = pendingEstimateRequest?.let {
                {
                    val request = pendingEstimateRequest
                    approvalMatch = null
                    pendingEstimateRequest = null
                    if (request != null) {
                        draft = null
                        runEstimate(request)
                    }
                }
            },
        )
    }

    if (approvalMatch == null) selectedMeal?.let { meal ->
        MealEntryOverlay(
            meal = meal,
            draft = selectedDraft ?: meal.toEditableDraft(),
            enabled = !isSaving,
            message = saveMessage,
            error = error,
            onDraftChange = { selectedDraft = it },
            onSave = { mealDraft, onSaved ->
                error = null
                saveMessage = null
                if (mealDraft.query.isBlank()) {
                    error = "Meal description cannot be blank before saving."
                } else {
                    scope.launch {
                        isSaving = true
                        try {
                            val updated = mealDraft.toEntity(
                                id = meal.id,
                                createdAtEpochMillis = mealDraft.createdAtEpochMillis ?: meal.createdAtEpochMillis,
                            )
                            withContext(Dispatchers.IO) { mealDao.update(updated) }
                            refreshHistory()
                            upsertCachesFromReviewedMeal(mealDraft)
                            selectedMeal = updated.toSavedMeal()
                            selectedDraft = selectedMeal?.toEditableDraft()
                            saveMessage = "Updated saved meal."
                            onSaved()
                        } catch (throwable: Throwable) {
                            error = throwable.message ?: "Could not update meal locally."
                        } finally {
                            isSaving = false
                        }
                    }
                }
            },
            onDelete = {
                scope.launch {
                    isSaving = true
                    try {
                        withContext(Dispatchers.IO) { mealDao.deleteById(meal.id) }
                        selectedMeal = null
                        selectedDraft = null
                        refreshHistory()
                        saveMessage = "Deleted saved meal."
                    } catch (throwable: Throwable) {
                        error = throwable.message ?: "Could not delete meal locally."
                    } finally {
                        isSaving = false
                    }
                }
            },
            onDismiss = { selectedMeal = null; selectedDraft = null },
        )
    }

    if (showActivityOverlay) selectedActivityDraft?.let { draft ->
        ActivityEntryOverlay(
            activity = selectedActivity,
            draft = draft,
            enabled = !isSaving,
            kcalPerStep = kcalPerStep,
            message = activityMessage,
            error = activityError,
            onDraftChange = { selectedActivityDraft = it },
            onSave = { activityDraft, onSaved ->
                activityError = null
                activityMessage = null
                val type = activityDraft.type
                val isSteps = type == ActivityType.STEPS
                val steps = activityDraft.steps.trim().toIntOrNull()
                val calories = if (isSteps) {
                    steps?.let { stepsBurnKcal(it, kcalPerStep) }
                } else {
                    activityDraft.caloriesBurnedKcal.trim().replace(',', '.').toDoubleOrNull()
                }
                val duration = if (isSteps) 0 else activityDraft.durationMinutes.trim().toIntOrNull()
                when {
                    type == null -> activityError = "Choose an activity type before saving."
                    isSteps && (steps == null || steps < 0) -> activityError = "Enter your step count as a whole number."
                    calories == null -> activityError = "Enter calories burned as a number."
                    duration == null -> activityError = "Enter duration in whole minutes."
                    else -> {
                        scope.launch {
                            isSaving = true
                            try {
                                val entity = activityDraft.toEntity(
                                    id = selectedActivity?.id ?: 0L,
                                    createdAtEpochMillis = activityDraft.createdAtEpochMillis ?: System.currentTimeMillis(),
                                ).copy(
                                    caloriesBurnedKcal = calories,
                                    durationMinutes = duration,
                                    stepsCount = if (isSteps) steps else null,
                                )
                                if (selectedActivity == null) {
                                    withContext(Dispatchers.IO) { activityDao.insert(entity) }
                                    refreshActivities()
                                    activityMessage = "Saved activity to local entries."
                                    dismissActivityEditor()
                                } else {
                                    withContext(Dispatchers.IO) { activityDao.update(entity) }
                                    refreshActivities()
                                    selectedActivity = entity.toSavedActivity()
                                    selectedActivityDraft = selectedActivity?.toEditableDraft()
                                    activityMessage = "Updated activity."
                                    onSaved()
                                }
                            } catch (throwable: Throwable) {
                                activityError = throwable.message ?: "Could not save activity locally."
                            } finally {
                                isSaving = false
                            }
                        }
                    }
                }
            },
            onDelete = {
                scope.launch {
                    isSaving = true
                    try {
                        val current = selectedActivity
                        if (current != null) {
                            withContext(Dispatchers.IO) { activityDao.deleteById(current.id) }
                            refreshActivities()
                        }
                        dismissActivityEditor()
                        activityMessage = "Deleted activity."
                    } catch (throwable: Throwable) {
                        activityError = throwable.message ?: "Could not delete activity locally."
                    } finally {
                        isSaving = false
                    }
                }
            },
            onDismiss = { dismissActivityEditor() },
        )
    }
}

private data class EstimateRequest(
    val requestQuery: String,
    val finalDraftQuery: String,
    val imageAttachments: List<GalleryImageAttachment>,
)
