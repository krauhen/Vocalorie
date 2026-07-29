package com.example.vocalorie.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.example.vocalorie.model.ActivityType

/**
 * The activity-type glyphs.
 *
 * These live in the UI layer because an [ImageVector] is a Compose type and the model layer must not
 * depend on Compose.
 *
 * Each vector is built exactly once and reused: the mapping used to rebuild one per call, so every
 * activity row and every picker chip allocated a fresh [ImageVector] on every recomposition.
 *
 * Colour is driven by the caller's `tint`, not by the path fill. Material's `Icon` wraps the painter
 * in `ColorFilter.tint(tint)`, whose `SrcIn` blend replaces the fill outright, so what renders is the
 * `tint` argument — `LocalContentColor.current` by default. [ICON_FILL] therefore only carries
 * coverage; it is kept at its original value so an untinted render is unchanged too.
 */
private val ICON_FILL = SolidColor(Color(0xFF111827))

/**
 * One instance per [ActivityType], resolved on first use. The `when` is exhaustive on purpose: a new
 * activity type must fail to compile rather than fall back to some other type's glyph.
 */
private val ACTIVITY_TYPE_ICONS: Map<ActivityType, ImageVector> =
    ActivityType.entries.associateWith { type ->
        when (type) {
            ActivityType.RUNNING -> buildRunningIcon()
            ActivityType.WALKING -> buildWalkingIcon()
            ActivityType.BIKE -> buildBikeIcon()
            ActivityType.KETTLEBELL -> buildKettlebellIcon()
            ActivityType.GYM -> buildGymIcon()
            ActivityType.HIKING -> buildHikingIcon()
            ActivityType.SWIMMING -> buildSwimmingIcon()
            ActivityType.STEPS -> buildStepsIcon()
            ActivityType.OTHER -> buildOtherIcon()
        }
    }

/** The cached glyph for this activity type. Colour comes from the `Icon(tint = …)` at the call site. */
fun ActivityType.activityTypeIcon(): ImageVector = ACTIVITY_TYPE_ICONS.getValue(this)

