package com.example.vocalorie.ai

import ai.koog.prompt.message.AttachmentContent
import ai.koog.prompt.message.AttachmentSource
import com.example.vocalorie.settings.ToolSettings
import com.example.vocalorie.ui.voice.GalleryImageAttachment
import java.io.File
import java.util.Properties
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class KoogNutritionSpikeLiveHarnessTest {
    @Test
    fun liveOpenAiEstimateFor100gPickleImage() = runBlocking {
        val apiKey = loadOpenAiApiKey()
        val imageBytes = loadLocalImageBytes()
        val attachment = GalleryImageAttachment(
            label = "pickle.jpg",
            image = AttachmentSource.Image(
                content = AttachmentContent.Binary.Bytes(imageBytes),
                format = "jpg",
                mimeType = "image/jpeg",
                fileName = "pickle.jpg",
            ),
        )

        val result = KoogNutritionSpike.estimate(
            openAiApiKey = apiKey,
            query = QUERY,
            toolSettings = ToolSettings(),
            imageAttachment = attachment,
        )

        printEstimate(result)
        assertTrue(result.needsHumanReview)
    }

    private fun loadLocalImageBytes(): ByteArray = File(LOCAL_PICKLE_IMAGE_PATH).readBytes()

    private fun loadOpenAiApiKey(): String {
        val props = Properties()
        val file = sequenceOf(
            java.io.File("local.properties"),
            java.io.File("../local.properties"),
            java.io.File("../../local.properties"),
        ).firstOrNull { it.exists() } ?: error("local.properties not found")
        file.inputStream().use { props.load(it) }
        return props.getProperty("openai.api.key")?.trim().orEmpty().also {
            require(it.isNotBlank()) { "openai.api.key missing from local.properties" }
        }
    }

    private fun printEstimate(result: com.example.vocalorie.model.NutritionSpikeResult) {
        println(
            buildString {
                appendLine("query=${result.query}")
                appendLine("confidence=${result.confidence}; needsHumanReview=${result.needsHumanReview}")
                appendLine("warnings=${result.warnings.joinToString(" | ")}")
                appendLine("assumptions=${result.assumptions.joinToString(" | ")}")
                appendLine("totals: calories=${result.totals.caloriesKcal}, amount=${result.totals.amountGml}, protein=${result.totals.proteinG}, carbs=${result.totals.carbsG}, fat=${result.totals.fatG}, satFat=${result.totals.saturatedFatG}, sugar=${result.totals.sugarG}, salt=${result.totals.saltG}")
                result.items.forEachIndexed { index, item ->
                    appendLine(
                        "item[$index]: name=${item.name}; quantity=${item.quantity}; amount=${item.amountGml}; calories=${item.caloriesKcal}; protein=${item.proteinG}; carbs=${item.carbsG}; fat=${item.fatG}; satFat=${item.saturatedFatG}; sugar=${item.sugarG}; salt=${item.saltG}; source=${item.source}",
                    )
                }
            },
        )
    }

    private companion object {
        const val QUERY = "100g"
        const val LOCAL_PICKLE_IMAGE_PATH = "/Users/use/Downloads/pickles.jpg"
    }
}
