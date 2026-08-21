package com.example.vocalorie.ui.capture

import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.EditableFoodItem
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.MealCategory
import com.example.vocalorie.settings.NutritionSettingsStore
import com.example.vocalorie.ui.entries.stats.DayScoreTipKind
import java.time.Instant
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The tip list the state holder publishes, and the rotation-interval setting behind the strip. */
@OptIn(ExperimentalCoroutinesApi::class)
class DayScoreTipsStateTest {

    /** Midday UTC, so the late-day cutoff never interferes with the tips under test. */
    private val middayMillis = Instant.parse("2024-05-14T12:00:00Z").toEpochMilli()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun environment() = FakeCaptureEnvironment().also { it.nowMillis = middayMillis }

    // --- Tips on the state -------------------------------------------------------------------

    @Test
    fun aTodayWithLoggedMealsExposesARankedTipList() = runTest {
        val environment = environment()
        environment.mealRepository.saveReviewedMeal(mealDraft("Apfel"), middayMillis)

        val tips = environment.viewModel(zone = ZoneId.of("UTC")).uiState.value.dayScoreTips

        assertTrue("a 40 kcal day is far under budget and must yield tips", tips.isNotEmpty())
        assertEquals("the ranked list must not repeat a shortfall", tips.map { it.kind }.distinct().size, tips.size)
    }

    @Test
    fun aDayWithNoLoggedMealsExposesNoTips() = runTest {
        val tips = environment().viewModel(zone = ZoneId.of("UTC")).uiState.value.dayScoreTips

        assertEquals(emptyList<Any>(), tips)
    }

    @Test
    fun aPastDayExposesNoTips() = runTest {
        val environment = environment()
        environment.mealRepository.saveReviewedMeal(mealDraft("Apfel"), middayMillis)
        val viewModel = environment.viewModel(zone = ZoneId.of("UTC"))

        viewModel.selectDayOffset(1)

        assertEquals(emptyList<Any>(), viewModel.uiState.value.dayScoreTips)
    }

    @Test
    fun crossingMidnightRecomputesTipsForTheNewDay() = runTest {
        val environment = environment()
        environment.mealRepository.saveReviewedMeal(mealDraft("Apfel", caloriesKcal = "40"), middayMillis)
        val viewModel = environment.viewModel(zone = ZoneId.of("UTC"))
        val beforeMidnight = viewModel.uiState.value.dayScoreTips
        assertTrue(beforeMidnight.any { it.kind == DayScoreTipKind.CALORIES_UNDER })

        val startOfNextDay = Instant.ofEpochMilli(middayMillis).plusSeconds(12 * 60 * 60 + 1)
        environment.nowMillis = startOfNextDay.toEpochMilli()
        environment.mealRepository.saveReviewedMeal(mealDraft("Torte", caloriesKcal = "8000"), environment.nowMillis)
        viewModel.refreshNow()

        val afterMidnight = viewModel.uiState.value.dayScoreTips
        assertTrue(
            "a new day's tips must reflect that day's own meals, not carry the previous day's kinds",
            afterMidnight.map { it.kind } != beforeMidnight.map { it.kind },
        )
        assertFalse(
            "the far-over-budget new day must not still show the previous day's under-budget tip",
            afterMidnight.any { it.kind == DayScoreTipKind.CALORIES_UNDER },
        )
    }

    // --- Rotation interval -------------------------------------------------------------------

    @Test
    fun theRotationIntervalDefaultsToFiveSeconds() = runTest {
        assertEquals(5, NutritionSettingsStore.DEFAULT_TIP_ROTATION_SECONDS)
        assertEquals(5, environment().viewModel().uiState.value.tipRotationSeconds)
    }

    @Test
    fun anAcceptedRotationIntervalIsPersisted() = runTest {
        val environment = environment()
        val viewModel = environment.viewModel()

        viewModel.saveTipRotationSeconds(" 10 ")

        assertEquals(10, environment.nutritionSettingsStore.getTipRotationSeconds())
        assertEquals(10, viewModel.uiState.value.tipRotationSeconds)
    }

    @Test
    fun zeroIsAcceptedAndMeansNoRotation() = runTest {
        val environment = environment()
        val viewModel = environment.viewModel()

        viewModel.saveTipRotationSeconds("0")

        assertEquals(0, environment.nutritionSettingsStore.getTipRotationSeconds())
    }

    @Test
    fun anOutOfRangeOrNonNumericIntervalIsRejectedWithAMessage() = runTest {
        listOf("90", "1", "abc").forEach { input ->
            val environment = environment()
            val viewModel = environment.viewModel()

            viewModel.saveTipRotationSeconds(input)

            assertEquals("'$input' must not be stored", 5, environment.nutritionSettingsStore.getTipRotationSeconds())
            assertNotNull("'$input' must explain itself", viewModel.uiState.value.settingsMessage)
        }
    }

