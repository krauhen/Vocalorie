package com.example.vocalorie.data.repository

import com.example.vocalorie.data.CacheDao
import com.example.vocalorie.data.MealDao
import com.example.vocalorie.data.MealSummary
import com.example.vocalorie.data.toCachedItemEntities
import com.example.vocalorie.data.toCachedMealEntity
import com.example.vocalorie.data.toEntity
import com.example.vocalorie.data.toSavedMeal
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.SavedMeal
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Meal history: reads observed from the database, writes dispatched off the caller's thread.
 *
 * Callers never wrap a call in a dispatcher switch — every method here does its own, including the
 * entity-to-domain mapping and the item-JSON decode that mapping performs.
 *
 * It holds [CacheDao] because a reviewed save is the one operation that writes the history row and
 * both reuse caches, and the spec requires that to be atomic. Cache *reads* live in
 * [MealCacheRepository].
 */
class MealRepository(
    private val mealDao: MealDao,
    private val cacheDao: CacheDao,
    private val transactions: TransactionRunner,
    private val tableChanges: TableChangeSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Every saved meal, re-emitted whenever the meals table changes. Newest first. */
    fun observeMeals(): Flow<List<SavedMeal>> = tableChanges.changes(VocalorieTables.MEALS)
        .map { withContext(dispatcher) { mealDao.getAll().map { entity -> entity.toSavedMeal() } } }

    /**
     * Totals-only rows, re-emitted whenever the meals table changes. Reads the persisted total
     * columns, so no per-item JSON is decoded on this path.
     */
    fun observeMealSummaries(): Flow<List<MealSummary>> = tableChanges.changes(VocalorieTables.MEALS)
        .map { withContext(dispatcher) { mealDao.getAllSummaries() } }

    suspend fun meals(): List<SavedMeal> =
        withContext(dispatcher) { mealDao.getAll().map { it.toSavedMeal() } }

    suspend fun mealSummaries(): List<MealSummary> = withContext(dispatcher) { mealDao.getAllSummaries() }

    /**
     * Persist a reviewed meal as one atomic unit: the history row, the whole-meal cache row and the
     * item-name cache rows either all commit or none does. Returns the new meal's row id.
     *
     * Reviewed-save is the only writer of either cache; both upsert last-saved-wins per key.
     */
    suspend fun saveReviewedMeal(draft: EditableMealDraft, createdAtEpochMillis: Long): Long =
        withContext(dispatcher) {
            transactions.inTransaction {
                val mealId = mealDao.insert(draft.toEntity(createdAtEpochMillis = createdAtEpochMillis))
                draft.toCachedMealEntity()?.let { cacheDao.upsertMeal(it) }
                val cachedItems = draft.toCachedItemEntities()
                if (cachedItems.isNotEmpty()) cacheDao.upsertItems(cachedItems)
                mealId
            }
        }

    /**
     * Update an existing reviewed meal in place, keeping its id and its (possibly edited) timestamp,
     * and refresh both reuse caches from it — as one atomic unit, for the same reason
     * [saveReviewedMeal] is atomic.
     *
     * Editing a saved meal is a reviewed save too: the corrected values are exactly the ones a later
     * cache hit should reuse, so leaving the caches on the pre-edit values would serve them back.
     */
    suspend fun updateReviewedMeal(id: Long, draft: EditableMealDraft, createdAtEpochMillis: Long): Int =
        withContext(dispatcher) {
            transactions.inTransaction {
                val updated = mealDao.update(draft.toEntity(id = id, createdAtEpochMillis = createdAtEpochMillis))
                draft.toCachedMealEntity()?.let { cacheDao.upsertMeal(it) }
                val cachedItems = draft.toCachedItemEntities()
                if (cachedItems.isNotEmpty()) cacheDao.upsertItems(cachedItems)
                updated
            }
        }

    suspend fun deleteMeal(id: Long): Int = withContext(dispatcher) { mealDao.deleteById(id) }
}
