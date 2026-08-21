package com.example.vocalorie.ui.capture

import com.example.vocalorie.ai.EstimationProgress
import com.example.vocalorie.ai.NutritionAgentException
import com.example.vocalorie.ai.NutritionEstimateOutcome
import com.example.vocalorie.model.ActivityType
import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.EditableActivityDraft
import com.example.vocalorie.model.EditableFoodItem
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.MealCategory
import com.example.vocalorie.ui.entries.EntriesTab
import kotlinx.coroutines.CompletableDeferred
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
import java.time.Duration
import java.time.Instant

/**
 * The capture flow's state holder, against in-memory doubles.
 *
 * None of this was testable before: the same logic lived inside a composable, closing over an
 * Android `Context` and thirty-five mutable slots. The cases that matter most here are the ones that
 * cost real money or real data — a warm cache must never turn into a billable estimate, a reviewed
 * save must be one atomic write, and a second tap must not duplicate a request in flight.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MealCaptureViewModelTest {

    @Before
    fun setUp() {
        // `viewModelScope` dispatches on the main dispatcher, which does not exist on the JVM.
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Cache before model ------------------------------------------------------------------

    @Test
    fun aWarmCacheIsServedForApprovalWithoutCallingTheModel() = runTest {
        val environment = FakeCaptureEnvironment()
        environment.mealRepository.saveReviewedMeal(mealDraft(query = "Buttermilch 200g"), 1_000L)
        val viewModel = environment.viewModel(initialRuntimeApiKey = "sk-test")

        viewModel.onQueryChange("Buttermilch 200g")
        viewModel.onEstimate()

        assertNotNull("the cached meal must be offered", viewModel.uiState.value.approvalMatch)
        assertEquals("nothing may be sent to the model", emptyList<String>(), environment.estimator.requests)
        assertEquals("Estimate instead", viewModel.uiState.value.approvalRejectLabel)
    }

    @Test
    fun approvingTheCachedDraftAdoptsItAndStillNeverEstimates() = runTest {
        val environment = FakeCaptureEnvironment()
        environment.mealRepository.saveReviewedMeal(mealDraft(query = "Buttermilch 200g"), 1_000L)
        val viewModel = environment.viewModel(initialRuntimeApiKey = "sk-test")
        viewModel.onQueryChange("Buttermilch 200g")
        viewModel.onEstimate()

        viewModel.approveCachedMeal()

        val state = viewModel.uiState.value
        assertNotNull("the cached draft becomes the reviewed draft", state.draft)
        assertEquals("Buttermilch 200g", state.query)
        assertNull(state.approvalMatch)
        assertNull(state.pendingEstimateRequest)
        assertEquals(emptyList<String>(), environment.estimator.requests)
    }

    @Test
    fun rejectingTheCachedDraftRunsTheEstimateThatWasHeldBack() = runTest {
        val environment = FakeCaptureEnvironment()
        environment.mealRepository.saveReviewedMeal(mealDraft(query = "Buttermilch 200g"), 1_000L)
        val viewModel = environment.viewModel(initialRuntimeApiKey = "sk-test")
        viewModel.onQueryChange("Buttermilch 200g")
        viewModel.onEstimate()

        viewModel.rejectCachedMeal()

        assertEquals(listOf("Buttermilch 200g"), environment.estimator.requests)
        assertNull(viewModel.uiState.value.approvalMatch)
        assertNull(viewModel.uiState.value.pendingEstimateRequest)
    }

    @Test
    fun aColdCacheGoesToTheModelAndTheResultBecomesTheReviewedDraft() = runTest {
        val environment = FakeCaptureEnvironment()
        environment.estimator.outcome = NutritionEstimateOutcome(result = nutritionAgentResult(query = "Apfel"))
        val viewModel = environment.viewModel(initialRuntimeApiKey = "sk-test")

        viewModel.onQueryChange("Apfel")
        viewModel.onEstimate()

        val state = viewModel.uiState.value
        assertEquals(listOf("Apfel"), environment.estimator.requests)
        assertEquals("Apfel", state.draft?.query)
        assertFalse("the spinner must be down again", state.isLoading)
        assertNull(state.error)
    }

    @Test
    fun aMealPickedFromHistorySearchIsOfferedWithNothingToEstimate() = runTest {
        val environment = FakeCaptureEnvironment()
        environment.mealRepository.saveReviewedMeal(mealDraft(query = "Apfel"), 1_000L)
        val viewModel = environment.viewModel(initialRuntimeApiKey = "sk-test")
        environment.data.tableChanges.signalWrite()
        val saved = viewModel.uiState.value.savedMeals.single()

        viewModel.onSearchQueryChange("Apf")
        viewModel.onSearchMealClick(saved)

        val state = viewModel.uiState.value
        assertNotNull(state.approvalMatch)
        assertNull("there is no request to fall back to", state.pendingEstimateRequest)
        assertEquals("Close", state.approvalRejectLabel)
        assertEquals("", state.searchQuery)

        viewModel.rejectCachedMeal()
        assertEquals(emptyList<String>(), environment.estimator.requests)
    }

    @Test
    fun estimatingWithNeitherAQueryNorAPhotoReportsItAndSendsNothing() = runTest {
        val environment = FakeCaptureEnvironment()
        val viewModel = environment.viewModel(initialRuntimeApiKey = "sk-test")

        viewModel.onEstimate()

        assertEquals("Enter a nutrition query or attach a photo.", viewModel.uiState.value.error)
        assertEquals(emptyList<String>(), environment.estimator.requests)
    }

    @Test
    fun estimatingWithoutAnyKeyAsksForOneInsteadOfCallingTheModel() = runTest {
        val environment = FakeCaptureEnvironment()
        val viewModel = environment.viewModel(initialRuntimeApiKey = "")

        viewModel.onQueryChange("Apfel")
        viewModel.onEstimate()

        assertEquals(
            "Enter an OpenAI API key or save one in Settings.",
            viewModel.uiState.value.error,
        )
        assertEquals(emptyList<String>(), environment.estimator.requests)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun aSecondEstimateTapWhileOneIsInFlightDoesNotDuplicateTheRequest() = runTest {
        val environment = FakeCaptureEnvironment()
        val gate = CompletableDeferred<Unit>()
        environment.estimator.gate = gate
        val viewModel = environment.viewModel(initialRuntimeApiKey = "sk-test")
        viewModel.onQueryChange("Apfel")

        viewModel.onEstimate()
        viewModel.onEstimate()

        assertEquals("one billable request, not two", listOf("Apfel"), environment.estimator.requests)
        assertTrue(viewModel.uiState.value.isLoading)
        gate.complete(Unit)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun estimationProgressHoldsTheLatestStepWhileInFlightAndClearsAfterSuccess() = runTest {
        val environment = FakeCaptureEnvironment()
        val gate = CompletableDeferred<Unit>()
        environment.estimator.gate = gate
        environment.estimator.progressToEmit = listOf(
            EstimationProgress.Preparing,
            EstimationProgress.SearchingSources,
            EstimationProgress.ReadingSource("https://fddb.info/db/de/lebensmittel/apfel/"),
            EstimationProgress.CalculatingNutrition,
        )
        val viewModel = environment.viewModel(initialRuntimeApiKey = "sk-test")
        viewModel.onQueryChange("Apfel")

        viewModel.onEstimate()

        assertEquals(EstimationProgress.CalculatingNutrition, viewModel.uiState.value.estimationProgress)
        gate.complete(Unit)
        assertNull(viewModel.uiState.value.estimationProgress)
    }

    @Test
    fun estimationProgressClearsButNamesTheLastStepInTheDiagnosticOnFailure() = runTest {
        val environment = FakeCaptureEnvironment()
        val gate = CompletableDeferred<Unit>()
        environment.estimator.gate = gate
        environment.estimator.progressToEmit = listOf(
            EstimationProgress.ReadingSource("https://fddb.info/db/de/lebensmittel/apfel/"),
        )
        environment.estimator.failWith = IllegalStateException("boom")
        val viewModel = environment.viewModel(initialRuntimeApiKey = "sk-test")
        viewModel.onQueryChange("Apfel")

        viewModel.onEstimate()
        gate.complete(Unit)

        val state = viewModel.uiState.value
        assertNull(state.estimationProgress)
        assertTrue(
            "diagnostic should name the last step",
            state.diagnostic?.contains("fddb.info") == true,
        )
    }

    @Test
    fun anEstimateFailureIsReportedWithItsDiagnostic() = runTest {
        val environment = FakeCaptureEnvironment()
        environment.estimator.failWith = NutritionAgentException(
            message = "OpenAI rejected the API key.",
            diagnostic = "401 Unauthorized",
            cause = IllegalStateException("401"),
        )
        val viewModel = environment.viewModel(initialRuntimeApiKey = "sk-test")

        viewModel.onQueryChange("Apfel")
        viewModel.onEstimate()

        val state = viewModel.uiState.value
        assertEquals("OpenAI rejected the API key.", state.error)
        assertEquals("401 Unauthorized", state.diagnostic)
        assertFalse(state.isLoading)
    }

    @Test
    fun aGroundingFailureIsCarriedAsAWarningRatherThanLost() = runTest {
        val environment = FakeCaptureEnvironment()
        environment.estimator.outcome = NutritionEstimateOutcome(
            result = nutritionAgentResult(query = "Apfel"),
            groundingFailureMessage = "Sources could not be checked.",
            groundingFailureDiagnostic = "brave: 429",
        )
        val viewModel = environment.viewModel(initialRuntimeApiKey = "sk-test")

        viewModel.onQueryChange("Apfel")
        viewModel.onEstimate()

        val state = viewModel.uiState.value
        assertEquals("Sources could not be checked.", state.groundingWarning)
        assertEquals("brave: 429", state.diagnostic)
        assertNotNull("the ungrounded estimate is still usable", state.draft)
    }

    // --- Reset --------------------------------------------------------------------------------

    @Test
    fun resetClearsEveryTransientCaptureFieldAndBumpsTheResetSignal() = runTest {
        val environment = FakeCaptureEnvironment()
        environment.estimator.failWith = IllegalStateException("boom")
        val viewModel = environment.viewModel(initialRuntimeApiKey = "sk-test")
        viewModel.onQueryChange("Apfel")
        viewModel.onSearchQueryChange("Apf")
        viewModel.onEstimate()
        val signalBefore = viewModel.uiState.value.resetSignal

        viewModel.resetNewMealEstimate()

        val state = viewModel.uiState.value
        assertEquals("", state.query)
        assertEquals("", state.searchQuery)
        assertNull(state.draft)
        assertNull(state.error)
        assertNull(state.diagnostic)
        assertNull(state.groundingWarning)
        assertNull(state.saveMessage)
        assertEquals(emptyList<Any>(), state.attachedImages)
        assertEquals(signalBefore + 1, state.resetSignal)
    }

    // --- Reviewed save ------------------------------------------------------------------------

    @Test
    fun savingAReviewedMealWritesTheRowAndBothCachesAtomicallyAndOpensIt() = runTest {
        val environment = FakeCaptureEnvironment()
        val viewModel = environment.viewModel(initialRuntimeApiKey = "sk-test")
        viewModel.onQueryChange("Apfel")

        viewModel.saveNewMeal(mealDraft(query = "Apfel"))

        val state = viewModel.uiState.value
        assertEquals(1, environment.data.mealDao.rows.size)
        assertEquals(1, environment.data.cacheDao.meals.size)
        assertEquals(1, environment.data.cacheDao.items.size)
        assertEquals("one transaction", 1, environment.data.transactionCount)
        assertEquals("Apfel", state.selectedMeal?.query)
        assertNotNull(state.selectedDraft)
        assertEquals("Saved meal to local entries.", state.saveMessage)
        assertEquals("", state.query)
        assertEquals(emptyList<Any>(), state.attachedImages)
        assertNull(state.draft)
        assertFalse(state.isSaving)
    }

    @Test
    fun savingABlankDescriptionIsRefusedBeforeAnyWrite() = runTest {
        val environment = FakeCaptureEnvironment()
        val viewModel = environment.viewModel()

        viewModel.saveNewMeal(mealDraft(query = "   "))

        assertEquals("Meal description cannot be blank before saving.", viewModel.uiState.value.error)
        assertEquals(emptyList<Any>(), environment.data.mealDao.rows.toList())
    }

    @Test
    fun aNewMealIsStampedWithTheViewedDayAtTheCurrentTime() = runTest {
        val environment = FakeCaptureEnvironment()
        val viewModel = environment.viewModel()
        viewModel.selectDayOffset(2)

        viewModel.saveNewMeal(mealDraft(query = "Apfel"))

        val expected = Instant.ofEpochMilli(environment.nowMillis).minus(Duration.ofDays(2)).toEpochMilli()
        assertEquals(expected, environment.data.mealDao.rows.single().createdAtEpochMillis)
    }

    @Test
    fun editingASavedMealKeepsItsIdAndRefreshesItsReuseCaches() = runTest {
        val environment = FakeCaptureEnvironment()
        environment.mealRepository.saveReviewedMeal(mealDraft(query = "Apfel", caloriesKcal = "40"), 1_000L)
        environment.data.tableChanges.signalWrite()
        val viewModel = environment.viewModel()
        val saved = viewModel.uiState.value.savedMeals.single()
        viewModel.openMeal(saved)
        var savedCallbackRan = false

        viewModel.saveSelectedMeal(mealDraft(query = "Apfel", caloriesKcal = "77")) { savedCallbackRan = true }

        assertTrue("the editor must be told the save landed", savedCallbackRan)
        assertEquals(saved.id, environment.data.mealDao.rows.single().id)
        assertEquals(77.0, environment.data.mealDao.rows.single().caloriesKcal!!, 1e-9)
        assertTrue(
            "the reuse cache must carry the corrected value",
            environment.data.cacheDao.meals.values.single().itemsJson.contains("77"),
        )
        assertEquals("Updated saved meal.", viewModel.uiState.value.saveMessage)
    }

    @Test
    fun deletingASavedMealClosesTheOverlayAndRemovesTheRow() = runTest {
        val environment = FakeCaptureEnvironment()
        environment.mealRepository.saveReviewedMeal(mealDraft(query = "Apfel"), 1_000L)
        environment.data.tableChanges.signalWrite()
        val viewModel = environment.viewModel()
        viewModel.openMeal(viewModel.uiState.value.savedMeals.single())

        viewModel.deleteSelectedMeal()

        assertEquals(emptyList<Any>(), environment.data.mealDao.rows.toList())
        assertNull(viewModel.uiState.value.selectedMeal)
        assertNull(viewModel.uiState.value.selectedDraft)
        assertEquals("Deleted saved meal.", viewModel.uiState.value.saveMessage)
    }

    // --- Observation instead of manual refresh ------------------------------------------------

    @Test
    fun aCommittedWriteReachesTheStateWithoutAnyManualRefresh() = runTest {
        val environment = FakeCaptureEnvironment()
        val viewModel = environment.viewModel()
        assertEquals(emptyList<Any>(), viewModel.uiState.value.savedMeals)

        environment.mealRepository.saveReviewedMeal(mealDraft(query = "Apfel"), 1_000L)
        environment.data.tableChanges.signalWrite()

        assertEquals(listOf("Apfel"), viewModel.uiState.value.savedMeals.map { it.query })
    }

    // --- Activities ---------------------------------------------------------------------------

    @Test
    fun aStepsActivityIsPersistedWithCaloriesDerivedFromItsStepCount() = runTest {
        val environment = FakeCaptureEnvironment()
        environment.nutritionSettingsStore.saveKcalPerStep(0.03)
        val viewModel = environment.viewModel()
        viewModel.openActivityEditor(null)

        viewModel.saveActivity(
            activityDraft(type = ActivityType.STEPS, steps = "8000", caloriesBurnedKcal = "999"),
        ) {}

        val row = environment.data.activityDao.rows.single()
        assertEquals("derived from steps, not from the typed calories", 240.0, row.caloriesBurnedKcal, 1e-9)
        assertEquals(0, row.durationMinutes)
        assertEquals(8000, row.stepsCount)
        assertFalse("a new activity closes its editor", viewModel.uiState.value.showActivityOverlay)
    }

    @Test
    fun anActivityWithNoTypeIsReportedWithoutWriting() = runTest {
        val environment = FakeCaptureEnvironment()
        val viewModel = environment.viewModel()

        viewModel.saveActivity(activityDraft(type = null)) {}

        assertEquals("Choose an activity type before saving.", viewModel.uiState.value.activityError)
        assertEquals(emptyList<Any>(), environment.data.activityDao.rows.toList())
    }

    @Test
    fun editingAnActivityKeepsTheEditorOpenOnItsUpdatedValues() = runTest {
        val environment = FakeCaptureEnvironment()
        val viewModel = environment.viewModel()
        viewModel.openActivityEditor(null)
        viewModel.saveActivity(activityDraft(title = "Walk")) {}
        environment.data.tableChanges.signalWrite()
        val saved = viewModel.uiState.value.savedActivities.single()
        viewModel.openActivityEditor(saved)

        viewModel.saveActivity(activityDraft(title = "Long walk")) {}

        assertEquals("Long walk", environment.data.activityDao.rows.single().title)
        assertEquals("Long walk", viewModel.uiState.value.selectedActivity?.title)
        assertEquals("Updated activity.", viewModel.uiState.value.activityMessage)
        assertTrue("an edit keeps its editor open", viewModel.uiState.value.showActivityOverlay)
    }

    // --- Clock and routing --------------------------------------------------------------------

    @Test
    fun refreshNowAdvancesTheClockAndNothingElse() = runTest {
        val environment = FakeCaptureEnvironment()
        val viewModel = environment.viewModel()
        val before = viewModel.uiState.value
        environment.nowMillis += 60_000L

        viewModel.refreshNow()

        val after = viewModel.uiState.value
        assertEquals(Instant.ofEpochMilli(environment.nowMillis), after.now)
        assertEquals("only the clock may move", before, after.copy(now = before.now))
    }

    @Test
    fun backClosesOneThingAtATimeOutermostFirst() = runTest {
        val environment = FakeCaptureEnvironment()
        environment.mealRepository.saveReviewedMeal(mealDraft(query = "Apfel"), 1_000L)
        environment.data.tableChanges.signalWrite()
        val viewModel = environment.viewModel()
        viewModel.openMeal(viewModel.uiState.value.savedMeals.single())
        viewModel.openActivityEditor(null)
        viewModel.openSettings()

        assertTrue(viewModel.uiState.value.canHandleBack)

        viewModel.onBack()
        assertFalse("settings closes first", viewModel.uiState.value.showSettings)
        viewModel.onBack()
        assertFalse("then the activity editor", viewModel.uiState.value.showActivityOverlay)
        viewModel.onBack()
        assertNull("then the meal overlay", viewModel.uiState.value.selectedMeal)
        assertFalse(viewModel.uiState.value.canHandleBack)
    }

    @Test
    fun theActivePaletteFollowsTheSelectedTab() = runTest {
        val environment = FakeCaptureEnvironment()
        val viewModel = environment.viewModel()

        assertEquals(viewModel.uiState.value.mealThemeColors, viewModel.uiState.value.activeThemeColors)

        viewModel.selectTab(EntriesTab.ACTIVITIES)

        assertEquals(viewModel.uiState.value.activityThemeColors, viewModel.uiState.value.activeThemeColors)
    }

    // --- Settings -----------------------------------------------------------------------------

    @Test
    fun anImpossibleMacroSplitIsRejectedWithItsMessage() = runTest {
        val environment = FakeCaptureEnvironment()
        val viewModel = environment.viewModel()

        viewModel.saveNutritionGoals(calorieGoalInput = "2000", proteinInput = "70", carbsInput = "60")

        assertEquals(
            "Protein and carbs percentages must be whole numbers that leave a non-negative fat share.",
            viewModel.uiState.value.settingsMessage,
        )
        assertEquals(2400, viewModel.uiState.value.nutritionGoals.calorieGoalKcal)
    }

    @Test
    fun savedNutritionGoalsAreReadBackIntoTheState() = runTest {
        val environment = FakeCaptureEnvironment()
        val viewModel = environment.viewModel()

        viewModel.saveNutritionGoals(calorieGoalInput = "1900", proteinInput = "30", carbsInput = "40")

        assertEquals("Saved nutrition goals.", viewModel.uiState.value.settingsMessage)
        assertEquals(1900, viewModel.uiState.value.nutritionGoals.calorieGoalKcal)
        assertEquals(30, viewModel.uiState.value.nutritionGoals.fatPercent)
    }

    @Test
    fun theStepBurnSettingRoundTripsThroughTheStateAsEntered() = runTest {
        val environment = FakeCaptureEnvironment()
        val viewModel = environment.viewModel()

        viewModel.saveKcalPerStep("30")

        assertEquals("Saved calories burned per 1,000 steps.", viewModel.uiState.value.settingsMessage)
        assertEquals(0.03, viewModel.uiState.value.kcalPerStep, 0.0)
    }

    // --- Backup -------------------------------------------------------------------------------

    @Test
    fun exportingABackupStampsItWithTheInjectedClock() = runTest {
        val environment = FakeCaptureEnvironment()
        val viewModel = environment.viewModel()

        viewModel.exportBackup("content://documents/backup.json")

        assertEquals(
            listOf("content://documents/backup.json" to environment.nowMillis),
            environment.backups.exported,
        )
        assertEquals("Exported your data backup.", viewModel.uiState.value.settingsMessage)
    }

    @Test
    fun anImportReportsWhatItMergedAndWhatItSkipped() = runTest {
        val environment = FakeCaptureEnvironment()
        val viewModel = environment.viewModel()

        viewModel.importBackup("content://documents/backup.json")

        assertEquals(listOf("content://documents/backup.json"), environment.backups.imported)
        assertEquals("Imported 3, skipped 1 already present.", viewModel.uiState.value.settingsMessage)
    }

    @Test
    fun aFailedImportReportsItsReasonRatherThanClaimingSuccess() = runTest {
        val environment = FakeCaptureEnvironment()
        environment.backups.importFailure = IllegalStateException("This file is not a Vocalorie backup.")
        val viewModel = environment.viewModel()

        viewModel.importBackup("content://documents/not-a-backup.json")

        assertEquals("This file is not a Vocalorie backup.", viewModel.uiState.value.settingsMessage)
    }

    // --- Helpers ------------------------------------------------------------------------------

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

    private fun activityDraft(
        title: String = "Walk",
        type: ActivityType? = ActivityType.RUNNING,
        steps: String = "",
        caloriesBurnedKcal: String = "250",
        durationMinutes: String = "30",
    ): EditableActivityDraft = EditableActivityDraft(
        type = type,
        title = title,
        description = "",
        caloriesBurnedKcal = caloriesBurnedKcal,
        durationMinutes = durationMinutes,
        steps = steps,
        createdAtEpochMillis = 1_000L,
    )
}
