package com.example.vocalorie.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.vocalorie.AppContainer
import com.example.vocalorie.data.searchSavedMeals
import com.example.vocalorie.data.toEditableDraft
import com.example.vocalorie.settings.ThemeColors
import com.example.vocalorie.ui.capture.CachedMealApprovalDialog
import com.example.vocalorie.ui.capture.MealCaptureViewModel
import com.example.vocalorie.ui.entries.ActivityEntryOverlay
import com.example.vocalorie.ui.entries.MealEntriesScreen
import com.example.vocalorie.ui.entries.MealEntryOverlay
import com.example.vocalorie.ui.entries.durationUntilNextLocalMidnight
import com.example.vocalorie.ui.settings.SettingsEvent
import com.example.vocalorie.ui.settings.SettingsScreen
import com.example.vocalorie.ui.voice.VoiceInputOverlay
import kotlinx.coroutines.delay
import java.time.Duration

/**
 * The capture flow's UI: it renders [com.example.vocalorie.ui.capture.MealCaptureUiState] and emits
 * events to [MealCaptureViewModel].
 *
 * Deliberately holds no database, preference store or estimation client, performs no persistence and
 * decides nothing — it used to do all three across seven hundred lines. Every argument lambda below
 * is a single call into the state holder; when one grows a condition, that condition belongs there.
 */
@Composable
fun MealCaptureScreen(
    /** Reports the palette the surrounding theme should switch to. */
    onActiveThemeColorsChange: (ThemeColors) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = remember(context) { AppContainer.get(context) }
    val viewModel: MealCaptureViewModel = viewModel(
        factory = remember(container) { MealCaptureViewModel.factory(container) },
    )
    val state by viewModel.uiState.collectAsState()

    // The surrounding theme follows the selected tab's palette and every appearance edit.
    LaunchedEffect(state.activeThemeColors) { onActiveThemeColorsChange(state.activeThemeColors) }

    // Coming back to the app after any length of backgrounding should never show a stale "Today".
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshNow()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Re-arms itself every time `now` moves: sleeps until just past the next local midnight, then
    // refreshes the clock, which naturally re-keys this effect for the following day.
    LaunchedEffect(state.now) {
        val margin = Duration.ofSeconds(2)
        delay(durationUntilNextLocalMidnight(state.now, viewModel.zone).plus(margin).toMillis())
        viewModel.refreshNow()
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { viewModel.exportBackup(it.toString()) } }

    val importBackupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { viewModel.importBackup(it.toString()) } }

    BackHandler(enabled = state.canHandleBack) { viewModel.onBack() }

    if (state.showSettings) {
        SettingsScreen(
            state = state.settings,
            onEvent = { event ->
                // The two document-picker events are the one thing the state holder cannot do: they
                // need an Activity Result launcher, which exists only in composition. It hears back
                // from the launcher above with the picked document.
                when (event) {
                    SettingsEvent.ExportData -> exportBackupLauncher.launch("vocalorie-backup.json")
                    SettingsEvent.ImportData -> importBackupLauncher.launch(arrayOf("application/json"))
                    else -> viewModel.onSettingsEvent(event)
                }
            },
            onBack = viewModel::closeSettings,
        )
    } else {
        MealEntriesScreen(
            meals = state.savedMeals,
            activities = state.savedActivities,
            selectedTab = state.selectedTab,
            onSelectTab = viewModel::selectTab,
            onOpenMeal = viewModel::openMeal,
            onOpenActivity = { viewModel.openActivityEditor(it) },
            onAddActivity = { viewModel.openActivityEditor(null) },
            onOpenSettings = viewModel::openSettings,
            onRefresh = { viewModel.refreshNow() },
            now = state.now,
            zone = viewModel.zone,
            selectedDayOffset = state.selectedDayOffset,
            onSelectedDayOffsetChange = viewModel::selectDayOffset,
            baseCaloriesBurned = state.baseCaloriesBurned,
            goals = state.nutritionGoals,
            dayScoreTips = state.dayScoreTips,
            tipRotationSeconds = state.tipRotationSeconds,
            canRewordTips = state.canRewordDayScoreTips,
            tipsRewordingInFlight = state.tipsRewordingInFlight,
            onRewordTips = viewModel::rewordDayScoreTips,
            modifier = modifier,
            voiceButton = {
                val searchResults = remember(state.savedMeals, state.searchQuery) {
                    searchSavedMeals(state.savedMeals, state.searchQuery)
                }
                VoiceInputOverlay(
                    query = state.query,
                    onQueryChange = viewModel::onQueryChange,
                    draft = state.draft,
                    onDraftChange = viewModel::onDraftChange,
                    isLoading = state.isLoading,
                    isSaving = state.isSaving,
                    error = state.error,
                    diagnostic = state.diagnostic,
                    groundingWarning = state.groundingWarning,
                    estimationProgress = state.estimationProgress,
                    saveMessage = state.saveMessage,
                    attachedImages = state.attachedImages,
                    searchQuery = state.searchQuery,
                    onSearchQueryChange = viewModel::onSearchQueryChange,
                    searchResults = searchResults,
                    onSearchMealClick = viewModel::onSearchMealClick,
                    resetSignal = state.resetSignal,
                    onEstimate = viewModel::onEstimate,
                    onReset = viewModel::resetNewMealEstimate,
                    onImagesChange = viewModel::onImagesChange,
                    onSave = viewModel::saveNewMeal,
                )
            },
        )
    }

    state.approvalMatch?.let { match ->
        CachedMealApprovalDialog(
            meal = match.meal,
            enabled = !state.isBusy,
            message = "Use this cached meal draft?",
            rejectLabel = state.approvalRejectLabel,
            onApprove = viewModel::approveCachedMeal,
            onReject = viewModel::rejectCachedMeal,
            onDismiss = viewModel::dismissApproval,
        )
    }

    if (state.approvalMatch == null) state.selectedMeal?.let { meal ->
        MealEntryOverlay(
            meal = meal,
            draft = state.selectedDraft ?: meal.toEditableDraft(),
            enabled = !state.isSaving,
            message = state.saveMessage,
            error = state.error,
            onDraftChange = viewModel::onSelectedDraftChange,
            onSave = viewModel::saveSelectedMeal,
            onDelete = viewModel::deleteSelectedMeal,
            onDismiss = viewModel::dismissSelectedMeal,
        )
    }

    if (state.showActivityOverlay) state.selectedActivityDraft?.let { draft ->
        ActivityEntryOverlay(
            activity = state.selectedActivity,
            draft = draft,
            enabled = !state.isSaving,
            kcalPerStep = state.kcalPerStep,
            message = state.activityMessage,
            error = state.activityError,
            onDraftChange = viewModel::onActivityDraftChange,
            onSave = viewModel::saveActivity,
            onDelete = viewModel::deleteActivity,
            onDismiss = viewModel::dismissActivityEditor,
        )
    }
}
