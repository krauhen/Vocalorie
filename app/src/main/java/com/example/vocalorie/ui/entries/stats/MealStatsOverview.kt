package com.example.vocalorie.ui.entries.stats

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vocalorie.model.NutritionGoals
import com.example.vocalorie.model.SavedActivity
import com.example.vocalorie.model.SavedMeal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import kotlin.math.roundToInt

private val HeatmapDeepGreen = Color(0xFF2E7D32)
private val HeatmapGreen = Color(0xFF43A047)
private val HeatmapYellow = Color(0xFFFDD835)
private val HeatmapOrange = Color(0xFFF57C00)
private val HeatmapDeepRed = Color(0xFFB71C1C)

// Score scale terminates at green (best): low scores red, mid yellow, high green.
internal fun scoreToColor(score: Double): Color = when {
    score <= 20.0 -> lerp(HeatmapDeepRed, HeatmapOrange, (score / 20.0).toFloat())
    score <= 40.0 -> lerp(HeatmapOrange, HeatmapYellow, ((score - 20.0) / 20.0).toFloat())
    score <= 60.0 -> lerp(HeatmapYellow, HeatmapGreen, ((score - 40.0) / 20.0).toFloat())
    score <= 100.0 -> lerp(HeatmapGreen, HeatmapDeepGreen, ((score - 60.0) / 40.0).toFloat())
    else -> HeatmapDeepGreen
}

@Composable
fun MealStatsOverview(
    meals: List<SavedMeal>,
    selectedRange: MealStatsRange,
    onRangeChange: (MealStatsRange) -> Unit,
    modifier: Modifier = Modifier,
    activities: List<SavedActivity> = emptyList(),
    goals: NutritionGoals = NutritionGoals.DEFAULT,
    zone: ZoneId = ZoneId.systemDefault(),
    selectedDate: LocalDate? = null,
    onDateSelected: (LocalDate) -> Unit = {},
) {
    val stats = computeMealStats(meals, selectedRange, Instant.now(), zone)
    val activityBurnedByDate = remember(activities, zone) {
        activities.groupingBy { Instant.ofEpochMilli(it.createdAtEpochMillis).atZone(zone).toLocalDate() }
            .fold(0.0) { acc, activity -> acc + activity.caloriesBurnedKcal }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            MealStatsRangeSelector(
                selectedRange = selectedRange,
                onRangeChange = onRangeChange,
                modifier = Modifier.fillMaxWidth(),
            )
            MealStatsTilesRow(stats = stats)
            MealStatsHeatmap(
                heatmap = stats.heatmap,
                dailyTotals = stats.dailyTotals,
                rangeStartDate = stats.rangeStartDate,
                goals = goals,
                activityBurnedByDate = activityBurnedByDate,
                selectedDate = selectedDate,
                onDateSelected = onDateSelected,
            )
        }
    }
}

@Composable
private fun MealStatsRangeSelector(
    selectedRange: MealStatsRange,
    onRangeChange: (MealStatsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        MealStatsRangeOption("All", MealStatsRange.ALL, selectedRange, onRangeChange, Modifier.weight(1f))
        MealStatsRangeOption("30d", MealStatsRange.LAST_30_DAYS, selectedRange, onRangeChange, Modifier.weight(1f))
        MealStatsRangeOption("7d", MealStatsRange.LAST_7_DAYS, selectedRange, onRangeChange, Modifier.weight(1f))
    }
}

@Composable
private fun MealStatsRangeOption(
    label: String,
    range: MealStatsRange,
    selectedRange: MealStatsRange,
    onRangeChange: (MealStatsRange) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSelected = range == selectedRange
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(backgroundColor)
            .clickable(onClick = { onRangeChange(range) })
            .padding(vertical = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = contentColor,
        )
    }
}

@Composable
private fun MealStatsTilesRow(stats: MealStats) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MealStatTile("Meals", stats.mealsLogged.toString(), Modifier.weight(1f))
        MealStatTile("Active", stats.activeDays.toString(), Modifier.weight(1f))
        MealStatTile("Streak", "${stats.currentStreak}d", Modifier.weight(1f))
        MealStatTile("Best", "${stats.longestStreak}d", Modifier.weight(1f))
        MealStatTile("Avg kcal", stats.avgDailyCaloriesKcal.roundToInt().toString(), Modifier.weight(1f))
    }
}

