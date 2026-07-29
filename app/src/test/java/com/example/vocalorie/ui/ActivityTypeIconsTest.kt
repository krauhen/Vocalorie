package com.example.vocalorie.ui

import com.example.vocalorie.model.ActivityType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The glyph mapping used to build a fresh `ImageVector` on every call, i.e. on every recomposition of
 * every activity row and picker chip. These pin that it is now built once per type and reused.
 */
class ActivityTypeIconsTest {

    @Test
    fun repeatedLookupsReturnTheSameCachedInstance() {
        ActivityType.entries.forEach { type ->
            assertSame(
                "$type must reuse one cached vector rather than rebuild it",
                type.activityTypeIcon(),
                type.activityTypeIcon(),
            )
        }
    }

    @Test
    fun everyActivityTypeIncludingOtherHasItsOwnGlyph() {
        val icons = ActivityType.entries.map { it.activityTypeIcon() }

        assertEquals(ActivityType.entries.size, icons.size)
        // Distinct instances, so no type silently borrows another's glyph.
        assertEquals(ActivityType.entries.size, icons.map { System.identityHashCode(it) }.toSet().size)
        assertEquals(ActivityType.entries.size, icons.map { it.name }.toSet().size)
    }

    @Test
    fun glyphNamesAreUnchangedFromTheModelLayerVersion() {
        // The move out of `model/` must not have renamed or reshuffled a glyph.
        assertEquals("ActivityRunning", ActivityType.RUNNING.activityTypeIcon().name)
        assertEquals("ActivityWalking", ActivityType.WALKING.activityTypeIcon().name)
        assertEquals("ActivityBike", ActivityType.BIKE.activityTypeIcon().name)
        assertEquals("ActivityKettlebell", ActivityType.KETTLEBELL.activityTypeIcon().name)
        assertEquals("ActivityGym", ActivityType.GYM.activityTypeIcon().name)
        assertEquals("ActivityHiking", ActivityType.HIKING.activityTypeIcon().name)
        assertEquals("ActivitySwimming", ActivityType.SWIMMING.activityTypeIcon().name)
        assertEquals("ActivitySteps", ActivityType.STEPS.activityTypeIcon().name)
        assertEquals("ActivityOther", ActivityType.OTHER.activityTypeIcon().name)
    }

}
