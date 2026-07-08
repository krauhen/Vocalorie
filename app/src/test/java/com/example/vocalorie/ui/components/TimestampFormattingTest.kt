package com.example.vocalorie.ui.components

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class TimestampFormattingTest {
    private val zone: ZoneId = ZoneId.of("Europe/London")

    @Test
    fun editableTimestampRoundTripsWithExplicitMinuteFormat() {
        val epochMillis = LocalDateTime.of(2026, 6, 30, 9, 5).atZone(zone).toInstant().toEpochMilli()

        assertEquals("2026-06-30 09:05", formatEditableTimestamp(epochMillis, zone))
        assertEquals(epochMillis, parseEditableTimestamp("2026-06-30 09:05", zone))
        assertEquals(epochMillis, parseEditableTimestamp("2026-06-30 9:05", zone))
    }

    @Test
    fun editableTimestampDoesNotResyncWhenUserEditsHourWithoutChangingInstant() {
        val epochMillis = LocalDateTime.of(2026, 6, 30, 13, 34).atZone(zone).toInstant().toEpochMilli()

        assertFalse(shouldResyncEditableTimestamp("2026-06-30 13:34", epochMillis, zone))
    }

    @Test
    fun dateFormattingUsesEuropeanDayMonthFormat() {
        val epochMillis = LocalDateTime.of(2026, 6, 30, 12, 0).atZone(zone).toInstant().toEpochMilli()

        assertTrue(formatDate(epochMillis).startsWith("30.06.2026 "))
        assertEquals("30.06.2026", formatDateOnly(epochMillis))
    }

    @Test
    fun editableTimestampRejectsInvalidOrImpossibleDates() {
        assertNull(parseEditableTimestamp("30/06/2026 09:05", zone))
        assertNull(parseEditableTimestamp("2026-02-30 09:05", zone))
        assertNull(parseEditableTimestamp("2026-06-30 24:01", zone))
    }

}
