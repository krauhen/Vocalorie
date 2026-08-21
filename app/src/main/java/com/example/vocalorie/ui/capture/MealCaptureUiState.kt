package com.example.vocalorie.ui.capture

import com.example.vocalorie.ai.EstimationProgress
import com.example.vocalorie.data.CachedMealMatch
import com.example.vocalorie.model.EditableActivityDraft
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.NutritionGoals
import com.example.vocalorie.model.SavedActivity
import com.example.vocalorie.model.SavedMeal
import com.example.vocalorie.settings.ThemeColors
import com.example.vocalorie.settings.ToolSettings
import com.example.vocalorie.ui.entries.EntriesTab
import com.example.vocalorie.ui.entries.stats.DayScoreTip
import com.example.vocalorie.ui.settings.SettingsUiState
import com.example.vocalorie.ui.voice.GalleryImageAttachment
import java.time.Instant

/**
 * Everything the capture flow draws, as one immutable value.
 *
 * This replaces thirty-five `mutableStateOf` slots that lived inside the screen composable, of which
 * only six survived a rotation. Held by [MealCaptureViewModel], it now survives a configuration
 * change in full — including a running estimate's eventual result, the reviewed draft and its
 * attached photos.
 *
 * Almost nothing here is derived state that a composable could compute itself; each field is either
 * user input, observed persisted data, or the outcome of an operation. The one exception is
 * [dayScoreTips], and it is documented where it sits.
 */
data class MealCaptureUiState(
    // --- Settings-derived, seeded synchronously so the first frame is not painted with defaults ---
    val mealThemeColors: ThemeColors,
    val activityThemeColors: ThemeColors,
    val baseCaloriesBurned: Int,
    val kcalPerStep: Double,
    val nutritionGoals: NutritionGoals,
    /** Seconds between day-score tip rotations; `0` means no rotation. */
    val tipRotationSeconds: Int,

    /**
     * The wall clock the flow reads. Advanced by [MealCaptureViewModel.refreshNow] and by every
     * write that stamps an entry, so no code path calls `Instant.now()` inside the state holder.
     */
    val now: Instant,

    // --- Routing ---
    val showSettings: Boolean = false,
    val selectedTab: EntriesTab = EntriesTab.MEALS,
    val selectedDayOffset: Int = 0,

    // --- New-meal capture ---
    val query: String = "",
    val searchQuery: String = "",
    val draft: EditableMealDraft? = null,
    val attachedImages: List<GalleryImageAttachment> = emptyList(),
    /** Bumped to tell the voice overlay to clear its own transient state. */
    val resetSignal: Int = 0,
    val isLoading: Boolean = false,
    /** The current step of an in-flight estimate, purely descriptive; [isLoading] is the gate. */
    val estimationProgress: EstimationProgress? = null,
    val isSaving: Boolean = false,
    val error: String? = null,
    val diagnostic: String? = null,
    val groundingWarning: String? = null,
    val saveMessage: String? = null,

    // --- Observed persisted data ---
    val savedMeals: List<SavedMeal> = emptyList(),
    val savedActivities: List<SavedActivity> = emptyList(),

    // --- Day-score tips ---
    /**
     * The one derived aggregate here, deliberately (design D1): the LLM rewording path needs a
     * repository and a scope, so the tip list cannot be owned by the composable that shows it.
     */
    val dayScoreTips: List<DayScoreTip> = emptyList(),
    val tipsRewordingInFlight: Boolean = false,

    // --- Cached-meal approval ---
    val approvalMatch: CachedMealMatch? = null,
    val pendingEstimateRequest: EstimateRequest? = null,

    // --- Saved-meal detail overlay ---
    val selectedMeal: SavedMeal? = null,
    val selectedDraft: EditableMealDraft? = null,

    // --- Activity editor overlay ---
    val showActivityOverlay: Boolean = false,
    val selectedActivity: SavedActivity? = null,
    val selectedActivityDraft: EditableActivityDraft? = null,
    val activityMessage: String? = null,
    val activityError: String? = null,

    // --- Settings screen ---
    val settingsMessage: String? = null,
    val runtimeApiKey: String = "",
    val savedKeyLabel: String? = null,
    val braveKeyLabel: String? = null,
    val toolSettings: ToolSettings = ToolSettings(),
) {

    /** The palette the surrounding theme should use, which follows the selected tab. */
    val activeThemeColors: ThemeColors
        get() = when (selectedTab) {
            EntriesTab.MEALS -> mealThemeColors
            EntriesTab.ACTIVITIES -> activityThemeColors
        }

    /**
     * Whether the tips strip may offer its rewording refresh.
     *
     * A stored (or entered) OpenAI key is the whole gate: the app is BYOK, so without one the
     * affordance would only ever produce a failure the user did not ask for.
     */
    val canRewordDayScoreTips: Boolean
        get() = dayScoreTips.isNotEmpty() && (savedKeyLabel != null || runtimeApiKey.isNotBlank())

    /** Editors and overlays are read-only while an estimate or a save is in flight. */
    val isBusy: Boolean get() = isLoading || isSaving

    /**
     * Label for the approval dialog's left button: rejecting a cache hit found by tapping Estimate
     * runs the estimate that was held back, while a cache draft opened from search has nothing to run.
     */
    val approvalRejectLabel: String get() = if (pendingEstimateRequest != null) "Estimate instead" else "Close"

    /** Whether a system back press has an overlay or screen to close. */
    val canHandleBack: Boolean
        get() = showSettings || approvalMatch != null || selectedMeal != null || showActivityOverlay

    /**
     * The slice the settings screen draws, so that screen takes one value rather than the eleven
     * value parameters it used to receive alongside twenty-eight callbacks.
     */
    val settings: SettingsUiState
        get() = SettingsUiState(
            mealColors = mealThemeColors,
            activityColors = activityThemeColors,
            baseCaloriesBurned = baseCaloriesBurned,
            kcalPerStep = kcalPerStep,
            nutritionGoals = nutritionGoals,
            tipRotationSeconds = tipRotationSeconds,
            savedKeyLabel = savedKeyLabel,
            runtimeApiKey = runtimeApiKey,
            braveKeyLabel = braveKeyLabel,
            toolSettings = toolSettings,
            message = settingsMessage,
            enabled = !isBusy,
        )
}
