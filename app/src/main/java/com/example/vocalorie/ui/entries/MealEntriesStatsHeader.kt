package com.example.vocalorie.ui.entries

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import com.example.vocalorie.ui.components.HeaderDropdownAction
import com.example.vocalorie.ui.components.formatEnergy
import com.example.vocalorie.ui.components.formatNullable
import com.example.vocalorie.ui.entries.stats.DayScoreTip
import com.example.vocalorie.ui.macroColors
import kotlinx.coroutines.delay
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
    dayScoreTips: List<DayScoreTip> = emptyList(),
    tipRotationSeconds: Int = 0,
    canRewordTips: Boolean = false,
    rewordingInFlight: Boolean = false,
    onRewordTips: () -> Unit = {},
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
            DayScoreTipsSection(
                tips = dayScoreTips,
                rotationSeconds = tipRotationSeconds,
                canReword = canRewordTips,
                rewordingInFlight = rewordingInFlight,
                onReword = onRewordTips,
            )
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

/**
 * The actionable tips under the day score, rotating one at a time.
 *
 * The rotating index and the expanded flag are view state (design D7): only the ranked list crosses
 * the state-holder boundary, so a five-second ticker never lands in a unit test. Renders nothing
 * when there is nothing to say.
 */
@Composable
internal fun DayScoreTipsSection(
    tips: List<DayScoreTip>,
    rotationSeconds: Int,
    canReword: Boolean,
    rewordingInFlight: Boolean,
    onReword: () -> Unit,
) {
    if (tips.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    var index by remember(tips) { mutableIntStateOf(0) }
    val rotating = tips.size

    LaunchedEffect(tips, rotationSeconds, expanded, rotating) {
        if (expanded || rotationSeconds <= 0 || rotating < 2) return@LaunchedEffect
        while (true) {
            delay(rotationSeconds * 1_000L)
            index = (index + 1) % rotating
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (expanded) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                tips.forEach { tip -> TipText(tip.text) }
            }
        } else {
            Crossfade(targetState = tips[index.coerceIn(0, tips.lastIndex)], modifier = Modifier.weight(1f), label = "dayScoreTip") { tip ->
                TipText(tip.text)
            }
        }
        if (canReword) {
            if (rewordingInFlight) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onReword, modifier = Modifier.size(24.dp)) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Reword the tips",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TipText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.primary,
    )
}
