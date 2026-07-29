package com.example.vocalorie.ui.entries

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.ceil
import kotlin.math.roundToInt

@Composable
internal fun StatsHistogramSeparator() {
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.36f)

    Canvas(modifier = Modifier.fillMaxWidth().height(10.dp)) {
        drawLine(
            color = lineColor,
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx())),
        )
    }
}

@Composable
internal fun CaloriesHistogram(buckets: List<MealCaloriesBucket>) {
    val maxCalories = buckets.maxOfOrNull { it.caloriesKcal } ?: 0.0
    val axisMaxCalories = remember(maxCalories) { niceCaloriesAxisMax(maxCalories) }
    val midTickCalories = axisMaxCalories / 2.0
    val timeLabels = remember(buckets) { formatHistogramTimeLabels(buckets) }
    val barColor = MaterialTheme.colorScheme.primary
    val emptyBarColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Calories over time",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
            Text(
                "kcal",
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(
                modifier = Modifier.height(42.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                Text(axisMaxCalories.formatCaloriesTick(), style = MaterialTheme.typography.labelSmall, color = labelColor)
                Text(midTickCalories.formatCaloriesTick(), style = MaterialTheme.typography.labelSmall, color = labelColor)
                Text("0", style = MaterialTheme.typography.labelSmall, color = labelColor)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Canvas(modifier = Modifier.fillMaxWidth().height(42.dp)) {
                    if (buckets.isEmpty()) return@Canvas

                    val gap = 1.dp.toPx()
                    val count = buckets.size
                    val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1f)
                    val minEmptyHeight = 1.dp.toPx()
                    val minFilledHeight = 3.dp.toPx()
                    val cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    listOf(0f, size.height / 2f, size.height).forEach { y ->
                        drawLine(color = gridColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }

                    buckets.forEachIndexed { index, bucket ->
                        val fraction = if (axisMaxCalories > 0.0) (bucket.caloriesKcal / axisMaxCalories).toFloat().coerceIn(0f, 1f) else 0f
                        val isFilled = bucket.caloriesKcal > 0.0
                        val barHeight = if (isFilled) (size.height * fraction).coerceAtLeast(minFilledHeight) else minEmptyHeight
                        val left = index * (barWidth + gap)
                        drawRoundRect(
                            color = if (isFilled) barColor else emptyBarColor,
                            topLeft = Offset(left, size.height - barHeight),
                            size = Size(barWidth, barHeight),
                            cornerRadius = cornerRadius,
                        )
                    }

                }
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(timeLabels.start, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = labelColor)
                    timeLabels.midpoint?.let {
                        Text(
                            it,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelSmall,
                            color = labelColor,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        timeLabels.end,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private data class HistogramTimeLabels(
    val start: String,
    val midpoint: String?,
    val end: String,
)

private fun formatHistogramTimeLabels(buckets: List<MealCaloriesBucket>): HistogramTimeLabels {
    if (buckets.isEmpty()) return HistogramTimeLabels("00:00", null, "23:59")
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    val start = buckets.first().startInclusive
    val end = buckets.last().end.minusSeconds(60)
    val midpoint = start.plusMillis(Duration.between(start, buckets.last().end).toMillis() / 2)
    return HistogramTimeLabels(
        start = formatter.format(start),
        midpoint = if (buckets.size >= 8) formatter.format(midpoint) else null,
        end = formatter.format(end),
    )
}

private fun niceCaloriesAxisMax(maxCalories: Double): Double {
    if (maxCalories <= 0.0) return 100.0
    val steps = listOf(50.0, 100.0, 200.0, 500.0, 1000.0, 2000.0, 5000.0)
    return steps.firstOrNull { it >= maxCalories } ?: (ceil(maxCalories / 5000.0) * 5000.0)
}

private fun Double.formatCaloriesTick(): String = roundToInt().toString()
