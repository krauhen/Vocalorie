package com.example.vocalorie.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class VocalorieBackupTest {
    @Test
    fun envelopeRoundTripsAllTablesPreservingPrimaryKeys() {
        val envelope = BackupEnvelope(
            exportedAtEpochMillis = 1_700_000_000_000L,
            meals = listOf(sampleMeal(id = 7L), sampleMeal(id = 42L)),
            activities = listOf(sampleActivity(id = 3L)),
            cachedMeals = listOf(sampleCachedMeal(key = "eggs toast")),
            cachedItems = listOf(sampleCachedItem(name = "eggs")),
        )

        val decoded = parseBackupEnvelope(encodeBackupEnvelope(envelope))

        assertEquals(envelope, decoded)
        assertEquals(listOf(7L, 42L), decoded.meals.map { it.id })
        assertEquals(3L, decoded.activities.single().id)
    }

    @Test
    fun selectNewRowsSkipsKeysThatAlreadyExist() {
        val incoming = listOf(sampleMeal(id = 1L), sampleMeal(id = 2L), sampleMeal(id = 3L))
        val existing = setOf(2L)

        val new = selectNewBackupRows(incoming, existing) { it.id }

        assertEquals(listOf(1L, 3L), new.map { it.id })
    }

    @Test
    fun reParsingAnExportedFileYieldsNoNewRowsWhenAllKeysExist() {
        val meals = listOf(sampleMeal(id = 1L), sampleMeal(id = 2L))
        val existingIds = meals.mapTo(HashSet()) { it.id }

        val new = selectNewBackupRows(meals, existingIds) { it.id }

        assertEquals(emptyList<MealEntity>(), new)
    }

    @Test
    fun parseRejectsUnknownFormat() {
        val json = encodeBackupEnvelope(BackupEnvelope(format = "something-else"))
        assertThrows(BackupFormatException::class.java) { parseBackupEnvelope(json) }
    }

    @Test
    fun parseRejectsUnsupportedSchemaVersion() {
        val json = encodeBackupEnvelope(BackupEnvelope(schemaVersion = BACKUP_SCHEMA_VERSION + 1))
        assertThrows(BackupFormatException::class.java) { parseBackupEnvelope(json) }
    }

    @Test
    fun parseRejectsGarbageJson() {
        assertThrows(BackupFormatException::class.java) { parseBackupEnvelope("not json at all") }
    }

    private fun sampleMeal(id: Long) = MealEntity(
        id = id,
        createdAtEpochMillis = 1_700_000_000_000L + id,
        title = "Meal $id",
        query = "meal $id",
        itemsJson = "[]",
        caloriesKcal = 123.0,
        amountGml = 200.0,
        proteinG = 10.0,
        carbsG = 20.0,
        fatG = 5.0,
        saturatedFatG = 1.0,
        sugarG = 2.0,
        saltG = 0.5,
        assumptionsText = "assume",
        warningsText = "",
        confidence = "LOW",
        needsHumanReview = false,
    )

    private fun sampleActivity(id: Long) = ActivityEntity(
        id = id,
        createdAtEpochMillis = 1_700_000_000_000L + id,
        type = "STEPS",
        title = "Walk",
        description = "",
        caloriesBurnedKcal = 80.0,
        durationMinutes = 0,
        stepsCount = 2000,
    )

    private fun sampleCachedMeal(key: String) = CachedMealEntity(
        normalizedKey = key,
        title = "Cached",
        query = key,
        itemsJson = "[]",
        assumptionsText = "",
        warningsText = "",
        confidence = "LOW",
        needsHumanReview = false,
    )

    private fun sampleCachedItem(name: String) = CachedItemEntity(
        normalizedName = name,
        displayName = name,
        caloriesKcalPer100 = 150.0,
        proteinGPer100 = 13.0,
        carbsGPer100 = 1.0,
        fatGPer100 = 11.0,
        saturatedFatGPer100 = 3.0,
        sugarGPer100 = 1.0,
        saltGPer100 = 0.3,
        source = "",
        reasoning = "",
    )
}