private fun activityIcon(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply(build).build()

private fun buildStepsIcon(): ImageVector = activityIcon("ActivitySteps") {
    // Two stylised footprints.
    path(fill = ICON_FILL, stroke = null) {
        moveTo(7f, 3f)
        curveTo(8.1f, 3f, 9f, 4.3f, 9f, 6f)
        curveTo(9f, 7.7f, 8.1f, 9f, 7f, 9f)
        curveTo(5.9f, 9f, 5f, 7.7f, 5f, 6f)
        curveTo(5f, 4.3f, 5.9f, 3f, 7f, 3f)
        close()
        moveTo(5f, 10f)
        lineTo(9f, 10f)
        curveTo(9.6f, 10f, 10f, 10.4f, 10f, 11f)
        lineTo(10f, 15f)
        curveTo(10f, 16.7f, 8.7f, 18f, 7f, 18f)
        curveTo(5.3f, 18f, 4f, 16.7f, 4f, 15f)
        lineTo(4f, 11f)
        curveTo(4f, 10.4f, 4.4f, 10f, 5f, 10f)
        close()
    }
    path(fill = ICON_FILL, stroke = null) {
        moveTo(17f, 6f)
        curveTo(18.1f, 6f, 19f, 7.3f, 19f, 9f)
        curveTo(19f, 10.7f, 18.1f, 12f, 17f, 12f)
        curveTo(15.9f, 12f, 15f, 10.7f, 15f, 9f)
        curveTo(15f, 7.3f, 15.9f, 6f, 17f, 6f)
        close()
        moveTo(15f, 13f)
        lineTo(19f, 13f)
        curveTo(19.6f, 13f, 20f, 13.4f, 20f, 14f)
        lineTo(20f, 18f)
        curveTo(20f, 19.7f, 18.7f, 21f, 17f, 21f)
        curveTo(15.3f, 21f, 14f, 19.7f, 14f, 18f)
        lineTo(14f, 14f)
        curveTo(14f, 13.4f, 14.4f, 13f, 15f, 13f)
        close()
    }
}

/** Deliberately characterless: a plain dot, so an unrecognized type claims no specific activity. */
private fun buildOtherIcon(): ImageVector = activityIcon("ActivityOther") {
    path(fill = ICON_FILL, stroke = null) {
        moveTo(16f, 12f)
        curveTo(16f, 14.209f, 14.209f, 16f, 12f, 16f)
        curveTo(9.791f, 16f, 8f, 14.209f, 8f, 12f)
        curveTo(8f, 9.791f, 9.791f, 8f, 12f, 8f)
        curveTo(14.209f, 8f, 16f, 9.791f, 16f, 12f)
        close()
    }
}

private fun buildRunningIcon(): ImageVector = activityIcon("ActivityRunning") {
    path(fill = ICON_FILL, stroke = null) {
        moveTo(12f, 3f)
        lineTo(14.2f, 5.1f)
        lineTo(12.7f, 6.8f)
        lineTo(11.2f, 5.4f)
        close()
        moveTo(10f, 7f)
        lineTo(13.2f, 7f)
        lineTo(15.2f, 9.2f)
        lineTo(18f, 10.2f)
        lineTo(17.1f, 12.3f)
        lineTo(14.1f, 11.4f)
        lineTo(12.7f, 9.8f)
        lineTo(11.2f, 12f)
        lineTo(13.2f, 14f)
        lineTo(16f, 17.5f)
        lineTo(14.4f, 19f)
        lineTo(11.6f, 15.8f)
        lineTo(9.8f, 13.7f)
        lineTo(8.2f, 16.4f)
        lineTo(5.6f, 17.4f)
        lineTo(4.8f, 15.2f)
        lineTo(6.8f, 14.5f)
        lineTo(8.6f, 11.2f)
        lineTo(10f, 7f)
        close()
    }
}

private fun buildWalkingIcon(): ImageVector = activityIcon("ActivityWalking") {
    path(fill = ICON_FILL, stroke = null) {
        moveTo(12f, 3.5f)
        lineTo(14.1f, 5.4f)
        lineTo(12.8f, 6.9f)
        lineTo(10.7f, 5.3f)
        close()
        moveTo(10.4f, 8f)
        lineTo(13.2f, 8f)
        lineTo(14.9f, 10.2f)
        lineTo(16.8f, 12f)
        lineTo(15.4f, 13.5f)
        lineTo(13.9f, 12.2f)
        lineTo(13.1f, 15f)
        lineTo(15.3f, 19f)
        lineTo(13.1f, 19.9f)
        lineTo(11.2f, 15.7f)
        lineTo(9.8f, 19f)
        lineTo(7.4f, 19f)
        lineTo(9.6f, 13.2f)
        lineTo(8.7f, 10.4f)
        lineTo(10.4f, 8f)
        close()
    }
}

private fun buildBikeIcon(): ImageVector = activityIcon("ActivityBike") {
    path(fill = ICON_FILL, stroke = null) {
        moveTo(5.5f, 14f)
        lineTo(7.5f, 16f)
        lineTo(5.5f, 18f)
        lineTo(3.5f, 16f)
        close()
        moveTo(18.5f, 14f)
        lineTo(20.5f, 16f)
        lineTo(18.5f, 18f)
        lineTo(16.5f, 16f)
        close()
        moveTo(7.4f, 15.2f)
        lineTo(10.3f, 9.2f)
        lineTo(13.6f, 9.2f)
        lineTo(15.2f, 12f)
        lineTo(18f, 12f)
        lineTo(15.4f, 8f)
        lineTo(11.2f, 8f)
        lineTo(8.9f, 13f)
        lineTo(6.8f, 13f)
        close()
        moveTo(12.5f, 9.2f)
        lineTo(14.2f, 5.5f)
        lineTo(16f, 5.5f)
        lineTo(16f, 7f)
        lineTo(14.9f, 7f)
        lineTo(13.7f, 9.2f)
        close()
    }
}

private fun buildKettlebellIcon(): ImageVector = activityIcon("ActivityKettlebell") {
    path(fill = ICON_FILL, stroke = null) {
        moveTo(9f, 6.2f)
        lineTo(15f, 6.2f)
        lineTo(15f, 8f)
        lineTo(9f, 8f)
        close()
        moveTo(8f, 9f)
        lineTo(16f, 9f)
        lineTo(18f, 12f)
        lineTo(17f, 18f)
        lineTo(7f, 18f)
        lineTo(6f, 12f)
        close()
        moveTo(8.5f, 11f)
        lineTo(15.5f, 11f)
        lineTo(15.5f, 13f)
        lineTo(8.5f, 13f)
        close()
    }
}

private fun buildGymIcon(): ImageVector = activityIcon("ActivityGym") {
    path(fill = ICON_FILL, stroke = null) {
        moveTo(3f, 10f)
        lineTo(5f, 10f)
        lineTo(5f, 14f)
        lineTo(3f, 14f)
        close()
        moveTo(19f, 10f)
        lineTo(21f, 10f)
        lineTo(21f, 14f)
        lineTo(19f, 14f)
        close()
        moveTo(5.5f, 10.2f)
        lineTo(18.5f, 10.2f)
        lineTo(18.5f, 13.8f)
        lineTo(5.5f, 13.8f)
        close()
        moveTo(7f, 7.5f)
        lineTo(9f, 7.5f)
        lineTo(9f, 16.5f)
        lineTo(7f, 16.5f)
        close()
        moveTo(15f, 7.5f)
        lineTo(17f, 7.5f)
        lineTo(17f, 16.5f)
        lineTo(15f, 16.5f)
        close()
    }
}

private fun buildHikingIcon(): ImageVector = activityIcon("ActivityHiking") {
    path(fill = ICON_FILL, stroke = null) {
        moveTo(4f, 19.5f)
        lineTo(10.2f, 8f)
        lineTo(13.2f, 13f)
        lineTo(15.3f, 10f)
        lineTo(20f, 19.5f)
        close()
        moveTo(13.2f, 4f)
        lineTo(14.2f, 6.5f)
        lineTo(16.8f, 6.8f)
        lineTo(14.9f, 8.6f)
        lineTo(15.4f, 11.2f)
        lineTo(13.2f, 9.9f)
        lineTo(11f, 11.2f)
        lineTo(11.5f, 8.6f)
        lineTo(9.6f, 6.8f)
        lineTo(12.2f, 6.5f)
        close()
    }
}

private fun buildSwimmingIcon(): ImageVector = activityIcon("ActivitySwimming") {
    path(fill = ICON_FILL, stroke = null) {
        moveTo(5.5f, 4f)
        lineTo(7.2f, 5.7f)
        lineTo(5.5f, 7.4f)
        lineTo(3.8f, 5.7f)
        close()
        moveTo(3.5f, 14.4f)
        lineTo(6.5f, 12.6f)
        lineTo(9.6f, 14.4f)
        lineTo(12.7f, 12.6f)
        lineTo(15.8f, 14.4f)
        lineTo(20.5f, 12.1f)
        lineTo(20.5f, 14.3f)
        lineTo(15.8f, 16.6f)
        lineTo(12.7f, 14.8f)
        lineTo(9.6f, 16.6f)
        lineTo(6.5f, 14.8f)
        lineTo(3.5f, 16.6f)
        close()
    }
}
