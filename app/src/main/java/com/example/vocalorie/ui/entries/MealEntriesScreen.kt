package com.example.vocalorie.ui.entries

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.vocalorie.model.MealCategory
import com.example.vocalorie.model.SavedMeal
import com.example.vocalorie.model.NutritionGoals
import com.example.vocalorie.model.SavedActivity
import com.example.vocalorie.model.displayName
import com.example.vocalorie.model.activityTypeIcon
import com.example.vocalorie.ui.components.HeaderDropdownAction
import com.example.vocalorie.ui.components.NutritionLabelRows
import com.example.vocalorie.ui.components.formatDate
import com.example.vocalorie.ui.components.formatNullable
import com.example.vocalorie.ui.components.activityCalorieStateStyle
import com.example.vocalorie.ui.components.mealCalorieStateStyle
import com.example.vocalorie.ui.components.ReadOnlyActivitySummary
import com.example.vocalorie.ui.macroColors
import com.example.vocalorie.ui.entries.stats.DailyNutritionTotals
import com.example.vocalorie.ui.entries.stats.MealStatsOverview
import com.example.vocalorie.ui.entries.stats.MealStatsRange
import com.example.vocalorie.ui.entries.stats.nutritionScore
import com.example.vocalorie.ui.entries.dailyEnergyBalance
import com.example.vocalorie.ui.entries.formatDuration
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Minimum time the pull-to-refresh indicator stays up, so a near-instant reload still feels deliberate. */
private const val REFRESH_INDICATOR_MIN_MILLIS: Long = 600L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealEntriesScreen(
    meals: List<SavedMeal>,
    activities: List<SavedActivity> = emptyList(),
    selectedTab: EntriesTab = EntriesTab.MEALS,
    onSelectTab: (EntriesTab) -> Unit = {},
    onOpenMeal: (SavedMeal) -> Unit,
    onOpenActivity: (SavedActivity) -> Unit = {},
    onAddActivity: () -> Unit = {},
    onOpenSettings: () -> Unit,
    onRefresh: suspend () -> Unit = {},
    selectedDayOffset: Int = 0,
    onSelectedDayOffsetChange: (Int) -> Unit = {},
    baseCaloriesBurned: Int = 2400,
    goals: NutritionGoals = NutritionGoals.DEFAULT,
    modifier: Modifier = Modifier,
    voiceButton: @Composable () -> Unit,
) {
    var selectedStatsRangeName by rememberSaveable { mutableStateOf(MealStatsRange.LAST_30_DAYS.name) }
    var selectedStatsModeName by rememberSaveable { mutableStateOf(MealStatsWindowMode.SINCE_MIDNIGHT.name) }
    var customRollingStatsMinutes by rememberSaveable { mutableIntStateOf(DEFAULT_ROLLING_STATS_MINUTES) }
    // `now` drives future/past classification. It advances whenever entries reload (LaunchedEffect
    // below) and on an explicit pull-to-refresh, so a passed-time entry stops being crossed out
    // without requiring a write. Previously it was memoized against `meals`, so it only moved on a save.
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(meals, activities) { now = Instant.now() }
    val refreshScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val visibleMeals = remember(meals, selectedDayOffset, now, zone) { filterMealsForDay(meals, selectedDayOffset, now, zone) }
    val visibleActivities = remember(activities, selectedDayOffset, now, zone) { filterActivitiesForDay(activities, selectedDayOffset, now, zone) }
    val dayWindow = remember(selectedDayOffset, now, zone) { selectedDayWindow(selectedDayOffset, now, zone) }
    val selectedStatsMode = remember(selectedStatsModeName) {
        runCatching { MealStatsWindowMode.valueOf(selectedStatsModeName) }.getOrDefault(MealStatsWindowMode.SINCE_MIDNIGHT)
    }
    val statsSelection = remember(selectedStatsMode, customRollingStatsMinutes) {
        MealStatsWindowSelection(
            mode = selectedStatsMode,
            customDuration = Duration.ofMinutes(customRollingStatsMinutes.toLong()),
        )
    }
    val selectedStatsRange = remember(selectedStatsRangeName) {
        runCatching { MealStatsRange.valueOf(selectedStatsRangeName) }.getOrDefault(MealStatsRange.LAST_30_DAYS)
    }
    val stats = remember(meals, now, zone, selectedDayOffset, statsSelection) { selectedTimelineStats(meals, now, zone, selectedDayOffset, statsSelection) }
    val caloriesHistogram = remember(meals, now, zone, selectedDayOffset) { selectedDayCaloriesHistogram(meals, now, zone, selectedDayOffset) }
    val consumedCalories = remember(visibleMeals) { visibleMeals.toDailyNutritionTotals().caloriesKcal }
    val activitiesCaloriesBurned = remember(visibleActivities) { visibleActivities.sumOf { it.caloriesBurnedKcal } }
    val selectedDayScore = remember(visibleMeals, goals, activitiesCaloriesBurned) {
        nutritionScore(visibleMeals.toDailyNutritionTotals(), goals, activitiesCaloriesBurned)
    }
    val burnedCalories = baseCaloriesBurned + activitiesCaloriesBurned
    val balanceCalories = remember(consumedCalories, activitiesCaloriesBurned, baseCaloriesBurned) {
        dailyEnergyBalance(consumedCalories, baseCaloriesBurned.toDouble(), activitiesCaloriesBurned)
    }
    var showStatsWindowMenu by rememberSaveable { mutableStateOf(false) }
    var showStatsWindowDialog by rememberSaveable { mutableStateOf(false) }
    var showDayStatsDetail by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                now = Instant.now()
                refreshScope.launch {
                    isRefreshing = true
                    try {
                        // The local DB reload is near-instant; hold the indicator briefly so the
                        // pull-to-refresh reads as a deliberate action rather than a flicker.
                        onRefresh()
                        delay(REFRESH_INDICATOR_MIN_MILLIS)
                    } finally {
                        isRefreshing = false
                    }
                }
            },
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                DayNavigator(
                    label = dayWindow.label,
                    canGoNewer = true,
                    onOlder = { onSelectedDayOffsetChange(selectedDayOffset + 1) },
                    onNewer = { onSelectedDayOffsetChange(selectedDayOffset - 1) },
                    onToday = { onSelectedDayOffsetChange(0) },
                )
            }
            item {
                SelectableStatsHeader(
                    stats = stats,
                    dayScore = selectedDayScore,
                    burnedCaloriesKcal = burnedCalories,
                    balanceCaloriesKcal = balanceCalories,
                    caloriesHistogram = caloriesHistogram,
                    changeMenuExpanded = showStatsWindowMenu,
                    onChangeMenuExpandedChange = { showStatsWindowMenu = it },
                    onSelectStatsWindow = { mode ->
                        showStatsWindowMenu = false
                        if (mode == MealStatsWindowMode.CUSTOM) {
                            showStatsWindowDialog = true
                        } else {
                            selectedStatsModeName = mode.name
                        }
                    },
                    onOpenDetail = { showDayStatsDetail = true },
                )
            }
            item {
                MealStatsOverview(
                    meals = meals,
                    activities = activities,
                    goals = goals,
                    selectedRange = selectedStatsRange,
                    onRangeChange = { range -> selectedStatsRangeName = range.name },
                    zone = zone,
                    selectedDate = LocalDate.ofInstant(now, zone).minusDays(selectedDayOffset.toLong()),
                    onDateSelected = { date ->
                        onSelectedDayOffsetChange(ChronoUnit.DAYS.between(date, LocalDate.ofInstant(now, zone)).toInt())
                    },
                )
            }
            item {
                TabRow(selectedTabIndex = selectedTab.ordinal) {
                    Tab(
                        selected = selectedTab == EntriesTab.MEALS,
                        onClick = { onSelectTab(EntriesTab.MEALS) },
                        text = { Text("Meals") },
                        icon = { Icon(Icons.Outlined.RestaurantMenu, contentDescription = null) },
                    )
                    Tab(
                        selected = selectedTab == EntriesTab.ACTIVITIES,
                        onClick = { onSelectTab(EntriesTab.ACTIVITIES) },
                        text = { Text("Activities") },
                        icon = { Icon(Icons.AutoMirrored.Outlined.DirectionsRun, contentDescription = null) },
                    )
                }
            }
            when (selectedTab) {
                EntriesTab.MEALS -> if (visibleMeals.isEmpty()) {
                    item { EmptyEntriesCard(hasSavedEntries = meals.isNotEmpty(), emptyText = "No saved meals yet") }
                } else {
                    items(visibleMeals, key = { it.id }) { meal -> MealEntryRow(meal = meal, now = now, onClick = { onOpenMeal(meal) }) }
                }
                EntriesTab.ACTIVITIES -> if (visibleActivities.isEmpty()) {
                    item { EmptyEntriesCard(hasSavedEntries = activities.isNotEmpty(), emptyText = "No saved activities yet") }
                } else {
                    items(visibleActivities, key = { it.id }) { activity -> ActivityEntryRow(activity = activity, now = now, onClick = { onOpenActivity(activity) }) }
                }
            }
        }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 24.dp, bottom = 20.dp),
        ) {
            when (selectedTab) {
                EntriesTab.MEALS -> voiceButton()
                EntriesTab.ACTIVITIES -> ActivityAddButton(onClick = onAddActivity)
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .navigationBarsPadding()
                .padding(start = 24.dp, bottom = 20.dp),
        ) { SettingsActionButton(onClick = onOpenSettings) }
    }

    if (showStatsWindowDialog) {
        StatsWindowSelectorDialog(
            selectedMode = selectedStatsMode,
            selectedCustomMinutes = customRollingStatsMinutes,
            onDismiss = { showStatsWindowDialog = false },
            onSelected = { mode, minutes ->
                selectedStatsModeName = mode.name
                customRollingStatsMinutes = minutes
                showStatsWindowDialog = false
            },
        )
    }

    if (showDayStatsDetail) {
        DayNutritionDetailDialog(
            label = stats.label,
            stats = stats.stats,
            onDismiss = { showDayStatsDetail = false },
        )
    }
}

