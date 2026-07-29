package com.example.vocalorie.ui.settings

import androidx.compose.ui.graphics.Color
import com.example.vocalorie.model.NutritionGoals
import com.example.vocalorie.settings.ThemeColors
import com.example.vocalorie.settings.ToolSettings

/**
 * One of the eleven editable palette entries.
 *
 * Naming a slot lets the screen say "save this colour" once instead of carrying eleven
 * indistinguishable `(Color) -> Unit` parameters that only their argument order told apart.
 * The three shared surface slots live in the meal palette, which is where they are persisted.
 */
enum class ThemeColorSlot {
    BACKGROUND,
    SURFACE,
    SURFACE_VARIANT,
    MEAL_PRIMARY,
    MEAL_SECONDARY,
    MEAL_ACCENT,
    MEAL_OUTLINE,
    ACTIVITY_PRIMARY,
    ACTIVITY_SECONDARY,
    ACTIVITY_ACCENT,
    ACTIVITY_OUTLINE,
}

/**
 * Everything [SettingsScreen] draws.
 *
 * This replaces the eleven value parameters that used to arrive alongside twenty-eight callbacks,
 * threaded 1:1 through a forty-line pass-through to an equally wide private composable. The screen
 * was already stateless and correct in shape; it was only unreadable in width.
 */
data class SettingsUiState(
    val mealColors: ThemeColors,
    val activityColors: ThemeColors,
    val baseCaloriesBurned: Int,
    val kcalPerStep: Double,
    val nutritionGoals: NutritionGoals,
    val savedKeyLabel: String?,
    val runtimeApiKey: String,
    val braveKeyLabel: String?,
    val toolSettings: ToolSettings,
    val message: String?,
    /** False while an estimate or a save is in flight, which makes every control read-only. */
    val enabled: Boolean,
) {

    /** The persisted colour for [slot]. */
    fun color(slot: ThemeColorSlot): Color = when (slot) {
        ThemeColorSlot.BACKGROUND -> mealColors.background
        ThemeColorSlot.SURFACE -> mealColors.surface
        ThemeColorSlot.SURFACE_VARIANT -> mealColors.surfaceVariant
        ThemeColorSlot.MEAL_PRIMARY -> mealColors.primary
        ThemeColorSlot.MEAL_SECONDARY -> mealColors.secondary
        ThemeColorSlot.MEAL_ACCENT -> mealColors.accent
        ThemeColorSlot.MEAL_OUTLINE -> mealColors.outline
        ThemeColorSlot.ACTIVITY_PRIMARY -> activityColors.primary
        ThemeColorSlot.ACTIVITY_SECONDARY -> activityColors.secondary
        ThemeColorSlot.ACTIVITY_ACCENT -> activityColors.accent
        ThemeColorSlot.ACTIVITY_OUTLINE -> activityColors.outline
    }
}

/**
 * Everything [SettingsScreen] can ask for.
 *
 * Each case is a request, not an implementation: the screen never decides whether an input is
 * valid, what a limit's bounds are, or what message to show — the state holder does, so those rules
 * stay testable without a composition. Back navigation stays a separate parameter because it is the
 * host's concern rather than a settings operation.
 */
sealed interface SettingsEvent {

    data class SaveColor(val slot: ThemeColorSlot, val color: Color) : SettingsEvent

    data class SaveBaseCaloriesBurned(val input: String) : SettingsEvent

    /** Edited as kcal per 1,000 steps; the state holder converts to the stored per-step value. */
    data class SaveKcalPer1000Steps(val input: String) : SettingsEvent

    data class SaveNutritionGoals(
        val calorieGoal: String,
        val proteinPercent: String,
        val carbsPercent: String,
    ) : SettingsEvent

    data class RuntimeApiKeyChanged(val apiKey: String) : SettingsEvent

    data class SaveOpenAiKey(val key: String) : SettingsEvent

    data object ClearOpenAiKey : SettingsEvent

    data class SaveBraveKey(val key: String) : SettingsEvent

    data object ClearBraveKey : SettingsEvent

    data class SaveMaxResearchToolCalls(val input: String) : SettingsEvent

    data class SaveMaxAgentIterations(val input: String) : SettingsEvent

    data class SaveOpenAiModelChoice(val choiceName: String) : SettingsEvent

    data class SaveSystemPrompt(val prompt: String) : SettingsEvent

    data object ResetSystemPrompt : SettingsEvent

    data object ExportData : SettingsEvent

    data object ImportData : SettingsEvent
}
