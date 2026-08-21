package com.example.vocalorie.ui.components

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryTimestampMergeTest {
    private val zone: ZoneId = ZoneId.of("Europe/Zurich")

    @Test
    fun mergePickedDateKeepsEveningTimeWhenDateMovesEarlier() {
        val current = LocalDateTime.of(2026, 6, 30, 19, 45).atZone(zone).toInstant().toEpochMilli()
        val pickedDate = toPickerDateMillis(LocalDateTime.of(2026, 6, 10, 0, 0).atZone(zone).toInstant().toEpochMilli(), zone)

        val merged = mergePickedDate(current, pickedDate, zone)

        assertEquals(LocalDateTime.of(2026, 6, 10, 19, 45), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(merged), zone))
    }

    @Test
    fun mergePickedDateFallsOnPickedCalendarDateInZone() {
        val current = LocalDateTime.of(2026, 3, 1, 8, 0).atZone(zone).toInstant().toEpochMilli()
        val pickedDate = toPickerDateMillis(LocalDateTime.of(2026, 3, 29, 0, 0).atZone(zone).toInstant().toEpochMilli(), zone)

        val merged = mergePickedDate(current, pickedDate, zone)

        assertEquals(LocalDateTime.of(2026, 3, 29, 8, 0).atZone(zone).toLocalDate(), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(merged), zone).toLocalDate())
    }

    @Test
    fun mergePickedDateResolvesToTappedDateThroughDstTransitionDay() {
        // 2026-03-29 is the Europe/Zurich spring-forward day; 02:30 local does not exist.
        val current = LocalDateTime.of(2026, 1, 1, 2, 30).atZone(zone).toInstant().toEpochMilli()
        val pickedDate = toPickerDateMillis(LocalDateTime.of(2026, 3, 29, 0, 0).atZone(zone).toInstant().toEpochMilli(), zone)

        val merged = mergePickedDate(current, pickedDate, zone)

        assertEquals(LocalDateTime.of(2026, 3, 29, 2, 30).atZone(zone).toLocalDate(), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(merged), zone).toLocalDate())
    }

    @Test
    fun mergePickedTimeChangesOnlyTimeAndZerosSeconds() {
        val current = LocalDateTime.of(2026, 6, 30, 8, 15, 45).atZone(zone).toInstant().toEpochMilli()

        val merged = mergePickedTime(current, 21, 5, zone)

        assertEquals(LocalDateTime.of(2026, 6, 30, 21, 5), LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(merged), zone))
    }

    @Test
    fun pickingCurrentDateIsNoOp() {
        val current = LocalDateTime.of(2026, 6, 30, 19, 45).atZone(zone).toInstant().toEpochMilli()
        val pickedDate = toPickerDateMillis(current, zone)

        assertEquals(current, mergePickedDate(current, pickedDate, zone))
    }

    @Test
    fun toPickerDateMillisRoundTripsThroughMergePickedDate() {
        val current = LocalDateTime.of(2026, 6, 30, 19, 45).atZone(zone).toInstant().toEpochMilli()

        assertEquals(current, mergePickedDate(current, toPickerDateMillis(current, zone), zone))
    }

    @Test
    fun toPickerDateMillisUsesLocalDateWhenItDiffersFromUtcDate() {
        // 00:30 in Europe/Zurich on 2026-06-30 is still 2026-06-29 in UTC.
        val current = LocalDateTime.of(2026, 6, 30, 0, 30).atZone(zone).toInstant().toEpochMilli()

        val pickedDate = toPickerDateMillis(current, zone)
        val merged = mergePickedDate(current, pickedDate, zone)

        assertEquals(current, merged)
    }
}
