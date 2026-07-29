package com.example.vocalorie.data.repository

import androidx.compose.ui.graphics.Color
import com.example.vocalorie.model.NutritionGoals
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
 * Appearance and nutrition-target settings over [ThemeSettingsStore].
 *
 * Takes the store, not a `Context`: nothing above this boundary needs one. Every read and write is
 * dispatched, because `SharedPreferences` touches disk on first access.
 */
class ThemeSettingsRepository(
    private val store: ThemeSettingsStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** One read for the whole settings-derived slice of screen state. */
    suspend fun snapshot(): ThemeSettingsSnapshot = withContext(dispatcher) {
        ThemeSettingsSnapshot(
            mealColors = store.get(),
            activityColors = store.getActivityColors(),
            baseCaloriesBurned = store.getBaseCaloriesBurned(),
            kcalPerStep = store.getKcalPerStep(),
            nutritionGoals = store.getNutritionGoals(),
        )
    }

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

    suspend fun saveBaseCaloriesBurned(value: Int) = withContext(dispatcher) { store.saveBaseCaloriesBurned(value) }

    suspend fun saveKcalPerStep(value: Double) = withContext(dispatcher) { store.saveKcalPerStep(value) }

    suspend fun saveNutritionGoals(goals: NutritionGoals) = withContext(dispatcher) { store.saveNutritionGoals(goals) }
}
