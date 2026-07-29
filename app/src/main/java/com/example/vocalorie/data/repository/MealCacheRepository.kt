package com.example.vocalorie.data.repository

import com.example.vocalorie.data.CacheDao
import com.example.vocalorie.data.CachedMealMatch
import com.example.vocalorie.data.cachedItemNameKeys
import com.example.vocalorie.data.toCachedMealMatch
import com.example.vocalorie.data.toStableNormalizedMealKey
import com.example.vocalorie.data.withItemsResolvedFromCache
import com.example.vocalorie.model.EditableMealDraft
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads the dedicated reuse caches by key, so lookup cost does not grow with cache size and no
 * caller has to hold either cache table in memory.
 *
 * Writes are deliberately absent: a reviewed save is the only writer of either cache and it has to
 * commit together with the history row, so that write lives in [MealRepository.saveReviewedMeal].
 */
class MealCacheRepository(
    private val cacheDao: CacheDao,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /**
     * The cached whole meal reusable for [requestQuery], prepared and scaled to the requested amount,
     * or `null` when the query has no usable normalized key or nothing is cached under it.
     *
     * A primary-key point lookup: matching stays exact normalized-key equality, unchanged from the
     * previous in-memory scan.
     */
    suspend fun findCachedMeal(requestQuery: String): CachedMealMatch? = withContext(dispatcher) {
        val normalizedRequestKey = requestQuery.toStableNormalizedMealKey()
        if (normalizedRequestKey.isBlank()) return@withContext null
        cacheDao.findMeal(normalizedRequestKey)?.toCachedMealMatch(requestQuery)
    }

    /**
     * Replace each of [draft]'s items with its cached per-100 nutrition scaled to that item's amount,
     * querying only the item names this draft actually contains. Items with no cache match or no
     * positive amount are left untouched.
     */
    suspend fun resolveItemsFromCache(draft: EditableMealDraft): EditableMealDraft = withContext(dispatcher) {
        val nameKeys = draft.cachedItemNameKeys()
        if (nameKeys.isEmpty()) return@withContext draft
        draft.withItemsResolvedFromCache(cacheDao.findItems(nameKeys))
    }
}
