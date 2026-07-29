package com.example.vocalorie.ui.entries

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.vocalorie.ui.components.NutritionLabelRows
import java.time.Duration
import kotlin.math.roundToInt

@Composable
internal fun DayNutritionDetailDialog(label: String, stats: MealNutritionStats, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(label) },
        text = {
            NutritionLabelRows(
                amountGml = stats.amountGml,
                caloriesKcal = stats.caloriesKcal,
                fatG = stats.fatG,
                saturatedFatG = stats.saturatedFatG,
                carbsG = stats.carbsG,
                sugarG = stats.sugarG,
                proteinG = stats.proteinG,
                saltG = stats.saltG,
            )
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
internal fun StatsWindowSelectorDialog(
    selectedMode: MealStatsWindowMode,
    selectedCustomMinutes: Int,
    onDismiss: () -> Unit,
    onSelected: (MealStatsWindowMode, Int) -> Unit,
) {
    var pendingModeName by rememberSaveable(selectedMode) { mutableStateOf(selectedMode.name) }
    var pendingMinutes by rememberSaveable(selectedCustomMinutes) { mutableIntStateOf(selectedCustomMinutes) }
    val pendingMode = remember(pendingModeName) {
        runCatching { MealStatsWindowMode.valueOf(pendingModeName) }.getOrDefault(MealStatsWindowMode.SINCE_MIDNIGHT)
    }
    val sliderSteps = ((MAX_ROLLING_STATS_MINUTES - MIN_ROLLING_STATS_MINUTES) / ROLLING_STATS_STEP_MINUTES) - 1

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stats window") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MealStatsWindowMode.entries.forEach { mode ->
                    StatsModeOption(mode, pendingMode) { pendingModeName = it.name }
                }
                if (pendingMode == MealStatsWindowMode.CUSTOM) {
                    Text(formatRollingStatsLabel(Duration.ofMinutes(pendingMinutes.toLong())), style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = pendingMinutes.toFloat(),
                        onValueChange = { value ->
                            pendingMinutes = normalizeRollingStatsDuration(Duration.ofMinutes(value.roundToInt().toLong())).toMinutes().toInt()
                        },
                        valueRange = MIN_ROLLING_STATS_MINUTES.toFloat()..MAX_ROLLING_STATS_MINUTES.toFloat(),
                        steps = sliderSteps,
                    )
                    Text("15-minute increments · 15 mins to 24 hours", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSelected(pendingMode, pendingMinutes) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun StatsModeOption(
    mode: MealStatsWindowMode,
    selectedMode: MealStatsWindowMode,
    onSelected: (MealStatsWindowMode) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onSelected(mode) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RadioButton(selected = selectedMode == mode, onClick = { onSelected(mode) })
        Text(mode.label, style = MaterialTheme.typography.bodyLarge)
    }
}