@Composable
private fun DayNutritionDetailDialog(label: String, stats: MealNutritionStats, onDismiss: () -> Unit) {
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
private fun SelectableStatsHeader(
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
                    DropdownMenuItem(
                        text = { Text("Since 00:00") },
                        onClick = { onSelectStatsWindow(MealStatsWindowMode.SINCE_MIDNIGHT) },
                    )
                    DropdownMenuItem(
                        text = { Text("Last 24h") },
                        onClick = { onSelectStatsWindow(MealStatsWindowMode.LAST_24_HOURS) },
                    )
                    DropdownMenuItem(
                        text = { Text("Custom") },
                        onClick = { onSelectStatsWindow(MealStatsWindowMode.CUSTOM) },
                    )
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

@Composable
private fun StatsHistogramSeparator() {
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
private fun CaloriesHistogram(buckets: List<MealCaloriesBucket>) {
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

@Composable
private fun StatsWindowSelectorDialog(
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
                StatsModeOption("Since 00:00", MealStatsWindowMode.SINCE_MIDNIGHT, pendingMode) { pendingModeName = it.name }
                StatsModeOption("Last 24h", MealStatsWindowMode.LAST_24_HOURS, pendingMode) { pendingModeName = it.name }
                StatsModeOption("Custom", MealStatsWindowMode.CUSTOM, pendingMode) { pendingModeName = it.name }
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
    label: String,
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
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun DayNavigator(
    label: String,
    canGoNewer: Boolean,
    onOlder: () -> Unit,
    onNewer: () -> Unit,
    onToday: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val primary = MaterialTheme.colorScheme.primary
            val background = MaterialTheme.colorScheme.background
            val headerShape = MaterialTheme.shapes.medium
            val headerBrush = remember(primary, background) {
                Brush.linearGradient(
                    colors = listOf(
                        lerp(primary, background, 0.22f),
                        primary,
                        lerp(primary, background, 0.28f),
                    ),
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(elevation = 6.dp, shape = headerShape, clip = false)
                    .clip(headerShape)
                    .background(headerBrush),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        label,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedButton(
                    onClick = onOlder,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) { DayNavigatorButtonText("Previous day") }
                TextButton(
                    onClick = onToday,
                    enabled = canGoNewer,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) { DayNavigatorButtonText("Today") }
                OutlinedButton(
                    onClick = onNewer,
                    enabled = canGoNewer,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) { DayNavigatorButtonText("Next day") }
            }
        }
    }
}

@Composable
private fun DayNavigatorButtonText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
    )
}

@Composable
private fun SettingsActionButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp, shadowElevation = 6.dp) {
        TextButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
            Text("⚙", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun EmptyEntriesCard(hasSavedEntries: Boolean, emptyText: String) {
    val title = if (hasSavedEntries) "No entries in this time window" else emptyText

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActivityAddButton(onClick: () -> Unit) {
    androidx.compose.material3.ExtendedFloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text("Add", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}

private fun Modifier.futureEntryHighlight(isFuture: Boolean, color: Color): Modifier {
    if (!isFuture) return this
    return drawBehind {
        val cornerRadiusPx = 12.dp.toPx()
        val cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        val hatchPath = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, cornerRadius)) }
        clipPath(hatchPath) {
            val spacingPx = 20.dp.toPx()
            val hatchStrokeWidthPx = 2.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    color = color.copy(alpha = 0.25f),
                    start = Offset(x, size.height),
                    end = Offset(x + size.height, 0f),
                    strokeWidth = hatchStrokeWidthPx,
                )
                x += spacingPx
            }
        }
        val strokeWidthPx = 2.dp.toPx()
        val inset = strokeWidthPx / 2
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
            cornerRadius = cornerRadius,
            style = Stroke(
                width = strokeWidthPx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16.dp.toPx(), 12.dp.toPx()), 0f),
            ),
        )
    }
}

