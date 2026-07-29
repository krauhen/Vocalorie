package com.example.vocalorie.data.repository

import com.example.vocalorie.data.ActivityDao
import com.example.vocalorie.data.toEntity
import com.example.vocalorie.data.toSavedActivity
import com.example.vocalorie.model.EditableActivityDraft
import com.example.vocalorie.model.SavedActivity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Activity history: reads observed from the database, writes dispatched off the caller's thread.
 * Callers never wrap a call in a dispatcher switch.
 */
class ActivityRepository(
    private val activityDao: ActivityDao,
    private val tableChanges: TableChangeSource,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    /** Every saved activity, re-emitted whenever the activities table changes. Newest first. */
    fun observeActivities(): Flow<List<SavedActivity>> = tableChanges.changes(VocalorieTables.ACTIVITIES)
        .map { withContext(dispatcher) { activityDao.getAll().map { entity -> entity.toSavedActivity() } } }

    suspend fun activities(): List<SavedActivity> =
        withContext(dispatcher) { activityDao.getAll().map { it.toSavedActivity() } }

    suspend fun saveActivity(draft: EditableActivityDraft, createdAtEpochMillis: Long): Long =
        withContext(dispatcher) { activityDao.insert(draft.toEntity(createdAtEpochMillis = createdAtEpochMillis)) }

    suspend fun updateActivity(id: Long, draft: EditableActivityDraft, createdAtEpochMillis: Long): Int =
        withContext(dispatcher) {
            activityDao.update(draft.toEntity(id = id, createdAtEpochMillis = createdAtEpochMillis))
        }

    suspend fun deleteActivity(id: Long): Int = withContext(dispatcher) { activityDao.deleteById(id) }
}
