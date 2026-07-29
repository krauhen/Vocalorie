package com.example.vocalorie.ui.entries

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.vocalorie.ui.components.HeaderDropdownAction
import com.example.vocalorie.ui.components.formatEnergy
import com.example.vocalorie.ui.components.formatNullable
import com.example.vocalorie.ui.macroColors
import kotlin.math.roundToInt

@Composable
internal fun SelectableStatsHeader(
    stats: LabeledMealStats,
    dayScore: Double?,
    burnedCaloriesKcal: Double,
    balanceCaloriesKcal: Double,
    caloriesHistogram: List<MealCaloriesBucket>,
    changeMenuExpanded: Boolean,
    onChangeMenuExpandedChange: (Boolean) -> Unit,
    onSelectStatsWindow: (MealStatsWindowMode) -> Unit,
    onOpenDetail: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenDetail),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stats.label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HeaderDropdownAction(
                    expanded = changeMenuExpanded,
                    onExpandedChange = onChangeMenuExpandedChange,
                ) {
                    // `entries` order is the display order: Since 00:00, Last 24h, Custom.
                    MealStatsWindowMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(mode.label) },
                            onClick = { onSelectStatsWindow(mode) },
                        )
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stats.stats.caloriesKcal.formatEnergy(),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                if (dayScore != null) {
                    Text(
                        "Score ${dayScore.roundToInt()}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            EnergySummaryRow(label = "Burned", value = burnedCaloriesKcal.formatEnergy())
            EnergySummaryRow(
                label = "Balance",
                value = if (balanceCaloriesKcal >= 0.0) "+${balanceCaloriesKcal.formatNullable()} kcal surplus" else "${balanceCaloriesKcal.formatNullable()} kcal deficit",
                valueColor = if (balanceCaloriesKcal >= 0.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
            )
            val headerMacros = macroColors()
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = headerMacros.protein, fontWeight = FontWeight.SemiBold)) {
                        append("Protein ${stats.stats.proteinG.formatNullable()}g")
                    }
                    append(" · ")
                    withStyle(SpanStyle(color = headerMacros.carbs, fontWeight = FontWeight.SemiBold)) {
                        append("Carbs ${stats.stats.carbsG.formatNullable()}g")
                    }
                    append(" · ")
                    withStyle(SpanStyle(color = headerMacros.fat, fontWeight = FontWeight.SemiBold)) {
                        append("Fat ${stats.stats.fatG.formatNullable()}g")
                    }
                    append(" · Amount ${stats.stats.amountGml.formatNullable()}g/ml")
                },
                style = MaterialTheme.typography.bodySmall,
            )
            StatsHistogramSeparator()
            CaloriesHistogram(caloriesHistogram)
        }
    }
}

@Composable
private fun EnergySummaryRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall, color = valueColor)
    }
}