@Composable
private fun MealEntryRow(meal: SavedMeal, now: Instant, onClick: () -> Unit) {
    val style = mealCalorieStateStyle(meal.totals.caloriesKcal)
    val isFuture = Instant.ofEpochMilli(meal.createdAtEpochMillis).isAfter(now)
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = style.containerColor, contentColor = style.contentColor),
            border = if (isFuture) null else BorderStroke(1.dp, style.borderColor),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        meal.title.ifBlank { meal.query },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = meal.category.categoryIcon(),
                        contentDescription = meal.category.name,
                        // Tint with the row's own contentColor (always legible on its container),
                        // not primary — high-calorie rows use primary as their background.
                        tint = style.contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    meal.query,
                    style = MaterialTheme.typography.bodySmall,
                    color = style.contentColor.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    meal.totals.caloriesKcal.formatEnergy(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                val macros = macroColors()
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = macros.fat, fontWeight = FontWeight.SemiBold)) {
                            append("Fat ${meal.totals.fatG.formatNullable()}g")
                        }
                        append(" · ")
                        withStyle(SpanStyle(color = macros.carbs, fontWeight = FontWeight.SemiBold)) {
                            append("Carbs ${meal.totals.carbsG.formatNullable()}g")
                        }
                        append(" · ")
                        withStyle(SpanStyle(color = macros.protein, fontWeight = FontWeight.SemiBold)) {
                            append("Protein ${meal.totals.proteinG.formatNullable()}g")
                        }
                        append(" · Amount ${meal.totals.amountGml.formatNullable()}g/ml")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = style.contentColor,
                )
                Text(
                    formatDate(meal.createdAtEpochMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = style.contentColor.copy(alpha = 0.65f),
                )
            }
        }
        if (isFuture) {
            Box(modifier = Modifier.matchParentSize().futureEntryHighlight(true, style.borderColor))
        }
    }
}

