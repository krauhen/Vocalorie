package com.example.vocalorie.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the additive 9 -> 10 migration that adds `cached_meals.category`, and the 10 -> 11
 * migration that clears both caches because the cache-key rule changed.
 *
 * v10 is the first version with a committed schema JSON (`exportSchema` was off before this
 * change), so v1-v8 remain verified only empirically on-device — recorded as accepted debt.
 */
@RunWith(AndroidJUnit4::class)
class VocalorieDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        VocalorieDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate9To10AddsCategoryColumnDefaultingToOther() {
        helper.createDatabase(TEST_DB, 9).use { db ->
            db.execSQL(
                """
                INSERT INTO cached_meals
                    (normalizedKey, title, query, itemsJson, assumptionsText, warningsText, confidence, needsHumanReview)
                VALUES
                    ('gyoza', 'Beef Gyoza', 'beef gyoza 600g', '[]', '', '', 'MEDIUM', 1)
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            10,
            true,
            VocalorieDatabase.MIGRATION_9_10,
        )

        db.query("SELECT normalizedKey, title, category FROM cached_meals").use { cursor ->
            assertTrue("expected the pre-migration row to survive", cursor.moveToFirst())
            assertEquals("gyoza", cursor.getString(0))
            assertEquals("Beef Gyoza", cursor.getString(1))
            assertEquals("OTHER", cursor.getString(2))
            assertEquals(1, cursor.count)
        }
    }

    @Test
    fun migrate9To10PreservesEveryExistingCachedRow() {
        helper.createDatabase(TEST_DB, 9).use { db ->
            repeat(3) { index ->
                db.execSQL(
                    """
                    INSERT INTO cached_meals
                        (normalizedKey, title, query, itemsJson, assumptionsText, warningsText, confidence, needsHumanReview)
                    VALUES
                        ('key$index', 'Meal $index', 'query $index', '[]', '', '', 'MEDIUM', 1)
                    """.trimIndent(),
                )
            }
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            10,
            true,
            VocalorieDatabase.MIGRATION_9_10,
        )

        db.query("SELECT COUNT(*) FROM cached_meals").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(3, cursor.getInt(0))
        }
    }

    @Test
    fun migrate10To11ClearsBothCachesAndKeepsTheHistory() {
        helper.createDatabase(TEST_DB, 10).use { db ->
            db.execSQL(
                """
                INSERT INTO cached_meals
                    (normalizedKey, title, query, itemsJson, assumptionsText, warningsText, confidence, needsHumanReview, category)
                VALUES
                    ('karotten zwei', 'Karotten', 'Karotten zwei', '[]', '', '', 'MEDIUM', 1, 'OTHER'),
                    ('karotten vier', 'Karotten', 'Karotten vier', '[]', '', '', 'MEDIUM', 1, 'OTHER')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO cached_items
                    (normalizedName, displayName, caloriesKcalPer100, proteinGPer100, carbsGPer100, fatGPer100,
                     saturatedFatGPer100, sugarGPer100, saltGPer100, source, reasoning)
                VALUES
                    ('karotten', 'Karotten', 41.0, 0.9, 10.0, 0.2, 0.0, 4.7, 0.1, '', '')
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO meals
                    (id, createdAtEpochMillis, title, query, itemsJson, caloriesKcal, amountGml, proteinG, carbsG,
                     fatG, saturatedFatG, sugarG, saltG, assumptionsText, warningsText, confidence,
                     needsHumanReview, category)
                VALUES
                    (1, 123, 'Karotten', 'Karotten zwei', '[]', 41.0, 100.0, 0.9, 10.0, 0.2, 0.0, 4.7, 0.1, '', '', 'MEDIUM', 1, 'OTHER')
                """.trimIndent(),
            )
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            11,
            true,
            VocalorieDatabase.MIGRATION_10_11,
        )

        db.query("SELECT COUNT(*) FROM cached_meals").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("expected the meal cache to be emptied", 0, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM cached_items").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("expected the item cache to be emptied", 0, cursor.getInt(0))
        }
        db.query("SELECT id, query FROM meals").use { cursor ->
            assertTrue("expected the meals history to survive", cursor.moveToFirst())
            assertEquals(1, cursor.count)
            assertEquals(1, cursor.getInt(0))
            assertEquals("Karotten zwei", cursor.getString(1))
        }
    }

    private companion object {
        const val TEST_DB = "migration-test.db"
    }
}
