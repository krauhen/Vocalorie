package com.example.vocalorie.data.repository

import com.example.vocalorie.data.toEntity
import com.example.vocalorie.data.toStableNormalizedMealKey
import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.EditableFoodItem
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.MealCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MealRepositoryTest {

    @Test
    fun aReviewedSaveWritesTheHistoryRowAndBothCachesInOneTransaction() = runTest {
        val data = FakeVocalorieData()
        val repository = data.mealRepository()

        val id = repository.saveReviewedMeal(draft(query = "Buttermilch 200g"), createdAtEpochMillis = 1_000L)

        assertEquals(1L, id)
        assertEquals(1, data.mealDao.rows.size)
        assertEquals(1, data.cacheDao.meals.size)
        assertEquals(1, data.cacheDao.items.size)
        assertEquals("One transaction, not three writes", 1, data.transactionCount)
    }

    @Test
    fun aSaveThatFailsPartWayLeavesNeitherTheMealRowNorItsCacheRows() = runTest {
        val data = FakeVocalorieData()
        data.cacheDao.failItemUpsertWith = IllegalStateException("disk full")
        val repository = data.mealRepository()

        val failure = runCatching {
            repository.saveReviewedMeal(draft(query = "Buttermilch 200g"), createdAtEpochMillis = 1_000L)
        }.exceptionOrNull()

        assertTrue("the failure must surface", failure is IllegalStateException)
        assertEquals("no meal row", emptyList<Any>(), data.mealDao.rows.toList())
        assertEquals("no whole-meal cache row", emptyMap<String, Any>(), data.cacheDao.meals.toMap())
        assertEquals("no item cache rows", emptyMap<String, Any>(), data.cacheDao.items.toMap())
    }

    @Test
    fun aSavedMealWithoutAUsableCacheKeyStillPersistsTheHistoryRow() = runTest {
        val data = FakeVocalorieData()
        val repository = data.mealRepository()

        // Only amount tokens: no normalized key, so nothing may be cached under a blank key.
        repository.saveReviewedMeal(draft(query = "200g"), createdAtEpochMillis = 1_000L)

        assertEquals(1, data.mealDao.rows.size)
        assertEquals(emptyMap<String, Any>(), data.cacheDao.meals.toMap())
    }

    @Test
    fun mealsAreMappedToDomainModelsNewestFirst() = runTest {
        val data = FakeVocalorieData()
        val repository = data.mealRepository()
        repository.saveReviewedMeal(draft(query = "Apfel"), createdAtEpochMillis = 1_000L)
        repository.saveReviewedMeal(draft(query = "Banane"), createdAtEpochMillis = 2_000L)

        val meals = repository.meals()

        assertEquals(listOf("Banane", "Apfel"), meals.map { it.query })
        assertEquals(2_000L, meals.first().createdAtEpochMillis)
        assertEquals(1, meals.first().items.size)
    }

    @Test
    fun summariesCarryThePersistedTotalsWithoutDecodingItemJson() = runTest {
        val data = FakeVocalorieData()
        val repository = data.mealRepository()
        repository.saveReviewedMeal(
            draft(query = "Buttermilch 200g", caloriesKcal = 96.0, amountGml = 200.0, category = MealCategory.DRINK),
            createdAtEpochMillis = 1_000L,
        )

        val summary = repository.mealSummaries().single()

        assertEquals(96.0, summary.caloriesKcal!!, 1e-9)
        assertEquals(200.0, summary.amountGml!!, 1e-9)
        assertEquals("DRINK", summary.category)
    }

    @Test
    fun observedMealsStartFromTheCurrentStateAndReEmitOnEachCommittedWrite() = runTest {
        val data = FakeVocalorieData()
        val repository = data.mealRepository()
        val emissions = mutableListOf<Int>()

        val collector = launch(Dispatchers.Unconfined) {
            repository.observeMeals().take(2).collect { emissions += it.size }
        }
        data.mealDao.insert(entity(createdAtEpochMillis = 1_000L))
        data.tableChanges.signalWrite()
        collector.join()

        assertEquals(listOf(0, 1), emissions)
        assertEquals(listOf(listOf("meals")), data.tableChanges.observedTables)
    }

    @Test
    fun observedSummariesFollowTheMealsTableToo() = runTest {
        val data = FakeVocalorieData()
        data.mealDao.insert(entity(createdAtEpochMillis = 1_000L))
        val repository = data.mealRepository()

        val summaries = repository.observeMealSummaries().first()

        assertEquals(1, summaries.size)
        assertEquals(listOf(listOf("meals")), data.tableChanges.observedTables)
    }

    @Test
    fun updatingAReviewedMealKeepsItsIdAndRefreshesItsCachesInOneTransaction() = runTest {
        val data = FakeVocalorieData()
        val repository = data.mealRepository()
        val id = repository.saveReviewedMeal(draft(query = "Apfel"), createdAtEpochMillis = 1_000L)
        val transactionsBefore = data.transactionCount

        val updated = repository.updateReviewedMeal(
            id = id,
            draft = draft(query = "Apfel", caloriesKcal = 77.0),
            createdAtEpochMillis = 1_500L,
        )

        assertEquals(1, updated)
        assertEquals(id, data.mealDao.rows.single().id)
        assertEquals(1_500L, data.mealDao.rows.single().createdAtEpochMillis)
        assertEquals(77.0, data.mealDao.rows.single().caloriesKcal!!, 1e-9)
        // An edit is a reviewed save: the corrected values are what a later cache hit must reuse.
        assertEquals("One transaction, not three writes", transactionsBefore + 1, data.transactionCount)
        assertEquals(1, data.cacheDao.meals.size)
        assertTrue(
            "the cache row must carry the edited calories",
            data.cacheDao.meals.values.single().itemsJson.contains("77"),
        )
    }

    @Test
    fun anUpdateThatFailsPartWayLeavesTheMealRowUnchanged() = runTest {
        val data = FakeVocalorieData()
        val repository = data.mealRepository()
        val id = repository.saveReviewedMeal(draft(query = "Apfel"), createdAtEpochMillis = 1_000L)
        data.cacheDao.failItemUpsertWith = IllegalStateException("disk full")

        val failure = runCatching {
            repository.updateReviewedMeal(
                id = id,
                draft = draft(query = "Apfel", caloriesKcal = 77.0),
                createdAtEpochMillis = 1_500L,
            )
        }.exceptionOrNull()

        assertTrue("the failure must surface", failure is IllegalStateException)
        assertEquals(1_000L, data.mealDao.rows.single().createdAtEpochMillis)
        assertEquals(40.0, data.mealDao.rows.single().caloriesKcal!!, 1e-9)
    }

    @Test
    fun deletingAMealRemovesOnlyThatRow() = runTest {
        val data = FakeVocalorieData()
        val repository = data.mealRepository()
        val keptId = repository.saveReviewedMeal(draft(query = "Apfel"), createdAtEpochMillis = 1_000L)
        val doomedId = repository.saveReviewedMeal(draft(query = "Banane"), createdAtEpochMillis = 2_000L)

        assertEquals(1, repository.deleteMeal(doomedId))

        assertEquals(listOf(keptId), data.mealDao.rows.map { it.id })
    }

    @Test
    fun everyReadAndWriteRunsOnTheInjectedDispatcher() = runTest {
        val data = FakeVocalorieData()
        val dispatcher = CountingDispatcher()
        val repository = data.mealRepository(dispatcher = dispatcher)

        repository.saveReviewedMeal(draft(query = "Apfel"), createdAtEpochMillis = 1_000L)
        val afterWrite = dispatcher.dispatches
        repository.meals()
        repository.mealSummaries()

        assertTrue("the save must dispatch", afterWrite > 0)
        assertTrue("the reads must dispatch too", dispatcher.dispatches > afterWrite)
    }

    @Test
    fun theCachedMealIsStoredUnderTheNormalizedQueryKey() = runTest {
        val data = FakeVocalorieData()

        data.mealRepository().saveReviewedMeal(draft(query = "Banane und Apfel"), createdAtEpochMillis = 1_000L)

        assertNotNull(data.cacheDao.meals["Banane und Apfel".toStableNormalizedMealKey()])
    }

    private fun FakeVocalorieData.mealRepository(
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = MealRepository(
        mealDao = mealDao,
        cacheDao = cacheDao,
        transactions = transactions,
        tableChanges = tableChanges,
        dispatcher = dispatcher,
    )

    private fun entity(createdAtEpochMillis: Long) =
        draft(query = "Apfel").toEntity(createdAtEpochMillis = createdAtEpochMillis)

    private fun draft(
        query: String,
        caloriesKcal: Double = 40.0,
        amountGml: Double = 100.0,
        category: MealCategory = MealCategory.MEAL,
    ): EditableMealDraft = EditableMealDraft(
        title = "",
        query = query,
        items = listOf(
            EditableFoodItem(
                name = query,
                quantity = query,
                amountGml = amountGml.toString(),
                caloriesKcal = caloriesKcal.toString(),
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
        category = category,
    )
}
