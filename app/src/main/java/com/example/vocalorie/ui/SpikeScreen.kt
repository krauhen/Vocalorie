package com.example.vocalorie.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.vocalorie.BuildConfig
import com.example.vocalorie.ai.KoogNutritionSpike
import com.example.vocalorie.ai.NutritionSpikeException
import com.example.vocalorie.data.CachedMealMatch
import com.example.vocalorie.data.findCachedMealMatch
import com.example.vocalorie.data.VocalorieDatabase
import com.example.vocalorie.data.searchSavedMeals
import com.example.vocalorie.data.toEditableDraft
import com.example.vocalorie.data.toEntity
import com.example.vocalorie.data.toSavedMeal
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.SavedMeal
import com.example.vocalorie.settings.OpenAiApiKeyStore
import com.example.vocalorie.settings.ToolSettings
import com.example.vocalorie.settings.ToolSettingsStore
import com.example.vocalorie.ui.entries.MealEntriesScreen
import com.example.vocalorie.ui.entries.MealEntryOverlay
import com.example.vocalorie.ui.settings.SettingsScreen
import com.example.vocalorie.ui.voice.GalleryImageAttachment
import com.example.vocalorie.ui.voice.VoiceInputOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SpikeScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { VocalorieDatabase.get(context).mealDao() }
    val apiKeyStore = remember { OpenAiApiKeyStore(context) }
    val toolSettingsStore = remember { ToolSettingsStore(context) }
    val scope = rememberCoroutineScope()

    var showSettings by rememberSaveable { mutableStateOf(false) }
    var runtimeApiKey by remember { mutableStateOf(BuildConfig.DEFAULT_OPENAI_API_KEY) }
    var query by rememberSaveable { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var diagnostic by remember { mutableStateOf<String?>(null) }
    var saveMessage by remember { mutableStateOf<String?>(null) }
    var attachedImage by remember { mutableStateOf<GalleryImageAttachment?>(null) }
    var resetSignal by remember { mutableStateOf(0) }
    var settingsMessage by remember { mutableStateOf<String?>(null) }
    var savedKeyLabel by remember { mutableStateOf<String?>(apiKeyStore.displayLabel()) }
    var toolSettings by remember { mutableStateOf(toolSettingsStore.get()) }
    var braveKeyLabel by remember { mutableStateOf<String?>(toolSettingsStore.savedBraveKeyLabel()) }
    var draft by remember { mutableStateOf<EditableMealDraft?>(null) }
    var savedMeals by remember { mutableStateOf<List<SavedMeal>>(emptyList()) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedMeal by remember { mutableStateOf<SavedMeal?>(null) }
    var selectedDraft by remember { mutableStateOf<EditableMealDraft?>(null) }
    var approvalMatch by remember { mutableStateOf<CachedMealMatch?>(null) }
    var pendingEstimateRequest by remember { mutableStateOf<EstimateRequest?>(null) }

    suspend fun refreshHistory() {
        savedMeals = withContext(Dispatchers.IO) { dao.getAll().map { it.toSavedMeal() } }
    }

    fun refreshSavedKeyLabel() { savedKeyLabel = apiKeyStore.displayLabel() }

    fun refreshToolSettings(): ToolSettings {
        val refreshed = toolSettingsStore.get()
        toolSettings = refreshed
        braveKeyLabel = toolSettingsStore.savedBraveKeyLabel()
        return refreshed
    }

    LaunchedEffect(Unit) { refreshHistory() }

    fun resetNewMealEstimate() {
        query = ""
        searchQuery = ""
        draft = null
        error = null
        diagnostic = null
        saveMessage = null
        attachedImage = null
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
                draft = KoogNutritionSpike.estimate(
                    openAiApiKey = keyForEstimate,
                    query = request.requestQuery,
                    toolSettings = settingsForEstimate,
                    imageAttachment = request.imageAttachment,
                ).toEditableDraft().copy(query = request.finalDraftQuery.ifBlank { request.requestQuery })
            } catch (throwable: NutritionSpikeException) {
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

    BackHandler(enabled = showSettings || approvalMatch != null || selectedMeal != null) {
        when {
            showSettings -> showSettings = false
            approvalMatch != null -> {
                approvalMatch = null
                pendingEstimateRequest = null
            }
            selectedMeal != null -> {
                selectedMeal = null
                selectedDraft = null
            }
        }
    }

    if (showSettings) {
        SettingsScreen(
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
            onBack = { showSettings = false },
        )
    } else {
        MealEntriesScreen(
            meals = savedMeals,
            onOpenMeal = {
                selectedMeal = it
                selectedDraft = it.toEditableDraft()
                error = null
                saveMessage = null
            },
            onOpenSettings = { showSettings = true },
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
                    attachedImageLabel = attachedImage?.label,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    searchResults = searchResults,
                    onSearchMealClick = { meal ->
                        error = null
                        diagnostic = null
                        saveMessage = null
                        approvalMatch = CachedMealMatch(meal = meal, draft = meal.toEditableDraft())
                        pendingEstimateRequest = null
                    },
                    resetSignal = resetSignal,
                    onEstimate = {
                        error = null
                        diagnostic = null
                        saveMessage = null
                        draft = null
                        val imageAttachment = attachedImage
                        val promptQuery = query.ifBlank {
                            if (imageAttachment != null) "Estimate the meal from the attached photo." else ""
                        }
                        val finalDraftQuery = query.ifBlank { imageAttachment?.label?.let { "Photo: $it" }.orEmpty() }
                        val cachedMatch = findCachedMealMatch(savedMeals, promptQuery)
                        when {
                            cachedMatch != null -> {
                                approvalMatch = cachedMatch
                                pendingEstimateRequest = EstimateRequest(promptQuery, finalDraftQuery, imageAttachment)
                            }
                            promptQuery.isBlank() -> error = "Enter a nutrition query or attach a photo."
                            else -> runEstimate(EstimateRequest(promptQuery, finalDraftQuery, imageAttachment))
                        }
                    },
                    onReset = { resetNewMealEstimate() },
                    onPickImage = { attachment: GalleryImageAttachment? ->
                        draft = null
                        error = null
                        diagnostic = null
                        saveMessage = null
                        attachedImage = attachment
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
                                    val savedId = withContext(Dispatchers.IO) { dao.insert(mealDraft.toEntity()) }
                                    refreshHistory()
                                    selectedMeal = savedMeals.firstOrNull { it.id == savedId }
                                    selectedDraft = selectedMeal?.toEditableDraft()
                                    draft = null
                                    query = ""
                                    attachedImage = null
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
                            withContext(Dispatchers.IO) { dao.update(updated) }
                            refreshHistory()
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
                        withContext(Dispatchers.IO) { dao.deleteById(meal.id) }
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
}

private data class EstimateRequest(
    val requestQuery: String,
    val finalDraftQuery: String,
    val imageAttachment: GalleryImageAttachment?,
)
