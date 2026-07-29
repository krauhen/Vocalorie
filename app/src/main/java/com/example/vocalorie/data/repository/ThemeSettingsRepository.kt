package com.example.vocalorie.data.repository

import androidx.compose.ui.graphics.Color
import com.example.vocalorie.model.NutritionGoals
import com.example.vocalorie.settings.NutritionSettingsStore
import com.example.vocalorie.settings.ThemeColors
import com.example.vocalorie.settings.ThemeSettingsStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Everything the appearance and nutrition-target settings contribute to screen state, read at once. */
data class ThemeSettingsSnapshot(
    val mealColors: ThemeColors,
    val activityColors: ThemeColors,
    val baseCaloriesBurned: Int,
    val kcalPerStep: Double,
    val nutritionGoals: NutritionGoals,
)

/**
 * Appearance settings over [ThemeSettingsStore] and nutrition targets over [NutritionSettingsStore].
 *
 * Takes the stores, not a `Context`: nothing above this boundary needs one. Every read and write is
 * dispatched, because `SharedPreferences` touches disk on first access — with the single documented
 * exception of [currentSnapshot].
 */
class ThemeSettingsRepository(
    private val store: ThemeSettingsStore,
    private val nutritionSettings: NutritionSettingsStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** One read for the whole settings-derived slice of screen state. */
    suspend fun snapshot(): ThemeSettingsSnapshot = withContext(dispatcher) { currentSnapshot() }

    /**
     * The same read, on the caller's thread.
     *
     * The one deliberate exception to "a repository owns its own dispatching": the very first frame
     * is painted with the user's saved colours and targets, and a dispatcher hop would paint the
     * defaults first and swap them a frame later — a visible flash. Called once, to seed state.
     */
    fun currentSnapshot(): ThemeSettingsSnapshot = ThemeSettingsSnapshot(
        mealColors = store.get(),
        activityColors = store.getActivityColors(),
        baseCaloriesBurned = nutritionSettings.getBaseCaloriesBurned(),
        kcalPerStep = nutritionSettings.getKcalPerStep(),
        nutritionGoals = nutritionSettings.getNutritionGoals(),
    )

    suspend fun savePrimary(color: Color) = withContext(dispatcher) { store.savePrimary(color) }

    suspend fun saveSecondary(color: Color) = withContext(dispatcher) { store.saveSecondary(color) }

    suspend fun saveAccent(color: Color) = withContext(dispatcher) { store.saveAccent(color) }

    suspend fun saveBackground(color: Color) = withContext(dispatcher) { store.saveBackground(color) }

    suspend fun saveSurface(color: Color) = withContext(dispatcher) { store.saveSurface(color) }

    suspend fun saveSurfaceVariant(color: Color) = withContext(dispatcher) { store.saveSurfaceVariant(color) }

    suspend fun saveOutline(color: Color) = withContext(dispatcher) { store.saveOutline(color) }

    suspend fun saveActivityPrimary(color: Color) = withContext(dispatcher) { store.saveActivityPrimary(color) }

    suspend fun saveActivitySecondary(color: Color) = withContext(dispatcher) { store.saveActivitySecondary(color) }

    suspend fun saveActivityAccent(color: Color) = withContext(dispatcher) { store.saveActivityAccent(color) }

    suspend fun saveActivityOutline(color: Color) = withContext(dispatcher) { store.saveActivityOutline(color) }

    suspend fun saveBaseCaloriesBurned(value: Int) =
        withContext(dispatcher) { nutritionSettings.saveBaseCaloriesBurned(value) }

    suspend fun saveKcalPerStep(value: Double) = withContext(dispatcher) { nutritionSettings.saveKcalPerStep(value) }

    suspend fun saveNutritionGoals(goals: NutritionGoals) =
        withContext(dispatcher) { nutritionSettings.saveNutritionGoals(goals) }
}
