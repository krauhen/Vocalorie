package com.example.vocalorie.ai

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionPromptContractTest {
    @Test
    fun promptRequiresMacrosAndRealImageInputWheneverCaloriesAreEstimated() {
        val promptSource = source("app/src/main/java/com/example/vocalorie/ai/KoogNutritionSpike.kt")
        val dtoSource = source("app/src/main/java/com/example/vocalorie/model/NutritionEstimateDtos.kt")

        assertTrue(promptSource.contains("protein, carbohydrates, and fat"))
        assertTrue(promptSource.contains("amount in g/ml"))
        assertTrue(promptSource.contains("saturated fat, sugar, and salt"))
        assertTrue(promptSource.contains("Do not return calories without"))
        assertTrue(promptSource.contains("Meal totals are computed by the app from item rows"))
        assertTrue(promptSource.contains("item values only"))
        assertTrue(promptSource.contains("combine the photo with the full text query"))
        assertTrue(promptSource.contains("Use that amount together with the image."))
        assertTrue(promptSource.contains("LLMParams(schema = outputStructure.schema)"))
        assertTrue(promptSource.contains("Estimate this meal from the attached photo"))
        assertTrue(promptSource.contains("imageAttachment?.let { image(it.image) }"))
        assertTrue(promptSource.contains("imageAttachment: GalleryImageAttachment? = null"))

        assertTrue(dtoSource.contains("A structured nutrition estimate"))
        assertTrue(dtoSource.contains("source must be a concrete http/https food-entry page URL"))
        assertFalse(dtoSource.contains("Ignored by the app"))
        assertFalse(dtoSource.contains("Normalized food item name."))
        assertFalse(dtoSource.contains("Required estimated calories in kcal."))
        assertFalse(dtoSource.contains("Estimated approximate amount in g/ml"))
        assertFalse(dtoSource.contains("Estimated saturated fat in grams"))
        assertFalse(dtoSource.contains("Source URL or source name"))
        assertFalse(dtoSource.contains("or null if unknown"))
        assertFalse(dtoSource.contains("A single estimated food item."))
    }

    @Test
    fun roomDatabaseRegistersAdditiveMigrationsForNutritionLabelAndAmountFields() {
        val databaseSource = source("app/src/main/java/com/example/vocalorie/data/VocalorieDatabase.java")
        val entitySource = source("app/src/main/java/com/example/vocalorie/data/MealEntity.kt")

        assertTrue(databaseSource.contains("version = 4"))
        assertTrue(databaseSource.contains("Migration(1, 2)"))
        assertTrue(databaseSource.contains("Migration(2, 3)"))
        assertTrue(databaseSource.contains("Migration(3, 4)"))
        assertTrue(databaseSource.contains("ALTER TABLE meals ADD COLUMN saturatedFatG REAL"))
        assertTrue(databaseSource.contains("ALTER TABLE meals ADD COLUMN sugarG REAL"))
        assertTrue(databaseSource.contains("ALTER TABLE meals ADD COLUMN saltG REAL"))
        assertTrue(databaseSource.contains("ALTER TABLE meals ADD COLUMN source TEXT"))
        assertTrue(databaseSource.contains("ALTER TABLE meals ADD COLUMN amountGml REAL"))
        assertTrue(databaseSource.contains("ALTER TABLE meals ADD COLUMN title TEXT NOT NULL DEFAULT ''"))
        assertFalse(databaseSource.contains("fallbackToDestructiveMigration"))

        assertTrue(entitySource.contains("val title: String"))
        assertTrue(entitySource.contains("val amountGml: Double?"))
        assertTrue(entitySource.contains("val saturatedFatG: Double?"))
        assertTrue(entitySource.contains("val sugarG: Double?"))
        assertTrue(entitySource.contains("val saltG: Double?"))
        assertTrue(entitySource.contains("val source: String?"))
    }

    @Test
    fun koogPromptUsesStructuredJsonSchemaAndInlineImageAttachment() {
        val promptSource = source("app/src/main/java/com/example/vocalorie/ai/KoogNutritionSpike.kt")

        assertTrue(promptSource.contains("GPT54MINI"))
        assertTrue(promptSource.contains("GPT5_4Mini"))
        assertTrue(promptSource.contains("prompt(\"vocalorie-nutrition-spike\""))
        assertTrue(promptSource.contains("LLMParams(schema = outputStructure.schema)"))
        assertTrue(promptSource.contains("image(it.image)"))
    }

    private fun source(path: String): String = File(path).takeIf { it.exists() }
        ?.readText()
        ?: File("../$path").readText()
}
