package com.example.vocalorie.data.repository

import com.example.vocalorie.data.toEntity
import com.example.vocalorie.model.ActivityDraftValidation
import com.example.vocalorie.model.ActivityType
import com.example.vocalorie.model.EditableActivityDraft
import com.example.vocalorie.model.validate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityRepositoryTest {

    @Test
    fun activitiesAreMappedToDomainModelsNewestFirst() = runTest {
        val data = FakeVocalorieData()
        val repository = data.activityRepository()
        repository.save(
            draft(title = "Walk", type = ActivityType.STEPS, steps = "8000"),
            createdAtEpochMillis = 1_000L,
        )
        repository.save(draft(title = "Run"), createdAtEpochMillis = 2_000L)

        val activities = repository.activities()

        assertEquals(listOf("Run", "Walk"), activities.map { it.title })
        assertEquals(ActivityType.RUNNING, activities.first().type)
        assertEquals(8000, activities.last().stepsCount)
        // A STEPS entry's calories are derived from its step count, not from the entered text.
        assertEquals(280.0, activities.last().caloriesBurnedKcal, 1e-9)
        // Only a STEPS entry carries a step count.
        assertNull(activities.first().stepsCount)
    }

    @Test
    fun observedActivitiesStartFromTheCurrentStateAndReEmitOnEachCommittedWrite() = runTest {
        val data = FakeVocalorieData()
        val repository = data.activityRepository()
        val emissions = mutableListOf<Int>()

        val collector = launch(Dispatchers.Unconfined) {
            repository.observeActivities().take(2).collect { emissions += it.size }
        }
        data.activityDao.insert(draft(title = "Walk").toEntity(createdAtEpochMillis = 1_000L))
        data.tableChanges.signalWrite()
        collector.join()

        assertEquals(listOf(0, 1), emissions)
        assertEquals(listOf(listOf("activities")), data.tableChanges.observedTables)
    }

    @Test
    fun updatingAnActivityKeepsItsId() = runTest {
        val data = FakeVocalorieData()
        val repository = data.activityRepository()
        val id = repository.save(draft(title = "Walk"), createdAtEpochMillis = 1_000L)

        val longWalk = draft(title = "Long walk")
        val updated = repository.updateActivity(
            id = id,
            draft = longWalk,
            validated = longWalk.validated(),
            createdAtEpochMillis = 1_500L,
        )

        assertEquals(1, updated)
        assertEquals(id, data.activityDao.rows.single().id)
        assertEquals("Long walk", data.activityDao.rows.single().title)
        assertEquals(1_500L, data.activityDao.rows.single().createdAtEpochMillis)
    }

    @Test
    fun deletingAnActivityRemovesOnlyThatRow() = runTest {
        val data = FakeVocalorieData()
        val repository = data.activityRepository()
        val keptId = repository.save(draft(title = "Walk"), createdAtEpochMillis = 1_000L)
        val doomedId = repository.save(draft(title = "Run"), createdAtEpochMillis = 2_000L)

        assertEquals(1, repository.deleteActivity(doomedId))

        assertEquals(listOf(keptId), data.activityDao.rows.map { it.id })
    }

    @Test
    fun everyReadAndWriteRunsOnTheInjectedDispatcher() = runTest {
        val data = FakeVocalorieData()
        val dispatcher = CountingDispatcher()
        val repository = data.activityRepository(dispatcher = dispatcher)

        repository.save(draft(title = "Walk"), createdAtEpochMillis = 1_000L)
        val afterWrite = dispatcher.dispatches
        repository.activities()

        assertTrue("the save must dispatch", afterWrite > 0)
        assertTrue("the read must dispatch too", dispatcher.dispatches > afterWrite)
    }

    private fun FakeVocalorieData.activityRepository(
        dispatcher: CoroutineDispatcher = Dispatchers.Unconfined,
    ) = ActivityRepository(
        activityDao = activityDao,
        tableChanges = tableChanges,
        dispatcher = dispatcher,
    )

    /** Saves through the validated path, which is the only path production uses. */
    private suspend fun ActivityRepository.save(
        draft: EditableActivityDraft,
        createdAtEpochMillis: Long,
    ): Long = saveActivity(draft, draft.validated(), createdAtEpochMillis)

    private fun EditableActivityDraft.validated(): ActivityDraftValidation.Valid =
        validate(kcalPerStep = 0.035) as ActivityDraftValidation.Valid

    private fun draft(
        title: String,
        type: ActivityType = ActivityType.RUNNING,
        steps: String = "",
    ): EditableActivityDraft = EditableActivityDraft(
        type = type,
        title = title,
        description = "",
        caloriesBurnedKcal = "250",
        durationMinutes = "30",
        steps = steps,
        createdAtEpochMillis = null,
    )
}