@Composable
private fun MealStatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(6.dp), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun MealStatsHeatmap(
    heatmap: Map<LocalDate, Double>,
    dailyTotals: Map<LocalDate, DailyNutritionTotals>,
    rangeStartDate: LocalDate,
    goals: NutritionGoals,
    activityBurnedByDate: Map<LocalDate, Double>,
    selectedDate: LocalDate? = null,
    onDateSelected: (LocalDate) -> Unit = {},
) {
    if (heatmap.isEmpty()) {
        Text(
            "No logged days yet.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val sortedDates = heatmap.keys.sorted()
    val startDate = sortedDates.first()
    val today = sortedDates.last()
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant
    val outOfRangeColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val crossColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    val selectedIndicatorColor = MaterialTheme.colorScheme.primary
    val selectedIndicatorHaloColor = MaterialTheme.colorScheme.surface
    val formatter = DateTimeFormatter.ofPattern("d MMM")
    val weekFields = WeekFields.ISO

    // Column-major: each column is one Mon-Sun week, oldest (complete) week on the left,
    // current (possibly partial) week on the right.
    val columns = (0 until HEATMAP_COLUMN_COUNT).map { col ->
        val weekMonday = startDate.plusDays(col.toLong() * 7)
        weekMonday to (0 until HEATMAP_ROW_COUNT).map { row -> weekMonday.plusDays(row.toLong()) }
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        columns.forEach { (weekMonday, _) ->
            Text(
                text = weekMonday.get(weekFields.weekOfWeekBasedYear()).toString(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (row in 0 until HEATMAP_ROW_COUNT) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                columns.forEach { (_, weekDates) ->
                    val date = weekDates[row]
                    val calories = if (date.isAfter(today)) null else heatmap[date]
                    val score = if (date.isAfter(today)) null else dailyTotals[date]?.let {
                        nutritionScore(it, goals, activityBurnedByDate[date] ?: 0.0)
                    }
                    val isOutOfRange = date.isBefore(rangeStartDate)
                    val isTracked = calories != null && calories > 0.0
                    val isSelected = date == selectedDate
                    val cellColor = when {
                        calories == null -> Color.Transparent
                        score == null -> emptyColor
                        isOutOfRange -> outOfRangeColor
                        else -> scoreToColor(score)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(cellColor)
                            .clickable(onClick = { onDateSelected(date) }),
                    ) {
                        if (isOutOfRange && isTracked) {
                            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                                val strokeWidth = size.minDimension * 0.12f
                                drawLine(crossColor, start = Offset(0f, 0f), end = Offset(size.width, size.height), strokeWidth = strokeWidth)
                                drawLine(crossColor, start = Offset(size.width, 0f), end = Offset(0f, size.height), strokeWidth = strokeWidth)
                            }
                        }
                        if (isSelected) {
                            Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
                                val haloWidth = 4.dp.toPx()
                                val haloInset = haloWidth / 2
                                drawRect(
                                    color = selectedIndicatorHaloColor,
                                    topLeft = Offset(haloInset, haloInset),
                                    size = Size(size.width - 2 * haloInset, size.height - 2 * haloInset),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(haloWidth),
                                )
                                val borderStrokeWidth = 2.dp.toPx()
                                val borderInset = borderStrokeWidth / 2
                                drawRect(
                                    color = selectedIndicatorColor,
                                    topLeft = Offset(borderInset, borderInset),
                                    size = Size(size.width - 2 * borderInset, size.height - 2 * borderInset),
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(borderStrokeWidth),
                                )

                                val center = Offset(size.width / 2, size.height / 2)
                                val ringStrokeWidth = size.minDimension * 0.14f
                                val diameter = size.minDimension * 0.6f
                                drawCircle(
                                    color = selectedIndicatorHaloColor,
                                    radius = diameter / 2,
                                    center = center,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(ringStrokeWidth + 2.dp.toPx()),
                                )
                                drawCircle(
                                    color = selectedIndicatorColor,
                                    radius = diameter / 2,
                                    center = center,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(ringStrokeWidth),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val firstLabel = startDate.format(formatter)
    val lastLabel = today.format(formatter)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(firstLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(lastLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
