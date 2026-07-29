package com.example.vocalorie.data.repository

import com.example.vocalorie.data.CachedItemEntity
import com.example.vocalorie.data.toCachedItemEntities
import com.example.vocalorie.data.toCachedMealEntity
import com.example.vocalorie.data.toStableNormalizedMealKey
import com.example.vocalorie.model.ConfidenceLevel
import com.example.vocalorie.model.EditableFoodItem
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.MealCategory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cache repository must reproduce the exact matching rules `MealCacheTest` pins, but by asking
 * for keys instead of reading the tables. So these tests assert both the result and that only the
 * needed keys were requested.
 */
class MealCacheRepositoryTest {

    @Test
    fun anIdenticalQueryIsAPointLookupThatHits() = runTest {
        val data = FakeVocalorieData()
        data.cache(draft(query = "Buttermilch 200g"))
        val repository = data.cacheRepository()

        val match = repository.findCachedMeal("Buttermilch 200g")

        assertNotNull(match)
        assertEquals(listOf("Buttermilch 200g".toStableNormalizedMealKey()), data.cacheDao.requestedMealKeys)
    }

    @Test
    fun wordOrderStillDoesNotAffectTheMatch() = runTest {
        val data = FakeVocalorieData()
        data.cache(draft(query = "Apfel und Banane"))

        assertNotNull(data.cacheRepository().findCachedMeal("Banane und Apfel"))
    }

    @Test
    fun aSubsetQueryStillDoesNotMatch() = runTest {
        val data = FakeVocalorieData()
        data.cache(draft(query = "Buttermilch mit Honig"))

        assertNull(data.cacheRepository().findCachedMeal("Buttermilch"))
    }

    @Test
    fun aQueryWithNoUsableKeyIsNotEvenLookedUp() = runTest {
        val data = FakeVocalorieData()
        data.cache(draft(query = "Buttermilch 200g"))

        assertNull(data.cacheRepository().findCachedMeal("200g"))
        assertEquals("a blank key must never reach the database", emptyList<String>(), data.cacheDao.requestedMealKeys)
    }

    @Test
    fun aCacheHitKeepsItsCategoryAndScalesToTheRequestedAmount() = runTest {
        val data = FakeVocalorieData()
        data.cache(draft(query = "Buttermilch 100g", amountGml = 100.0, caloriesKcal = 40.0, category = MealCategory.DRINK))

        val match = data.cacheRepository().findCachedMeal("Buttermilch 200g")!!

        assertEquals(MealCategory.DRINK, match.meal.category)
        assertEquals("80", match.draft.caloriesKcal)
        assertEquals("200", match.draft.amountGml)
    }

    @Test
    fun aLookupNeverReadsTheWholeTable() = runTest {
        val data = FakeVocalorieData()
        // Distinct keys: a bare number is stripped from a normalized key, so the suffix is a word.
        repeat(50) { index -> data.cache(draft(query = "Meal alpha$index")) }
        val repository = data.cacheRepository()

        assertEquals(50, data.cacheDao.meals.size)
        assertNotNull(repository.findCachedMeal("Meal alpha7"))

        assertEquals(1, data.cacheDao.requestedMealKeys.size)
    }

    @Test
    fun itemResolutionAsksOnlyForTheNamesTheDraftContains() = runTest {
        val data = FakeVocalorieData()
        data.cacheDao.items["buttermilch"] = cachedItem("buttermilch", "Buttermilch", caloriesKcalPer100 = 40.0)
        data.cacheDao.items["honig"] = cachedItem("honig", "Honig", caloriesKcalPer100 = 300.0)
        data.cacheDao.items["nichtgefragt"] = cachedItem("nichtgefragt", "Nicht gefragt")

        val resolved = data.cacheRepository().resolveItemsFromCache(
            draft(query = "Buttermilch", itemName = "Buttermilch", amountGml = 200.0, caloriesKcal = 0.0),
        )

        assertEquals(listOf(listOf("buttermilch")), data.cacheDao.requestedItemKeys)
        assertEquals("80", resolved.items.single().caloriesKcal)
        assertEquals("80", resolved.caloriesKcal)
    }

    @Test
    fun aDraftWithNoUsableItemNamesSkipsTheLookupEntirely() = runTest {
        val data = FakeVocalorieData()
        val original = draft(query = "Buttermilch", itemName = "123")

        val resolved = data.cacheRepository().resolveItemsFromCache(original)

        assertEquals(original, resolved)
        assertEquals(emptyList<List<String>>(), data.cacheDao.requestedItemKeys)
    }

    @Test
    fun aDraftWhoseItemsAreNotCachedComesBackUnchanged() = runTest {
        val data = FakeVocalorieData()
        val original = draft(query = "Buttermilch", itemName = "Buttermilch")

        val resolved = data.cacheRepository().resolveItemsFromCache(original)

        assertEquals(original, resolved)
        assertEquals(listOf(listOf("buttermilch")), data.cacheDao.requestedItemKeys)
    }

    @Test
    fun bothLookupsRunOnTheInjectedDispatcher() = runTest {
        val data = FakeVocalorieData()
        val dispatcher = CountingDispatcher()
        val repository = data.cacheRepository(dispatcher = dispatcher)

        repository.findCachedMeal("Buttermilch")
        val afterMealLookup = dispatcher.dispatches
        repository.resolveItemsFromCache(draft(query = "Buttermilch", itemName = "Buttermilch"))

        assertTrue("the meal lookup must dispatch", afterMealLookup > 0)
        assertTrue("the item lookup must dispatch too", dispatcher.dispatches > afterMealLookup)
    }

    private fun FakeVocalorieData.cacheRepository(
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = MealCacheRepository(cacheDao = cacheDao, dispatcher = dispatcher)

    /** Write a reviewed meal into the fake caches the way a save would. */
    private fun FakeVocalorieData.cache(mealDraft: EditableMealDraft) {
        mealDraft.toCachedMealEntity()?.let { cacheDao.upsertMeal(it) }
        mealDraft.toCachedItemEntities().takeIf { it.isNotEmpty() }?.let { cacheDao.upsertItems(it) }
    }

    private fun cachedItem(
        normalizedName: String,
        displayName: String,
        caloriesKcalPer100: Double? = null,
    ) = CachedItemEntity(
        normalizedName = normalizedName,
        displayName = displayName,
        caloriesKcalPer100 = caloriesKcalPer100,
        proteinGPer100 = null,
        carbsGPer100 = null,
        fatGPer100 = null,
        saturatedFatGPer100 = null,
        sugarGPer100 = null,
        saltGPer100 = null,
        source = "",
        reasoning = "",
    )

    private fun draft(
        query: String,
        itemName: String = query,
        amountGml: Double = 100.0,
        caloriesKcal: Double = 40.0,
        category: MealCategory = MealCategory.MEAL,
    ): EditableMealDraft = EditableMealDraft(
        title = "",
        query = query,
        items = listOf(
            EditableFoodItem(
                name = itemName,
                quantity = query,
                amountGml = amountGml.toInt().toString(),
                caloriesKcal = caloriesKcal.toInt().toString(),
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
