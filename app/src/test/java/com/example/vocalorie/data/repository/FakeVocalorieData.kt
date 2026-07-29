package com.example.vocalorie.data.repository

import com.example.vocalorie.data.ActivityDao
import com.example.vocalorie.data.ActivityEntity
import com.example.vocalorie.data.CacheDao
import com.example.vocalorie.data.CachedItemEntity
import com.example.vocalorie.data.CachedMealEntity
import com.example.vocalorie.data.MealDao
import com.example.vocalorie.data.MealEntity
import com.example.vocalorie.data.MealSummary
import com.example.vocalorie.data.toMealSummary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

/**
 * In-memory doubles for the three DAO interfaces plus the two database seams.
 *
 * An abstract Room `@Database` cannot be instantiated on the JVM, so the repositories are tested
 * against the DAO contracts instead. [FakeVocalorieData.transactions] models real rollback by
 * snapshotting every table before the block and restoring it if the block throws, which is what
 * makes the atomicity test meaningful rather than tautological.
 */
internal class FakeVocalorieData {
    val mealDao = FakeMealDao()
    val activityDao = FakeActivityDao()
    val cacheDao = FakeCacheDao()
    val tableChanges = FakeTableChangeSource()

    var transactionCount: Int = 0
        private set

    val transactions: TransactionRunner = object : TransactionRunner {
        override suspend fun <T> inTransaction(block: suspend () -> T): T {
            transactionCount++
            val snapshot = snapshot()
            return try {
                block()
            } catch (throwable: Throwable) {
                restore(snapshot)
                throw throwable
            }
        }
    }

    private class Snapshot(
        val meals: List<MealEntity>,
        val nextMealId: Long,
        val activities: List<ActivityEntity>,
        val nextActivityId: Long,
        val cachedMeals: Map<String, CachedMealEntity>,
        val cachedItems: Map<String, CachedItemEntity>,
    )

    private fun snapshot() = Snapshot(
        meals = mealDao.rows.toList(),
        nextMealId = mealDao.nextId,
        activities = activityDao.rows.toList(),
        nextActivityId = activityDao.nextId,
        cachedMeals = cacheDao.meals.toMap(),
        cachedItems = cacheDao.items.toMap(),
    )

    private fun restore(snapshot: Snapshot) {
        mealDao.rows.clear()
        mealDao.rows.addAll(snapshot.meals)
        mealDao.nextId = snapshot.nextMealId
        activityDao.rows.clear()
        activityDao.rows.addAll(snapshot.activities)
        activityDao.nextId = snapshot.nextActivityId
        cacheDao.meals.clear()
        cacheDao.meals.putAll(snapshot.cachedMeals)
        cacheDao.items.clear()
        cacheDao.items.putAll(snapshot.cachedItems)
    }
}

internal class FakeMealDao : MealDao {
    val rows = mutableListOf<MealEntity>()
    var nextId: Long = 1L

    override fun getAll(): List<MealEntity> = rows.sortedByDescending { it.createdAtEpochMillis }

    override fun getAllSummaries(): List<MealSummary> = getAll().map { it.toMealSummary() }

    override fun insert(meal: MealEntity): Long {
        val id = nextId++
        rows += meal.copy(id = id)
        return id
    }

    override fun update(meal: MealEntity): Int {
        val index = rows.indexOfFirst { it.id == meal.id }
        if (index < 0) return 0
        rows[index] = meal
        return 1
    }

    override fun deleteById(id: Long): Int = if (rows.removeAll { it.id == id }) 1 else 0
}

internal class FakeActivityDao : ActivityDao {
    val rows = mutableListOf<ActivityEntity>()
    var nextId: Long = 1L

    override fun getAll(): List<ActivityEntity> = rows.sortedByDescending { it.createdAtEpochMillis }

    override fun insert(activity: ActivityEntity): Long {
        val id = nextId++
        rows += activity.copy(id = id)
        return id
    }

    override fun update(activity: ActivityEntity): Int {
        val index = rows.indexOfFirst { it.id == activity.id }
        if (index < 0) return 0
        rows[index] = activity
        return 1
    }

    override fun deleteById(id: Long): Int = if (rows.removeAll { it.id == id }) 1 else 0
}

internal class FakeCacheDao : CacheDao {
    val meals = linkedMapOf<String, CachedMealEntity>()
    val items = linkedMapOf<String, CachedItemEntity>()

    /** Every key handed to [findMeal]/[findItems], so a test can prove a lookup was keyed. */
    val requestedMealKeys = mutableListOf<String>()
    val requestedItemKeys = mutableListOf<List<String>>()

    /** Set to fail the item upsert, standing in for a write that dies part-way through a save. */
    var failItemUpsertWith: Throwable? = null

    override fun getAllMeals(): List<CachedMealEntity> = meals.values.toList()

    override fun getAllItems(): List<CachedItemEntity> = items.values.toList()

    override fun findMeal(normalizedKey: String): CachedMealEntity? {
        requestedMealKeys += normalizedKey
        return meals[normalizedKey]
    }

    override fun findItems(normalizedNames: List<String>): List<CachedItemEntity> {
        requestedItemKeys += normalizedNames
        return normalizedNames.mapNotNull { items[it] }
    }

    override fun upsertMeal(meal: CachedMealEntity) {
        meals[meal.normalizedKey] = meal
    }

    override fun upsertItems(items: List<CachedItemEntity>) {
        failItemUpsertWith?.let { throw it }
        items.forEach { this.items[it.normalizedName] = it }
    }
}

/** A [TableChangeSource] a test can tick by hand, standing in for Room's invalidation tracker. */
internal class FakeTableChangeSource : TableChangeSource {
    private val ticks = MutableStateFlow(0)

    /** The tables each observer asked to be notified about. */
    val observedTables = mutableListOf<List<String>>()

    override fun changes(vararg tables: String): Flow<Unit> {
        observedTables += tables.toList()
        return ticks.map { }
    }

    /** Signal a committed write, the way the database would. */
    fun signalWrite() {
        ticks.value += 1
    }
}

/**
 * Counts how often work was dispatched onto it, so "the repository owns its own dispatching" is a
 * testable claim rather than a comment. Delegates to a real dispatcher, so the work genuinely leaves
 * the calling thread.
 */
internal class CountingDispatcher(
    private val delegate: CoroutineDispatcher = Dispatchers.Default,
) : CoroutineDispatcher() {
    var dispatches: Int = 0
        private set

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        dispatches++
        delegate.dispatch(context, block)
    }
}