    @Test
    fun anAcceptedIntervalClearsTheMessageSlotBeforeReporting() = runTest {
        val viewModel = environment().viewModel()

        viewModel.saveTipRotationSeconds("30")

        assertEquals("Saved tip rotation interval.", viewModel.uiState.value.settingsMessage)
        assertNull(viewModel.uiState.value.error)
    }

    // --- Optional rewording ------------------------------------------------------------------

    /** A today-with-meals state holder whose rule tips are ready to be reworded. */
    private suspend fun rewordable(environment: FakeCaptureEnvironment): MealCaptureViewModel {
        environment.mealRepository.saveReviewedMeal(mealDraft("Apfel"), middayMillis)
        return environment.viewModel(initialRuntimeApiKey = "sk-test", zone = ZoneId.of("UTC"))
    }

    @Test
    fun aValidReplyReplacesTheTipTextButNotTheRanking() = runTest {
        val environment = environment()
        val viewModel = rewordable(environment)
        val ruleTips = viewModel.uiState.value.dayScoreTips
        environment.rewordingAgent.reply = ruleTips.map { "Reworded advice number ${it.kind.ordinal} for today" }

        viewModel.rewordDayScoreTips()

        val tips = viewModel.uiState.value.dayScoreTips
        assertEquals(ruleTips.map { it.kind }, tips.map { it.kind })
        assertTrue("every tip must carry the reworded text", tips.all { it.text.startsWith("Reworded advice") })
        assertFalse(viewModel.uiState.value.tipsRewordingInFlight)
    }

    @Test
    fun aShortReplyIsDiscardedWholesale() = runTest {
        val environment = environment()
        val viewModel = rewordable(environment)
        val ruleTips = viewModel.uiState.value.dayScoreTips
        environment.rewordingAgent.reply = ruleTips.drop(1).map { "Reworded advice for the rest of today" }

        viewModel.rewordDayScoreTips()

        assertEquals(ruleTips, viewModel.uiState.value.dayScoreTips)
    }

    @Test
    fun oneOverLongEntryDiscardsTheWholeReply() = runTest {
        val environment = environment()
        val viewModel = rewordable(environment)
        val ruleTips = viewModel.uiState.value.dayScoreTips
        environment.rewordingAgent.reply = ruleTips.mapIndexed { position, _ ->
            if (position == 0) {
                "This particular reply entry runs to a full twelve separate words here"
            } else {
                "Reworded advice for the rest of today"
            }
        }

        viewModel.rewordDayScoreTips()

        assertEquals(ruleTips, viewModel.uiState.value.dayScoreTips)
    }

    @Test
    fun aFailedRewordingLeavesTheRuleTipsAndRaisesNoWarning() = runTest {
        val environment = environment()
        val viewModel = rewordable(environment)
        val ruleTips = viewModel.uiState.value.dayScoreTips
        environment.rewordingAgent.failWith = IllegalStateException("no network")

        viewModel.rewordDayScoreTips()

        val state = viewModel.uiState.value
        assertEquals(ruleTips, state.dayScoreTips)
        assertNull(state.settingsMessage)
        assertNull(state.error)
        assertNull(state.groundingWarning)
        assertFalse(state.tipsRewordingInFlight)
    }

    @Test
    fun theRefreshAffordanceNeedsBothTipsAndAKey() = runTest {
        val environment = environment()
        assertTrue("tips plus a key enables it", rewordable(environment).uiState.value.canRewordDayScoreTips)

        val keyless = environment()
        keyless.mealRepository.saveReviewedMeal(mealDraft("Apfel"), middayMillis)
        assertFalse(keyless.viewModel(zone = ZoneId.of("UTC")).uiState.value.canRewordDayScoreTips)
        assertFalse(environment().viewModel(initialRuntimeApiKey = "sk-test").uiState.value.canRewordDayScoreTips)
    }

    private fun mealDraft(query: String, caloriesKcal: String = "40"): EditableMealDraft = EditableMealDraft(
        title = "",
        query = query,
        items = listOf(
            EditableFoodItem(
                name = query,
                quantity = query,
                amountGml = "100",
                caloriesKcal = caloriesKcal,
                proteinG = "",
                carbsG = "",
                fatG = "",
                saturatedFatG = "",
                sugarG = "",
                saltG = "",
                source = "",
                reasoning = "",
            ),
        ),
        caloriesKcal = "",
        amountGml = "",
        proteinG = "",
        carbsG = "",
        fatG = "",
        saturatedFatG = "",
        sugarG = "",
        saltG = "",
        assumptionsText = "",
        warningsText = "",
        confidence = ConfidenceLevel.MEDIUM,
        needsHumanReview = false,
        category = MealCategory.MEAL,
    )
}
