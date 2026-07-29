package com.example.vocalorie.ai

import com.example.vocalorie.testsupport.productionSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionPromptContractTest {
    @Test
    fun promptRequiresMacrosAndRealImageInputWheneverCaloriesAreEstimated() {
        val promptSource = productionSource("KoogNutritionAgent.kt")
        val dtoSource = productionSource("NutritionEstimateDtos.kt")

        assertTrue(promptSource.contains("protein, carbohydrates, and fat"))
        assertTrue(promptSource.contains("amount in g/ml"))
        assertTrue(promptSource.contains("saturated fat, sugar, and salt"))
        assertTrue(promptSource.contains("Do not return calories without"))
        assertTrue(promptSource.contains("Meal totals are computed by the app from item rows"))
        assertTrue(promptSource.contains("item values only"))
        assertTrue(promptSource.contains("combine the photo with the full text query"))
        assertTrue(promptSource.contains("Use that amount together with the image."))
        assertTrue(promptSource.contains("German decimal commas"))
        assertTrue(promptSource.contains("German BLS"))
        assertTrue(promptSource.contains("Every food item's source must be a concrete http/https food-entry page URL"))
        assertTrue(promptSource.contains("leave source empty rather than naming a database"))
        assertTrue(promptSource.contains("Split composite meals into individual food items"))
        assertTrue(promptSource.contains("Classify the whole meal into exactly one category"))
        assertTrue(promptSource.contains("Generate a short, natural title"))
        assertTrue(promptSource.contains("Always reply in German"))
        assertTrue(promptSource.contains("LLMParams(schema = outputStructure.schema)"))
        assertTrue(promptSource.contains("Estimate this meal from the attached photo"))
        assertTrue(promptSource.contains("imageAttachments.forEach { image(it.image) }"))
        assertTrue(promptSource.contains("imageAttachments: List<GalleryImageAttachment> = emptyList()"))

        assertTrue(dtoSource.contains("A structured nutrition estimate"))
        assertTrue(dtoSource.contains("source must be a concrete http/https food-entry page URL"))
        assertTrue(dtoSource.contains("A short, natural-language title for the whole meal"))
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
    fun roomDatabaseRegistersMigrationsIncludingV6ActivitiesTable() {
        val databaseSource = productionSource("VocalorieDatabase.java")
        val entitySource = productionSource("MealEntity.kt")

        assertTrue(databaseSource.contains("version = 10"))
        assertTrue(databaseSource.contains("Migration(1, 2)"))
        assertTrue(databaseSource.contains("Migration(2, 3)"))
        assertTrue(databaseSource.contains("Migration(3, 4)"))
        assertTrue(databaseSource.contains("MIGRATION_4_5"))
        assertTrue(databaseSource.contains("MIGRATION_5_6"))
        assertTrue(databaseSource.contains("MIGRATION_6_7"))
        assertTrue(databaseSource.contains("MIGRATION_7_8"))
        assertTrue(databaseSource.contains("MIGRATION_8_9"))
        assertTrue(databaseSource.contains("MIGRATION_9_10"))
        assertTrue(databaseSource.contains("ALTER TABLE meals ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'"))
        assertTrue(databaseSource.contains("ALTER TABLE cached_meals ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'"))
        assertTrue(databaseSource.contains("CREATE TABLE cached_meals"))
        assertTrue(databaseSource.contains("CREATE TABLE cached_items"))
        assertTrue(databaseSource.contains("ALTER TABLE activities ADD COLUMN stepsCount INTEGER"))
        assertTrue(databaseSource.contains("ALTER TABLE meals ADD COLUMN saturatedFatG REAL"))
        assertTrue(databaseSource.contains("ALTER TABLE meals ADD COLUMN sugarG REAL"))
        assertTrue(databaseSource.contains("ALTER TABLE meals ADD COLUMN saltG REAL"))
        assertTrue(databaseSource.contains("ALTER TABLE meals ADD COLUMN amountGml REAL"))
        assertTrue(databaseSource.contains("ALTER TABLE meals ADD COLUMN title TEXT NOT NULL DEFAULT ''"))
        assertTrue(databaseSource.contains("CREATE TABLE activities"))
        assertFalse(databaseSource.contains("fallbackToDestructiveMigration"))

        assertTrue(entitySource.contains("val title: String"))
        assertTrue(entitySource.contains("val amountGml: Double?"))
        assertTrue(entitySource.contains("val saturatedFatG: Double?"))
        assertTrue(entitySource.contains("val sugarG: Double?"))
        assertTrue(entitySource.contains("val saltG: Double?"))
        assertTrue(entitySource.contains("val category: String"))
    }

    @Test
    fun koogPromptUsesStructuredJsonSchemaAndInlineImageAttachment() {
        val promptSource = productionSource("KoogNutritionAgent.kt")
        // The model-choice mapping lives in ToolSettings.OpenAiModelChoice.model; the agent reads it
        // instead of duplicating the `when`.
        val modelChoiceSource = productionSource("ToolSettings.kt")

        assertTrue(modelChoiceSource.contains("GPT54MINI"))
        assertTrue(modelChoiceSource.contains("GPT5_4Mini"))
        assertTrue(promptSource.contains("toolSettings.openAiModelChoice.model"))
        assertTrue(promptSource.contains("prompt(\"vocalorie-nutrition-estimate\""))
        assertTrue(promptSource.contains("LLMParams(schema = outputStructure.schema)"))
        assertTrue(promptSource.contains("image(it.image)"))
    }

}
