package com.example.vocalorie.ui

import com.example.vocalorie.testsupport.productionSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiCopyContractTest {
    @Test
    fun mealEditorDoesNotExposeConfidenceReasoningAssumptionsOrWarningsFields() {
        val mealEditor = productionSource("MealEditor.kt")
        val entryOverlay = productionSource("MealEntryOverlay.kt")
        val combined = mealEditor + "\n" + entryOverlay

        listOf("Confidence", "Reasoning", "Assumptions", "Warnings").forEach { label ->
            assertFalse("UI should not expose $label", combined.contains(label))
        }
    }

    @Test
    fun mealUiExposesAmountGmlForEditingAndStats() {
        val mealEditor = productionSource("MealEditor.kt")
        val mealEntriesScreen = productionSource("MealEntriesScreen.kt")

        assertTrue(mealEditor.contains("Amount (g/ml)"))
        assertTrue(mealEntriesScreen.contains("Amount"))
        assertTrue(mealEntriesScreen.contains("g/ml"))
    }

    @Test
    fun mealEditorShowsComputedTotalsAndPortionScalingControls() {
        val mealEditor = productionSource("MealEditor.kt")

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
            "CommonUi.kt",
            "MealEditor.kt",
            "MealEntriesScreen.kt",
            "MealEntryOverlay.kt",
        ).joinToString("\n") { productionSource(it) }

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
    fun mealCaptureScreenStartsWithEmptyMealText() {
        val mealCaptureScreen = productionSource("MealCaptureScreen.kt")

        assertTrue(mealCaptureScreen.contains("mutableStateOf(\"\")"))
        assertFalse(mealCaptureScreen.contains("Estimate calories and macros for 2 eggs and 1 banana"))
    }

    @Test
    fun dayNavigationUsesExplicitButtonsWithoutChevronInstruction() {
        val mealEntriesScreen = productionSource("MealEntriesScreen.kt")

        assertTrue(mealEntriesScreen.contains("Previous day"))
        assertTrue(mealEntriesScreen.contains("Next day"))
        assertTrue(mealEntriesScreen.contains("Today"))
        assertFalse(mealEntriesScreen.contains("Use chevrons to browse days"))
    }

    @Test
    fun emptyEntriesCopyDistinguishesNoSavedEntriesFromEmptyWindow() {
        val mealEntriesScreen = productionSource("MealEntriesScreen.kt")

        assertTrue(mealEntriesScreen.contains("No saved meals yet"))
        assertTrue(mealEntriesScreen.contains("No saved activities yet"))
        assertTrue(mealEntriesScreen.contains("No entries in this time window"))
    }

    @Test
    fun mealEntryActionUsesAddMealCopyInsteadOfMicLabel() {
        val mealEntriesScreen = productionSource("MealEntriesScreen.kt")
        val voiceInputOverlay = productionSource("VoiceInputOverlay.kt")
        val combined = mealEntriesScreen + "\n" + voiceInputOverlay

        assertTrue(combined.contains("Add"))
        assertTrue(mealEntriesScreen.contains("Meals"))
        assertTrue(mealEntriesScreen.contains("Activities"))
        assertTrue(combined.contains("Text(if (keepListening) \"Stop\" else \"Voice\")"))
        assertTrue(combined.contains("Text(\"Photo\")"))
        assertFalse(mealEntriesScreen.contains("use the mic"))
        assertFalse(mealEntriesScreen.contains("round mic button"))
    }

    @Test
    fun newMealEstimateLoadingDoesNotExposeTechnicalProviderNames() {
        val voiceInputOverlay = productionSource("VoiceInputOverlay.kt")
        val technicalLoadingCopy = "Calling Koog" + " + OpenAI"

        assertTrue(voiceInputOverlay.contains("LoadingRow(\"Estimating…\")"))
        assertFalse(voiceInputOverlay.contains(technicalLoadingCopy))
    }

    @Test
    fun newMealEstimateExposesResetActionThatClearsTransientDraftState() {
        val mealCaptureScreen = productionSource("MealCaptureScreen.kt")
        val voiceInputOverlay = productionSource("VoiceInputOverlay.kt")

        assertTrue(voiceInputOverlay.contains("onReset: () -> Unit"))
        assertTrue(voiceInputOverlay.contains("resetSignal: Int"))
        assertTrue(voiceInputOverlay.contains("LaunchedEffect(resetSignal)"))
        assertTrue(voiceInputOverlay.contains("voiceMessage = null"))
        assertTrue(voiceInputOverlay.contains("Text(\"Reset\")"))
        assertTrue(voiceInputOverlay.contains("Text(\"Save entry\")"))
        assertTrue(voiceInputOverlay.contains("Text(if (error == null) \"Estimate\" else \"Retry\")"))
        assertTrue(voiceInputOverlay.contains("Text(\"Photo\")"))
        assertTrue(voiceInputOverlay.contains("query.isNotBlank() || draft != null || attachedImages.isNotEmpty()"))
        assertTrue(mealCaptureScreen.contains("fun resetNewMealEstimate()"))
        assertTrue(mealCaptureScreen.contains("query = \"\""))
        assertTrue(mealCaptureScreen.contains("draft = null"))
        assertTrue(mealCaptureScreen.contains("error = null"))
        assertTrue(mealCaptureScreen.contains("diagnostic = null"))
        assertTrue(mealCaptureScreen.contains("saveMessage = null"))
        assertTrue(mealCaptureScreen.contains("attachedImages = emptyList()"))
        assertTrue(mealCaptureScreen.contains("resetSignal += 1"))
        assertTrue(mealCaptureScreen.contains("onReset = { resetNewMealEstimate() }"))
    }

    @Test
    fun mealEntriesAndMealCaptureUiDoNotShowTheOldTapAnEntryHelperSentence() {
        val sources = listOf(
            "MealEntriesScreen.kt",
            "MealCaptureScreen.kt",
            "VoiceInputOverlay.kt",
        ).joinToString("\n") { productionSource(it) }

        assertFalse(sources.contains("Tap an entry to inspect ..."))
    }

    @Test
    fun savedEntriesUseOneSelectableStatsHeader() {
        val mealEntriesScreen = productionSource("MealEntriesScreen.kt")

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
        val mealEditor = productionSource("MealEditor.kt")
        val entryOverlay = productionSource("MealEntryOverlay.kt")
        val commonUi = productionSource("CommonUi.kt")
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
        val mealEditor = productionSource("MealEditor.kt")
        val entryOverlay = productionSource("MealEntryOverlay.kt")
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
        val entryOverlay = productionSource("MealEntryOverlay.kt")

        assertTrue(entryOverlay.contains("Text(\"Delete\")"))
        assertTrue(entryOverlay.contains("Text(\"Save\")"))
        assertTrue(entryOverlay.contains("Text(if (isEditing) \"Cancel\" else \"Close\")"))
        assertTrue(entryOverlay.contains("actionLabel = null"))
        assertTrue(entryOverlay.contains("onAction = null"))
    }

    @Test
    fun settingsUiExposesResearchToolLimitAndAdvancedAgentFuse() {
        val settingsScreen = productionSource("SettingsScreen.kt")

        assertTrue(settingsScreen.contains("Max research tool calls"))
        assertTrue(settingsScreen.contains("actual Brave/WebFetch calls"))
        assertTrue(settingsScreen.contains("Agent workflow step limit"))
        assertTrue(settingsScreen.contains("advanced internal safety fuse"))
        assertTrue(settingsScreen.contains("Allowed range:"))
        assertTrue(settingsScreen.contains("Save research limit"))
        assertTrue(settingsScreen.contains("KeyboardType.Number"))
    }
}