@Composable
private fun ActivityEntryRow(activity: SavedActivity, now: Instant, onClick: () -> Unit) {
    val style = activityCalorieStateStyle(activity.caloriesBurnedKcal)
    val isFuture = Instant.ofEpochMilli(activity.createdAtEpochMillis).isAfter(now)
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = style.containerColor, contentColor = style.contentColor),
            border = if (isFuture) null else BorderStroke(1.dp, style.borderColor),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(activity.type.activityTypeIcon(), contentDescription = null)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        activity.title.ifBlank { activity.type.displayName() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (activity.description.isNotBlank()) {
                        Text(
                            activity.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = style.contentColor.copy(alpha = 0.82f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "${activity.caloriesBurnedKcal.formatNullable()} kcal · ${formatDuration(activity.durationMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = style.contentColor.copy(alpha = 0.82f),
                    )
                }
            }
        }
        if (isFuture) {
            Box(modifier = Modifier.matchParentSize().futureEntryHighlight(true, style.borderColor))
        }
    }
}

private fun List<SavedMeal>.toDailyNutritionTotals(): DailyNutritionTotals = fold(
    DailyNutritionTotals(0.0, 0.0, 0.0, 0.0),
) { acc, meal ->
    acc.copy(
        caloriesKcal = acc.caloriesKcal + (meal.totals.caloriesKcal ?: 0.0),
        proteinG = acc.proteinG + (meal.totals.proteinG ?: 0.0),
        carbsG = acc.carbsG + (meal.totals.carbsG ?: 0.0),
        fatG = acc.fatG + (meal.totals.fatG ?: 0.0),
        saturatedFatG = acc.saturatedFatG + (meal.totals.saturatedFatG ?: 0.0),
        sugarG = acc.sugarG + (meal.totals.sugarG ?: 0.0),
        saltG = acc.saltG + (meal.totals.saltG ?: 0.0),
    )
}

private fun Double?.formatEnergy(): String = this?.let { kcal ->
    "${(kcal * 4.184).roundToInt()} kJ / ${kcal.formatNullable()} kcal"
} ?: "unknown"

/** Total mapping of a meal's food-type category to its list-row icon; OTHER is the neutral default. */
private fun MealCategory.categoryIcon() = when (this) {
    MealCategory.MEAL -> Icons.Outlined.Restaurant
    MealCategory.SNACK -> Icons.Outlined.Cookie
    MealCategory.DRINK -> Icons.Outlined.LocalCafe
    MealCategory.DESSERT -> Icons.Outlined.Cake
    MealCategory.OTHER -> Icons.Outlined.Fastfood
}
