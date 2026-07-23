package com.example.vocalorie.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MealMigrationTest {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VocalorieDatabase::class.java.canonicalName,
    )

    @Before
    fun setUp() {
        // Clear any existing database before each test
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase("vocalorie.db")
    }

    @Test
    fun migrate4To5RemovesSourceColumnButPreservesMealData() {
        // Create v4 database with source values
        helper.createDatabase("vocalorie.db", 4).use { db ->
            db.execSQL(
                """
                INSERT INTO meals (
                    id, createdAtEpochMillis, title, query, itemsJson,
                    caloriesKcal, amountGml, proteinG, carbsG, fatG,
                    saturatedFatG, sugarG, saltG, source, assumptionsText,
                    warningsText, confidence, needsHumanReview
                ) VALUES (
                    1, 1688127180000, 'Test Meal', '2 eggs', '[]',
                    150.0, 100.0, 12.5, 1.0, 10.0,
                    3.2, 0.4, 0.3, 'https://example.com/food', 'No assumptions',
                    'No warnings', 'MEDIUM', 1
                )
                """.trimIndent(),
            )
        }

        // Run migration
        val migratedDb = helper.runMigrationsAndValidate("vocalorie.db", 5, true, VocalorieDatabase.MIGRATION_4_5)

        // Verify schema version
        val version = migratedDb.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        assert(version == 5) { "Expected schema version 5, got $version" }

        // Verify meal data is preserved (except source column)
        val cursor = migratedDb.query(
            "SELECT id, title, query, caloriesKcal, confidence FROM meals WHERE id = 1",
        )
        cursor.use {
            assert(it.moveToFirst()) { "Expected meal record not found" }
            assert(it.getLong(0) == 1L) { "Expected id 1" }
            assert(it.getString(1) == "Test Meal") { "Expected title 'Test Meal'" }
            assert(it.getString(2) == "2 eggs") { "Expected query '2 eggs'" }
            assert(it.getDouble(3) == 150.0) { "Expected calories 150.0" }
            assert(it.getString(4) == "MEDIUM") { "Expected confidence 'MEDIUM'" }
        }

        // Verify source column does not exist in v5 schema
        val schemaCheck = migratedDb.query("PRAGMA table_info(meals)")
        schemaCheck.use { cursor ->
            val columnNames = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                columnNames.add(cursor.getString(1))
            }
            assert(!columnNames.contains("source")) { "Source column should not exist in v5 schema" }
        }

        migratedDb.close()
    }

    @Test
    fun migrate7To8CreatesEmptyCachesAndPreservesMeals() {
        // Create a v7 database with an existing meal.
        helper.createDatabase("vocalorie.db", 7).use { db ->
            db.execSQL(
                """
                INSERT INTO meals (
                    id, createdAtEpochMillis, title, query, itemsJson,
                    caloriesKcal, amountGml, proteinG, carbsG, fatG,
                    saturatedFatG, sugarG, saltG, assumptionsText,
                    warningsText, confidence, needsHumanReview
                ) VALUES (
                    1, 1688127180000, 'Buttermilch', 'Buttermilch 200g', '[]',
                    100.0, 200.0, 5.0, 8.0, 2.0,
                    1.0, 6.0, 0.2, 'No assumptions',
                    'No warnings', 'MEDIUM', 1
                )
                """.trimIndent(),
            )
        }

        val migratedDb = helper.runMigrationsAndValidate("vocalorie.db", 8, true, VocalorieDatabase.MIGRATION_7_8)

        val version = migratedDb.query("PRAGMA user_version").use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        assert(version == 8) { "Expected schema version 8, got $version" }

        // Existing meal survives untouched.
        migratedDb.query("SELECT title, query, caloriesKcal FROM meals WHERE id = 1").use {
            assert(it.moveToFirst()) { "Expected meal record not found" }
            assert(it.getString(0) == "Buttermilch") { "Expected title 'Buttermilch'" }
            assert(it.getString(1) == "Buttermilch 200g") { "Expected query 'Buttermilch 200g'" }
            assert(it.getDouble(2) == 100.0) { "Expected calories 100.0" }
        }

        // Both cache tables exist and start empty (no backfill from history).
        migratedDb.query("SELECT COUNT(*) FROM cached_meals").use {
            it.moveToFirst()
            assert(it.getInt(0) == 0) { "Expected empty cached_meals" }
        }
        migratedDb.query("SELECT COUNT(*) FROM cached_items").use {
            it.moveToFirst()
            assert(it.getInt(0) == 0) { "Expected empty cached_items" }
        }

        migratedDb.close()
    }
}
