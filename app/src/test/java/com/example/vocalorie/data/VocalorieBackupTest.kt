package com.example.vocalorie.data

import com.example.vocalorie.testsupport.productionSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

    // --- Accepted-version set (a schema bump must not orphan existing exports) ---

    @Test
    fun everyAdditivelyReachableSchemaVersionStillImports() {
        SUPPORTED_BACKUP_SCHEMA_VERSIONS.forEach { version ->
            val json = encodeBackupEnvelope(BackupEnvelope(schemaVersion = version, meals = listOf(sampleMeal(id = 1L))))

            val decoded = parseBackupEnvelope(json)

            assertEquals(version, decoded.schemaVersion)
            assertEquals(1L, decoded.meals.single().id)
        }
    }

    @Test
    fun exportsFromBeforeTheCategoryBumpStillImport() {
        // v8 predates both `meals.category` and `cached_meals.category`; the omitted fields take
        // their additive defaults instead of the file being refused.
        val legacyJson = """
            {
              "format": "vocalorie-backup",
              "schemaVersion": 8,
              "exportedAtEpochMillis": 1700000000000,
              "meals": [{
                "id": 1, "createdAtEpochMillis": 1700000000001, "title": "Old meal", "query": "old",
                "itemsJson": "[]", "caloriesKcal": 100.0, "amountGml": null, "proteinG": null,
                "carbsG": null, "fatG": null, "saturatedFatG": null, "sugarG": null, "saltG": null,
                "assumptionsText": "", "warningsText": "", "confidence": "LOW", "needsHumanReview": false
              }],
              "cachedMeals": [{
                "normalizedKey": "old", "title": "Old meal", "query": "old", "itemsJson": "[]",
                "assumptionsText": "", "warningsText": "", "confidence": "LOW", "needsHumanReview": false
              }]
            }
        """.trimIndent()

        val decoded = parseBackupEnvelope(legacyJson)

        assertEquals(8, decoded.schemaVersion)
        assertEquals("OTHER", decoded.meals.single().category)
        assertEquals("OTHER", decoded.cachedMeals.single().category)
    }

    @Test
    fun parseRejectsSchemaVersionBelowTheSupportedRange() {
        val json = encodeBackupEnvelope(BackupEnvelope(schemaVersion = SUPPORTED_BACKUP_SCHEMA_VERSIONS.first - 1))
        assertThrows(BackupFormatException::class.java) { parseBackupEnvelope(json) }
    }

    @Test
    fun rejectionMessageNamesTheSupportedRange() {
        val json = encodeBackupEnvelope(BackupEnvelope(schemaVersion = SUPPORTED_BACKUP_SCHEMA_VERSIONS.last + 1))

        val message = assertThrows(BackupFormatException::class.java) { parseBackupEnvelope(json) }.message.orEmpty()

        assertTrue(message, message.contains("v${SUPPORTED_BACKUP_SCHEMA_VERSIONS.first}"))
        assertTrue(message, message.contains("v${SUPPORTED_BACKUP_SCHEMA_VERSIONS.last}"))
    }

    @Test
    fun exportedSchemaVersionEqualsTheRoomDatabaseVersion() {
        val declaredVersion = requireNotNull(
            Regex("""version\s*=\s*(\d+)""")
                .find(productionSource("VocalorieDatabase.java"))
                ?.groupValues
                ?.get(1)
                ?.toInt(),
        ) { "Could not read the @Database version from VocalorieDatabase.java" }

        assertEquals(declaredVersion, BACKUP_SCHEMA_VERSION)
        assertEquals(BACKUP_SCHEMA_VERSION, SUPPORTED_BACKUP_SCHEMA_VERSIONS.last)
        assertEquals(BACKUP_SCHEMA_VERSION, BackupEnvelope().schemaVersion)
    }

    /**
     * Source-text contract: `VocalorieDatabase` is an abstract Room class that cannot be built on the
     * JVM, so a torn snapshot is only reachable on-device. This pins that all four reads sit after the
     * transaction opens, which is the property that makes tearing impossible.
     */
    @Test
    fun exportReadsEveryTableInsideOneTransaction() {
        val source = productionSource("VocalorieBackup.kt")
        val exportBody = source.substringAfter("fun exportBackupJson").substringBefore("\nfun ")
        val transactionCall = listOf("runInTransaction", "withTransaction").firstOrNull { exportBody.contains(it) }

        assertTrue("exportBackupJson must open a transaction:\n$exportBody", transactionCall != null)
        listOf("mealDao().getAll()", "activityDao().getAll()", "cacheDao().getAllMeals()", "cacheDao().getAllItems()")
            .forEach { read ->
                assertTrue(
                    "$read must be read inside the transaction block",
                    exportBody.substringAfter(transactionCall!!).contains(read),
                )
            }
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
