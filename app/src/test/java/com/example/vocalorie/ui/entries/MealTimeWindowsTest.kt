package com.example.vocalorie.ui.entries

import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.ActivityType
import com.example.vocalorie.model.NutritionTotals
import com.example.vocalorie.model.SavedActivity
import com.example.vocalorie.model.SavedMeal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class MealTimeWindowsTest {
    private val zone: ZoneId = ZoneId.of("Europe/London")
    private val now: Instant = LocalDateTime.of(2026, 6, 30, 15, 0).atZone(zone).toInstant()

    @Test
    fun todayOffsetIncludesLocalCalendarTodayAndSameDayFutureMeals() {
        val startOfToday = LocalDateTime.of(2026, 6, 30, 0, 0).atZone(zone).toInstant()
        val today = meal(1, now.minusSeconds(2 * 60 * 60))
        val startBoundary = meal(2, startOfToday)
        val previousDayWithin24Hours = meal(3, startOfToday.minusSeconds(1))
        val sameDayFuture = meal(4, now.plusSeconds(1))

        val visible = filterMealsForDay(listOf(today, startBoundary, previousDayWithin24Hours, sameDayFuture), dayOffset = 0, now = now, zone = zone)

        assertEquals(listOf(4L, 1L, 2L), visible.map { it.id })
    }

    @Test
    fun visibleMealsAreSortedByEntryTimeDescendingAfterFiltering() {
        val todayEight = meal(1, LocalDateTime.of(2026, 6, 30, 8, 0).atZone(zone).toInstant())
        val todayNine = meal(2, LocalDateTime.of(2026, 6, 30, 9, 0).atZone(zone).toInstant())
        val yesterdayEight = meal(3, LocalDateTime.of(2026, 6, 29, 8, 0).atZone(zone).toInstant())
        val yesterdayNine = meal(4, LocalDateTime.of(2026, 6, 29, 9, 0).atZone(zone).toInstant())
        val mealsNewestFirst = listOf(todayNine, todayEight, yesterdayNine, yesterdayEight)

        val todayVisible = filterMealsForDay(mealsNewestFirst, dayOffset = 0, now = now, zone = zone)
        val yesterdayVisible = filterMealsForDay(mealsNewestFirst, dayOffset = 1, now = now, zone = zone)

        assertEquals(listOf(2L, 1L), todayVisible.map { it.id })
        assertEquals(listOf(4L, 3L), yesterdayVisible.map { it.id })
    }

    @Test
    fun activitiesUseTheSameSelectedDayFilteringAndSorting() {
        val todayEight = activity(1, LocalDateTime.of(2026, 6, 30, 8, 0).atZone(zone).toInstant())
        val todayNine = activity(2, LocalDateTime.of(2026, 6, 30, 9, 0).atZone(zone).toInstant())
        val yesterdayNine = activity(3, LocalDateTime.of(2026, 6, 29, 9, 0).atZone(zone).toInstant())

        val visible = filterActivitiesForDay(listOf(todayNine, todayEight, yesterdayNine), dayOffset = 0, now = now, zone = zone)

        assertEquals(listOf(2L, 1L), visible.map { it.id })
    }

    @Test
    fun todayVisibleMealsAreIndependentFromLast24HourStats() {
        val lastNightWithin24Hours = meal(1, now.minusSeconds(16 * 60 * 60), calories = 100.0)
        val currentDay = meal(2, now.minusSeconds(2 * 60 * 60), calories = 85.0)
        val olderThan24Hours = meal(3, now.minusSeconds(25 * 60 * 60), calories = 500.0)
        val sameDayFuture = meal(4, now.plusSeconds(1), calories = 300.0)
        val meals = listOf(lastNightWithin24Hours, currentDay, olderThan24Hours, sameDayFuture)

        val visible = filterMealsForDay(meals, dayOffset = 0, now = now, zone = zone)
        val last24HourStats = selectedTimelineStats(
            meals,
            now,
            zone,
            selection = MealStatsWindowSelection(mode = MealStatsWindowMode.LAST_24_HOURS),
        ).stats

        assertEquals(listOf(4L, 2L), visible.map { it.id })
        assertEquals(185.0, last24HourStats.caloriesKcal, 0.0)
    }

    @Test
    fun selectedDayWindowLabelsTodayWithoutLast24HoursCopy() {
        val window = selectedDayWindow(dayOffset = 0, now = now, zone = zone)

        assertTrue(window.label.startsWith("Today"))
        assertEquals("Today · 30.06.2026", window.label)
    }

    @Test
    fun formatDurationProducesHoursAndMinutes() {
        assertEquals("0m", formatDuration(0))
        assertEquals("45m", formatDuration(45))
        assertEquals("1h", formatDuration(60))
        assertEquals("1h 1m", formatDuration(61))
    }

    @Test
    fun dailyEnergyBalanceReturnsSignedDifference() {
        assertEquals(-400.0, dailyEnergyBalance(2000.0, 2400.0, 0.0), 0.0)
        assertEquals(400.0, dailyEnergyBalance(2800.0, 2400.0, 0.0), 0.0)
        assertEquals(-300.0, dailyEnergyBalance(2400.0, 2400.0, 300.0), 0.0)
    }

    @Test
    fun selectedDayTimestampUsesViewedDayAtCurrentWallClockTime() {
        // Offset 0 resolves to ~now (today at the current time-of-day).
        assertEquals(now.toEpochMilli(), selectedDayTimestampMillis(dayOffset = 0, now = now, zone = zone))

        // Past/future offsets land on that calendar day at the same 15:00 wall-clock time.
        val yesterdayAtNowTime = LocalDateTime.of(2026, 6, 29, 15, 0).atZone(zone).toInstant()
        val tomorrowAtNowTime = LocalDateTime.of(2026, 7, 1, 15, 0).atZone(zone).toInstant()
        assertEquals(yesterdayAtNowTime.toEpochMilli(), selectedDayTimestampMillis(dayOffset = 1, now = now, zone = zone))
        assertEquals(tomorrowAtNowTime.toEpochMilli(), selectedDayTimestampMillis(dayOffset = -1, now = now, zone = zone))
    }

    @Test
    fun selectedDayTimestampFallsInsideThatDaysWindow() {
        for (offset in listOf(-2, 0, 1, 5)) {
            val stamp = selectedDayTimestampMillis(dayOffset = offset, now = now, zone = zone)
            val window = selectedDayWindow(dayOffset = offset, now = now, zone = zone)
            assertTrue("offset $offset stamp should be inside its day window", window.contains(stamp))
        }
    }

    @Test
    fun negativeDayOffsetNavigatesToTomorrow() {
        val window = selectedDayWindow(dayOffset = -1, now = now, zone = zone)
        val tomorrowStart = LocalDateTime.of(2026, 7, 1, 0, 0).atZone(zone).toInstant()
        val dayAfterTomorrowStart = LocalDateTime.of(2026, 7, 2, 0, 0).atZone(zone).toInstant()

        assertEquals(tomorrowStart, window.startInclusive)
        assertEquals(dayAfterTomorrowStart, window.end)
        assertEquals(false, window.endInclusive)
    }

    @Test
    fun todayWindowEndIsEndOfLocalDayNotNow() {
        val window = selectedDayWindow(dayOffset = 0, now = now, zone = zone)
        val endOfToday = LocalDateTime.of(2026, 7, 1, 0, 0).atZone(zone).toInstant()

        assertEquals(endOfToday, window.end)
        assertTrue(window.end.isAfter(now))
    }

    @Test
    fun selectedDayWindowLabelsYesterdayWithDate() {
        val window = selectedDayWindow(dayOffset = 1, now = now, zone = zone)

        assertTrue(window.label.startsWith("Yesterday"))
        assertEquals("Yesterday · 29.06.2026", window.label)
    }

    @Test
    fun yesterdayOffsetUsesLocalCalendarDayBoundaries() {
        val yesterdayWindow = selectedDayWindow(dayOffset = 1, now = now, zone = zone)
        val yesterdayStart = LocalDateTime.of(2026, 6, 29, 0, 0).atZone(zone).toInstant()
        val yesterdayEndMinusOne = LocalDateTime.of(2026, 6, 29, 23, 59, 59).atZone(zone).toInstant()
        val dayBefore = yesterdayStart.minusSeconds(1)
        val todayStart = LocalDateTime.of(2026, 6, 30, 0, 0).atZone(zone).toInstant()

        val visible = filterMealsForDay(
            listOf(
                meal(1, yesterdayStart),
                meal(2, yesterdayEndMinusOne),
                meal(3, dayBefore),
                meal(4, todayStart),
            ),
            dayOffset = 1,
            now = now,
            zone = zone,
        )

        assertTrue(yesterdayWindow.label.startsWith("Yesterday"))
        assertEquals("Yesterday · 29.06.2026", yesterdayWindow.label)
        assertEquals(listOf(2L, 1L), visible.map { it.id })
    }

    @Test
    fun sinceMidnightUsesSelectedCalendarDayWhenBrowsingNonTodayDay() {
        val yesterdayStart = LocalDateTime.of(2026, 6, 29, 0, 0).atZone(zone).toInstant()
        val yesterdayNoon = LocalDateTime.of(2026, 6, 29, 12, 0).atZone(zone).toInstant()
        val todayNoon = LocalDateTime.of(2026, 6, 30, 12, 0).atZone(zone).toInstant()

        val stats = selectedTimelineStats(
            meals = listOf(
                meal(1, yesterdayStart.plusSeconds(60 * 60), calories = 100.0),
                meal(2, yesterdayNoon, calories = 200.0),
                meal(3, todayNoon, calories = 999.0),
            ),
            now = now,
            zone = zone,
            selection = MealStatsWindowSelection(mode = MealStatsWindowMode.SINCE_MIDNIGHT),
            selectedDayOffset = 1,
        )

        assertEquals("Since 00:00", stats.label)
        assertEquals(300.0, stats.stats.caloriesKcal, 0.0)
    }

    @Test
    fun selectedStatsSumNutritionForExpectedWindowsAndIgnoreNulls() {
        val meals = listOf(
            meal(1, now.minusSeconds(60 * 60), calories = 100.0, protein = 10.0, carbs = 20.0, fat = 5.0, amount = 150.0, saturatedFat = 1.0, sugar = 2.0, salt = 0.1),
            meal(2, LocalDateTime.of(2026, 6, 30, 8, 0).atZone(zone).toInstant(), calories = 200.0, protein = null, carbs = 30.0, fat = 10.0, amount = null, saturatedFat = 2.0, sugar = 3.0, salt = 0.2),
            meal(3, now.minusSeconds(5 * 60 * 60), calories = 300.0, protein = 30.0, carbs = 40.0, fat = null, amount = 250.0, saturatedFat = 3.0, sugar = 4.0, salt = 0.3),
            meal(4, now.minusSeconds(23 * 60 * 60), calories = 400.0, protein = 40.0, carbs = 50.0, fat = 20.0, amount = 500.0, saturatedFat = 4.0, sugar = 5.0, salt = 0.4),
        )

        val last24Hours = selectedTimelineStats(meals, now, zone, selection = MealStatsWindowSelection(mode = MealStatsWindowMode.LAST_24_HOURS))
        val sinceMidnight = selectedTimelineStats(meals, now, zone, selection = MealStatsWindowSelection(mode = MealStatsWindowMode.SINCE_MIDNIGHT))
        val custom = selectedTimelineStats(meals, now, zone, selection = MealStatsWindowSelection(mode = MealStatsWindowMode.CUSTOM))

        assertEquals("Last 24h", last24Hours.label)
        assertStats(last24Hours.stats, calories = 1000.0, protein = 80.0, carbs = 140.0, fat = 35.0, amount = 900.0, saturatedFat = 10.0, sugar = 14.0, salt = 1.0)
        assertEquals("Since 00:00", sinceMidnight.label)
        assertStats(sinceMidnight.stats, calories = 600.0, protein = 40.0, carbs = 90.0, fat = 15.0, amount = 400.0, saturatedFat = 6.0, sugar = 9.0, salt = 0.6)
        assertEquals("Last 4h", custom.label)
        assertStats(custom.stats, calories = 100.0, protein = 10.0, carbs = 20.0, fat = 5.0, amount = 150.0, saturatedFat = 1.0, sugar = 2.0, salt = 0.1)
    }

    @Test
    fun sinceMidnightStatsUseTheFullSelectedCalendarDayForNonTodayOffsets() {
        val yesterdayStart = LocalDateTime.of(2026, 6, 29, 0, 0).atZone(zone).toInstant()
        val yesterdayLate = LocalDateTime.of(2026, 6, 29, 23, 30).atZone(zone).toInstant()
        val todayStart = LocalDateTime.of(2026, 6, 30, 0, 0).atZone(zone).toInstant()

        val stats = selectedTimelineStats(
            listOf(
                meal(1, yesterdayStart, calories = 100.0),
                meal(2, yesterdayLate, calories = 200.0),
                meal(3, todayStart, calories = 300.0),
            ),
            now,
            zone,
            selectedDayOffset = 1,
            selection = MealStatsWindowSelection(mode = MealStatsWindowMode.SINCE_MIDNIGHT),
        )

        assertEquals("Since 00:00", stats.label)
        assertStats(stats.stats, calories = 300.0, protein = 2.0, carbs = 2.0, fat = 2.0, amount = 2.0)
    }

    @Test
    fun selectedDayHistogramUsesTheFullSelectedCalendarDayForNonTodayOffsets() {
        val yesterdayStart = LocalDateTime.of(2026, 6, 29, 0, 0).atZone(zone).toInstant()
        val yesterdayLate = LocalDateTime.of(2026, 6, 29, 23, 45).atZone(zone).toInstant()
        val todayStart = LocalDateTime.of(2026, 6, 30, 0, 0).atZone(zone).toInstant()

        val buckets = selectedDayCaloriesHistogram(
            meals = listOf(
                meal(1, yesterdayStart, calories = 100.0),
                meal(2, yesterdayLate, calories = 200.0),
                meal(3, todayStart, calories = 300.0),
            ),
            now = now,
            zone = zone,
            selectedDayOffset = 1,
        )

        assertEquals(96, buckets.size)
        assertEquals(300.0, buckets.sumOf { it.caloriesKcal }, 0.0)
        assertEquals(yesterdayStart, buckets.first().startInclusive)
        assertEquals(yesterdayStart.plus(Duration.ofMinutes(15)), buckets.first().end)
        assertEquals(200.0, buckets.last().caloriesKcal, 0.0)
        assertEquals(todayStart, buckets.last().end)
    }

    @Test
    fun selectedTimelineStatsUseCustomRollingDurationAndLabel() {
        val within45Minutes = meal(1, now.minusSeconds(44 * 60), calories = 100.0)
        val outside45Minutes = meal(2, now.minusSeconds(46 * 60), calories = 250.0)

        val stats = selectedTimelineStats(
            listOf(within45Minutes, outside45Minutes),
            now,
            zone,
            selection = MealStatsWindowSelection(mode = MealStatsWindowMode.CUSTOM, customDuration = Duration.ofMinutes(45)),
        )

        assertEquals("Last 45 mins", stats.label)
        assertStats(stats.stats, calories = 100.0, protein = 1.0, carbs = 1.0, fat = 1.0, amount = 1.0)
    }

    @Test
    fun selectedStatsExposeExactlyOneStatsBlock() {
        val stats = selectedTimelineStats(
            meals = emptyList(),
            now = now,
            zone = zone,
            selection = MealStatsWindowSelection(mode = MealStatsWindowMode.SINCE_MIDNIGHT),
        )

        assertEquals("Since 00:00", stats.label)
    }

    @Test
    fun caloriesHistogramAggregatesMealsIntoQuarterHourBucketsForSelectedWindow() {
        val start = now.minus(Duration.ofHours(1))
        val meals = listOf(
            meal(1, start, calories = 100.0),
            meal(2, start.plus(Duration.ofMinutes(14)).plusSeconds(59), calories = 50.0),
            meal(3, start.plus(Duration.ofMinutes(15)), calories = 200.0),
            meal(4, start.plus(Duration.ofMinutes(45)), calories = null),
            meal(5, start.minusSeconds(1), calories = 999.0),
            meal(6, now.plusSeconds(1), calories = 999.0),
        )

        val buckets = selectedCaloriesHistogram(
            meals = meals,
            now = now,
            zone = zone,
            selection = MealStatsWindowSelection(mode = MealStatsWindowMode.CUSTOM, customDuration = Duration.ofHours(1)),
        )

        assertEquals(listOf(150.0, 200.0, 0.0, 0.0), buckets.map { it.caloriesKcal })
        assertEquals(start, buckets.first().startInclusive)
        assertEquals(start.plus(Duration.ofMinutes(15)), buckets.first().end)
    }

    @Test
    fun caloriesHistogramUsesTheSelectedStatsWindowNotTheVisibleDayFilter() {
        val lastNightWithin24Hours = meal(1, now.minus(Duration.ofHours(16)), calories = 100.0)
        val todayWithin24Hours = meal(2, now.minus(Duration.ofHours(2)), calories = 85.0)
        val olderThan24Hours = meal(3, now.minus(Duration.ofHours(25)), calories = 500.0)

        val last24Hours = selectedCaloriesHistogram(
            meals = listOf(lastNightWithin24Hours, todayWithin24Hours, olderThan24Hours),
            now = now,
            zone = zone,
            selection = MealStatsWindowSelection(mode = MealStatsWindowMode.LAST_24_HOURS),
        )
        val sinceMidnight = selectedCaloriesHistogram(
            meals = listOf(lastNightWithin24Hours, todayWithin24Hours, olderThan24Hours),
            now = now,
            zone = zone,
            selection = MealStatsWindowSelection(mode = MealStatsWindowMode.SINCE_MIDNIGHT),
        )

        assertEquals(185.0, last24Hours.sumOf { it.caloriesKcal }, 0.0)
        assertEquals(85.0, sinceMidnight.sumOf { it.caloriesKcal }, 0.0)
    }

    @Test
    fun caloriesHistogramIncludesMealAtInclusiveWindowEndInFinalBucket() {
        val buckets = selectedCaloriesHistogram(
            meals = listOf(meal(1, now, calories = 120.0)),
            now = now,
            zone = zone,
            selection = MealStatsWindowSelection(mode = MealStatsWindowMode.CUSTOM, customDuration = Duration.ofMinutes(15)),
        )

        assertEquals(listOf(120.0), buckets.map { it.caloriesKcal })
    }

    @Test
    fun customRollingDurationLabelsAreNatural() {
        assertEquals("Last 45 mins", formatRollingStatsLabel(Duration.ofMinutes(45)))
        assertEquals("Last 1h 30 mins", formatRollingStatsLabel(Duration.ofMinutes(90)))
        assertEquals("Last 23h", formatRollingStatsLabel(Duration.ofHours(23)))
        assertEquals("Last 4h", formatRollingStatsLabel(Duration.ofHours(4)))
        assertEquals("Last 24h", formatRollingStatsLabel(Duration.ofHours(24)))
    }

    @Test
    fun customRollingDurationIsClampedToAllowedQuarterHourRange() {
        assertEquals(Duration.ofMinutes(15), normalizeRollingStatsDuration(Duration.ofMinutes(1)))
        assertEquals(Duration.ofMinutes(15), normalizeRollingStatsDuration(Duration.ofMinutes(22)))
        assertEquals(Duration.ofMinutes(30), normalizeRollingStatsDuration(Duration.ofMinutes(23)))
        assertEquals(Duration.ofHours(24), normalizeRollingStatsDuration(Duration.ofHours(25)))
    }

    private fun assertStats(
        stats: MealNutritionStats,
        calories: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        amount: Double,
        saturatedFat: Double = 0.0,
        sugar: Double = 0.0,
        salt: Double = 0.0,
    ) {
        assertEquals(calories, stats.caloriesKcal, 0.0)
        assertEquals(protein, stats.proteinG, 0.0)
        assertEquals(carbs, stats.carbsG, 0.0)
        assertEquals(fat, stats.fatG, 0.0)
        assertEquals(amount, stats.amountGml, 0.0)
        assertEquals(saturatedFat, stats.saturatedFatG, 1e-9)
        assertEquals(sugar, stats.sugarG, 1e-9)
        assertEquals(salt, stats.saltG, 1e-9)
    }

    private fun meal(
        id: Long,
        createdAt: Instant,
        calories: Double? = 1.0,
        protein: Double? = 1.0,
        carbs: Double? = 1.0,
        fat: Double? = 1.0,
        amount: Double? = 1.0,
        saturatedFat: Double? = 0.0,
        sugar: Double? = 0.0,
        salt: Double? = 0.0,
    ) = SavedMeal(
        id = id,
        createdAtEpochMillis = createdAt.toEpochMilli(),
        query = "meal $id",
        items = emptyList(),
        totals = NutritionTotals(
            caloriesKcal = calories,
            amountGml = amount,
            proteinG = protein,
            carbsG = carbs,
            fatG = fat,
            saturatedFatG = saturatedFat,
            sugarG = sugar,
            saltG = salt,
        ),
        assumptions = emptyList(),
        warnings = emptyList(),
        confidence = ConfidenceLevel.MEDIUM,
        needsHumanReview = false,
    )

    @Test
    fun dayOffsetAfterDayChangeKeepsTodayAtZero() {
        assertEquals(0, dayOffsetAfterDayChange(offset = 0, daysPassed = 1))
    }

    @Test
    fun dayOffsetAfterDayChangeShiftsAPastDayForwardByOneDayPassed() {
        assertEquals(2, dayOffsetAfterDayChange(offset = 1, daysPassed = 1))
    }

    @Test
    fun dayOffsetAfterDayChangeShiftsATomorrowOffsetBackToTodayAfterOneDayPassed() {
        assertEquals(0, dayOffsetAfterDayChange(offset = -1, daysPassed = 1))
    }

    @Test
    fun dayOffsetAfterDayChangeShiftsByMultipleDaysPassed() {
        assertEquals(5, dayOffsetAfterDayChange(offset = 3, daysPassed = 2))
    }

    @Test
    fun dayOffsetAfterDayChangeIsUnchangedWhenNoDaysHavePassed() {
        assertEquals(3, dayOffsetAfterDayChange(offset = 3, daysPassed = 0))
    }

    @Test
    fun durationUntilNextLocalMidnightAtMiddayIsTwelveHours() {
        val midday = LocalDateTime.of(2026, 6, 30, 12, 0).atZone(zone).toInstant()

        assertEquals(Duration.ofHours(12), durationUntilNextLocalMidnight(midday, zone))
    }

    @Test
    fun durationUntilNextLocalMidnightASecondBeforeMidnightIsOneSecond() {
        val almostMidnight = LocalDateTime.of(2026, 6, 30, 23, 59, 59).atZone(zone).toInstant()

        assertEquals(Duration.ofSeconds(1), durationUntilNextLocalMidnight(almostMidnight, zone))
    }

    @Test
    fun durationUntilNextLocalMidnightAccountsForALostSpringForwardHour() {
        // 2026-03-29 is the Europe/London spring-forward day: clocks jump 01:00 -> 02:00, so this
        // calendar day only has 23 real hours between its midnight and the next.
        val startOfSpringForwardDay = LocalDateTime.of(2026, 3, 29, 0, 0).atZone(zone).toInstant()

        assertEquals(Duration.ofHours(23), durationUntilNextLocalMidnight(startOfSpringForwardDay, zone))
    }

    private fun activity(id: Long, createdAt: Instant) = SavedActivity(
        id = id,
        createdAtEpochMillis = createdAt.toEpochMilli(),
        type = ActivityType.RUNNING,
        title = "activity $id",
        description = "",
        caloriesBurnedKcal = 100.0,
        durationMinutes = 30,
    )
}
