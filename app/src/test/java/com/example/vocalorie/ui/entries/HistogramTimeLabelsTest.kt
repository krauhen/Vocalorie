package com.example.vocalorie.ui.entries

import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistogramTimeLabelsTest {
    private val zone: ZoneId = ZoneId.of("Europe/Berlin")

    private fun bucket(hour: Int, endHour: Int) = MealCaloriesBucket(
        startInclusive = LocalDateTime.of(2026, 8, 20, hour, 0).atZone(zone).toInstant(),
        end = LocalDateTime.of(2026, 8, 20, endHour, 0).atZone(zone).toInstant(),
        caloriesKcal = 0.0,
    )

    @Test
    fun `a normal bucket list labels its start and last minute`() {
        val buckets = listOf(bucket(6, 8), bucket(8, 10), bucket(10, 12))

        val labels = formatHistogramTimeLabels(buckets, Locale.GERMAN, zone)

        assertEquals("06:00", labels.start)
        assertEquals("11:59", labels.end)
        // A midpoint label is only worth the width once the axis is crowded.
        assertNull(labels.midpoint)
    }

    @Test
    fun `a crowded bucket list also labels its midpoint`() {
        val buckets = (0 until 8).map { bucket(it, it + 1) }

        val labels = formatHistogramTimeLabels(buckets, Locale.GERMAN, zone)

        assertEquals("00:00", labels.start)
        assertEquals("04:00", labels.midpoint)
        assertEquals("07:59", labels.end)
    }

    @Test
    fun `an empty bucket list falls back to the whole day with no midpoint`() {
        val labels = formatHistogramTimeLabels(emptyList(), Locale.GERMAN, zone)

        assertEquals("00:00", labels.start)
        assertNull(labels.midpoint)
        assertEquals("23:59", labels.end)
    }

    @Test
    fun `the labels do not depend on the locale`() {
        val buckets = listOf(bucket(6, 8), bucket(8, 10))

        val german = formatHistogramTimeLabels(buckets, Locale.GERMAN, zone)
        val us = formatHistogramTimeLabels(buckets, Locale.US, zone)

        assertEquals(german, us)
    }
}
