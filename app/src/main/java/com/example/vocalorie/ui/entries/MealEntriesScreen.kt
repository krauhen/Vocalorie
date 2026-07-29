package com.example.vocalorie.ui.entries

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.outlined.RestaurantMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.example.vocalorie.model.SavedMeal
import com.example.vocalorie.model.NutritionGoals
import com.example.vocalorie.model.SavedActivity
import com.example.vocalorie.ui.entries.stats.DailyNutritionTotals
import com.example.vocalorie.ui.entries.stats.MealStatsOverview
import com.example.vocalorie.ui.entries.stats.MealStatsRange
import com.example.vocalorie.ui.entries.stats.nutritionScore
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

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
                    now = now,
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
