package com.example.vocalorie.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiCopyContractTest {
    @Test
    fun mealEditorDoesNotExposeConfidenceReasoningAssumptionsOrWarningsFields() {
        val mealEditor = source("app/src/main/java/com/example/vocalorie/ui/components/MealEditor.kt")
        val entryOverlay = source("app/src/main/java/com/example/vocalorie/ui/entries/MealEntryOverlay.kt")
        val combined = mealEditor + "\n" + entryOverlay

        listOf("Confidence", "Reasoning", "Assumptions", "Warnings").forEach { label ->
            assertFalse("UI should not expose $label", combined.contains(label))
        }
    }

    @Test
    fun mealEditorLabelsSourceAsSourceUrl() {
        val mealEditor = source("app/src/main/java/com/example/vocalorie/ui/components/MealEditor.kt")

        assertTrue(mealEditor.contains("Source URL"))
        assertTrue(mealEditor.contains("KeyboardType.Uri"))
        assertTrue(mealEditor.contains("SourceUrlRow(\"Source URL\", source)"))
        assertFalse(mealEditor.contains("Text(\"Source\")"))
        assertFalse(mealEditor.contains("NutritionRow(\"Source\""))
    }

    @Test
    fun mealUiExposesAmountGmlForEditingAndStats() {
        val mealEditor = source("app/src/main/java/com/example/vocalorie/ui/components/MealEditor.kt")
        val mealEntriesScreen = source("app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt")

        assertTrue(mealEditor.contains("Amount (g/ml)"))
        assertTrue(mealEntriesScreen.contains("Amount"))
        assertTrue(mealEntriesScreen.contains("g/ml"))
    }

    @Test
    fun mealEditorShowsComputedTotalsAndPortionScalingControls() {
        val mealEditor = source("app/src/main/java/com/example/vocalorie/ui/components/MealEditor.kt")

        assertTrue(mealEditor.contains("Meal title"))
        assertTrue(mealEditor.contains("Recipe makes"))
        assertTrue(mealEditor.contains("I ate"))
        assertTrue(mealEditor.contains("Apply portion"))
        assertTrue(mealEditor.contains("¼"))
        assertTrue(mealEditor.contains("½"))
        assertTrue(mealEditor.contains("¾"))
        assertTrue(mealEditor.contains("All"))
    }

    @Test
    fun savedEntryUiDoesNotExposeReviewStatusOrBorderEmphasisCopy() {
        val sources = listOf(
            "app/src/main/java/com/example/vocalorie/ui/components/CommonUi.kt",
            "app/src/main/java/com/example/vocalorie/ui/components/MealEditor.kt",
            "app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt",
            "app/src/main/java/com/example/vocalorie/ui/entries/MealEntryOverlay.kt",
        ).joinToString("\n") { source(it) }

        listOf(
            "Needs macro check",
            "Needs human review",
            "Double border",
            "Soft border",
            "Solid accent",
            "\"Saved meal\"",
        ).forEach { label ->
            assertFalse("Saved entry UI should not expose $label", sources.contains(label))
        }
    }

    @Test
    fun spikeScreenStartsWithEmptyMealText() {
        val spikeScreen = source("app/src/main/java/com/example/vocalorie/ui/SpikeScreen.kt")

        assertTrue(spikeScreen.contains("mutableStateOf(\"\")"))
        assertFalse(spikeScreen.contains("Estimate calories and macros for 2 eggs and 1 banana"))
    }

    @Test
    fun dayNavigationUsesExplicitButtonsWithoutChevronInstruction() {
        val mealEntriesScreen = source("app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt")

        assertTrue(mealEntriesScreen.contains("Previous day"))
        assertTrue(mealEntriesScreen.contains("Next day"))
        assertTrue(mealEntriesScreen.contains("Today"))
        assertFalse(mealEntriesScreen.contains("Use chevrons to browse days"))
    }

    @Test
    fun emptyEntriesCopyDistinguishesNoSavedEntriesFromEmptyWindow() {
        val mealEntriesScreen = source("app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt")

        assertTrue(mealEntriesScreen.contains("No saved entries yet"))
        assertTrue(mealEntriesScreen.contains("No entries in this time window"))
    }

    @Test
    fun mealEntryActionUsesAddMealCopyInsteadOfMicLabel() {
        val mealEntriesScreen = source("app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt")
        val voiceInputOverlay = source("app/src/main/java/com/example/vocalorie/ui/voice/VoiceInputOverlay.kt")
        val combined = mealEntriesScreen + "\n" + voiceInputOverlay

        assertTrue(combined.contains("Add meal"))
        assertTrue(combined.contains("Text(if (keepListening) \"Stop\" else \"Voice\")"))
        assertTrue(combined.contains("Text(\"Photo\")"))
        assertFalse(mealEntriesScreen.contains("use the mic"))
        assertFalse(mealEntriesScreen.contains("round mic button"))
    }

    @Test
    fun newMealEstimateLoadingDoesNotExposeTechnicalProviderNames() {
        val voiceInputOverlay = source("app/src/main/java/com/example/vocalorie/ui/voice/VoiceInputOverlay.kt")
        val technicalLoadingCopy = "Calling Koog" + " + OpenAI"

        assertTrue(voiceInputOverlay.contains("LoadingRow(\"Estimating…\")"))
        assertFalse(voiceInputOverlay.contains(technicalLoadingCopy))
    }

    @Test
    fun newMealEstimateExposesResetActionThatClearsTransientDraftState() {
        val spikeScreen = source("app/src/main/java/com/example/vocalorie/ui/SpikeScreen.kt")
        val voiceInputOverlay = source("app/src/main/java/com/example/vocalorie/ui/voice/VoiceInputOverlay.kt")

        assertTrue(voiceInputOverlay.contains("onReset: () -> Unit"))
        assertTrue(voiceInputOverlay.contains("resetSignal: Int"))
        assertTrue(voiceInputOverlay.contains("LaunchedEffect(resetSignal)"))
        assertTrue(voiceInputOverlay.contains("voiceMessage = null"))
        assertTrue(voiceInputOverlay.contains("Text(\"Reset\")"))
        assertTrue(voiceInputOverlay.contains("Text(\"Save entry\")"))
        assertTrue(voiceInputOverlay.contains("Text(if (error == null) \"Estimate\" else \"Retry\")"))
        assertTrue(voiceInputOverlay.contains("Text(\"Photo\")"))
        assertTrue(voiceInputOverlay.contains("query.isNotBlank() || draft != null || attachedImages.isNotEmpty()"))
        assertTrue(spikeScreen.contains("fun resetNewMealEstimate()"))
        assertTrue(spikeScreen.contains("query = \"\""))
        assertTrue(spikeScreen.contains("draft = null"))
        assertTrue(spikeScreen.contains("error = null"))
        assertTrue(spikeScreen.contains("diagnostic = null"))
        assertTrue(spikeScreen.contains("saveMessage = null"))
        assertTrue(spikeScreen.contains("attachedImages = emptyList()"))
        assertTrue(spikeScreen.contains("resetSignal += 1"))
        assertTrue(spikeScreen.contains("onReset = { resetNewMealEstimate() }"))
    }

    @Test
    fun mealEntriesAndSpikeUiDoNotShowTheOldTapAnEntryHelperSentence() {
        val sources = listOf(
            "app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt",
            "app/src/main/java/com/example/vocalorie/ui/SpikeScreen.kt",
            "app/src/main/java/com/example/vocalorie/ui/voice/VoiceInputOverlay.kt",
        ).joinToString("\n") { source(it) }

        assertFalse(sources.contains("Tap an entry to inspect ..."))
    }

    @Test
    fun savedEntriesUseOneSelectableStatsHeader() {
        val mealEntriesScreen = source("app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt")

        assertTrue(mealEntriesScreen.contains("SelectableStatsHeader"))
        assertTrue(mealEntriesScreen.contains("StatsWindowSelectorDialog"))
        assertTrue(mealEntriesScreen.contains("Since 00:00"))
        assertTrue(mealEntriesScreen.contains("Last 24h"))
        assertTrue(mealEntriesScreen.contains("Custom"))
        assertFalse(mealEntriesScreen.contains("TimelineStatsCards"))
        assertFalse(mealEntriesScreen.contains("RollingStatsDurationDialog"))
    }

    @Test
    fun savedMealDetailsExposeAddedAtAndEditableAddedDateTime() {
        val mealEditor = source("app/src/main/java/com/example/vocalorie/ui/components/MealEditor.kt")
        val entryOverlay = source("app/src/main/java/com/example/vocalorie/ui/entries/MealEntryOverlay.kt")
        val commonUi = source("app/src/main/java/com/example/vocalorie/ui/components/CommonUi.kt")
        val combined = mealEditor + "\n" + entryOverlay + "\n" + commonUi

        assertTrue(combined.contains("Meal title"))
        assertTrue(combined.contains("Added at"))
        assertTrue(combined.contains("Added date/time"))
        assertTrue(combined.contains("yyyy-MM-dd HH:mm"))
        assertTrue(entryOverlay.contains("actionLabel = \"Save\""))
        assertTrue(entryOverlay.contains("actionEnabled = isCreatedAtValid"))
    }

    @Test
    fun savedMealDetailsExposeCollapsibleItemsInReadOnlyMode() {
        val mealEditor = source("app/src/main/java/com/example/vocalorie/ui/components/MealEditor.kt")
        val entryOverlay = source("app/src/main/java/com/example/vocalorie/ui/entries/MealEntryOverlay.kt")
        val combined = mealEditor + "\n" + entryOverlay

        assertTrue(entryOverlay.contains("ReadOnlyMealSummary("))
        assertTrue(entryOverlay.contains("EditableMealEditor("))
        assertTrue(mealEditor.contains("fun ReadOnlyMealSummary("))
        assertTrue(mealEditor.contains("items: List<FoodItemEstimate>"))
        assertTrue(mealEditor.contains("items.isNotEmpty()"))
        assertTrue(mealEditor.contains("SectionTitle(\"Items\")"))
        assertTrue(mealEditor.contains("Text(if (itemsExpanded) \"Hide\" else \"Show\")"))
        assertTrue(mealEditor.contains("ReadOnlyFoodItemCard(item = item)"))
        assertTrue(entryOverlay.contains("items = meal.items"))
        assertFalse(combined.contains("Reasoning"))
    }

    @Test
    fun editingEntryPlacesSaveBesideDeleteAndCancel() {
        val entryOverlay = source("app/src/main/java/com/example/vocalorie/ui/entries/MealEntryOverlay.kt")

        assertTrue(entryOverlay.contains("Text(\"Delete\")"))
        assertTrue(entryOverlay.contains("Text(\"Save\")"))
        assertTrue(entryOverlay.contains("Text(if (isEditing) \"Cancel\" else \"Close\")"))
        assertTrue(entryOverlay.contains("if (isEditing) {\n                    TextButton(onClick = { onSave(draft) { isEditing = false } }, enabled = enabled && isCreatedAtValid) { Text(\"Save\") }"))
        assertTrue(entryOverlay.contains("actionLabel = null"))
        assertTrue(entryOverlay.contains("onAction = null"))
    }

    @Test
    fun settingsUiExposesResearchToolLimitAndAdvancedAgentFuse() {
        val settingsScreen = source("app/src/main/java/com/example/vocalorie/ui/settings/SettingsScreen.kt")

        assertTrue(settingsScreen.contains("Max research tool calls"))
        assertTrue(settingsScreen.contains("actual Brave/WebFetch calls"))
        assertTrue(settingsScreen.contains("Agent workflow step limit"))
        assertTrue(settingsScreen.contains("advanced internal safety fuse"))
        assertTrue(settingsScreen.contains("Allowed range:"))
        assertTrue(settingsScreen.contains("Save research limit"))
        assertTrue(settingsScreen.contains("KeyboardType.Number"))
    }

    private fun source(path: String): String = File(path).takeIf { it.exists() }
        ?.readText()
        ?: File("../$path").readText()
}
