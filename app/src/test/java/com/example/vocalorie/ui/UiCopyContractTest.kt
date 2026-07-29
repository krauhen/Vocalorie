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
        val mealEntries = mealEntriesSources()

        assertTrue(mealEditor.contains("Amount (g/ml)"))
        assertTrue(mealEntries.contains("Amount"))
        assertTrue(mealEntries.contains("g/ml"))
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
            "MealEntryOverlay.kt",
        ).joinToString("\n") { productionSource(it) } + "\n" + mealEntriesSources()

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
        // The meal text used to be a `mutableStateOf("")` inside the screen composable; it is a field
        // of the capture flow's immutable state now, and its default is what "starts empty" means.
        val uiState = productionSource("MealCaptureUiState.kt")
        val captureFlow = listOf(
            "MealCaptureUiState.kt",
            "MealCaptureViewModel.kt",
            "MealCaptureScreen.kt",
        ).joinToString("\n") { productionSource(it) }

        assertTrue(uiState.contains("val query: String = \"\""))
        assertFalse(captureFlow.contains("Estimate calories and macros for 2 eggs and 1 banana"))
    }

    @Test
    fun dayNavigationUsesExplicitButtonsWithoutChevronInstruction() {
        val dayNavigator = productionSource("MealEntriesDayNavigator.kt")

        assertTrue(dayNavigator.contains("Previous day"))
        assertTrue(dayNavigator.contains("Next day"))
        assertTrue(dayNavigator.contains("Today"))
        assertFalse(mealEntriesSources().contains("Use chevrons to browse days"))
    }

    @Test
    fun emptyEntriesCopyDistinguishesNoSavedEntriesFromEmptyWindow() {
        val mealEntriesScreen = productionSource("MealEntriesScreen.kt")
        val mealEntriesRows = productionSource("MealEntriesRows.kt")

        assertTrue(mealEntriesScreen.contains("No saved meals yet"))
        assertTrue(mealEntriesScreen.contains("No saved activities yet"))
        assertTrue(mealEntriesRows.contains("No entries in this time window"))
    }

    @Test
    fun mealEntryActionUsesAddMealCopyInsteadOfMicLabel() {
        val mealEntriesScreen = productionSource("MealEntriesScreen.kt")
        val mealEntries = mealEntriesSources()
        val voiceInputOverlay = productionSource("VoiceInputOverlay.kt")
        val combined = mealEntries + "\n" + voiceInputOverlay

        assertTrue(combined.contains("Add"))
        assertTrue(mealEntriesScreen.contains("Meals"))
        assertTrue(mealEntriesScreen.contains("Activities"))
        assertTrue(combined.contains("Text(if (keepListening) \"Stop\" else \"Voice\")"))
        assertTrue(combined.contains("Text(\"Photo\")"))
        assertFalse(mealEntries.contains("use the mic"))
        assertFalse(mealEntries.contains("round mic button"))
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
        // Reset used to clear nine `mutableStateOf` slots inside the screen composable; it clears the
        // same nine fields of the immutable capture state in the state holder now.
        val viewModel = productionSource("MealCaptureViewModel.kt")
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
        assertTrue(viewModel.contains("fun resetNewMealEstimate()"))
        assertTrue(viewModel.contains("query = \"\""))
        assertTrue(viewModel.contains("searchQuery = \"\""))
        assertTrue(viewModel.contains("draft = null"))
        assertTrue(viewModel.contains("error = null"))
        assertTrue(viewModel.contains("diagnostic = null"))
        assertTrue(viewModel.contains("groundingWarning = null"))
        assertTrue(viewModel.contains("saveMessage = null"))
        assertTrue(viewModel.contains("attachedImages = emptyList()"))
        assertTrue(viewModel.contains("resetSignal = it.resetSignal + 1"))
        assertTrue(mealCaptureScreen.contains("onReset = viewModel::resetNewMealEstimate"))
    }

    @Test
    fun mealEntriesAndMealCaptureUiDoNotShowTheOldTapAnEntryHelperSentence() {
        val sources = listOf(
            "MealCaptureScreen.kt",
            "MealCaptureUiState.kt",
            "MealCaptureViewModel.kt",
            "CachedMealApprovalDialog.kt",
            "VoiceInputOverlay.kt",
        ).joinToString("\n") { productionSource(it) } + "\n" + mealEntriesSources()

        assertFalse(sources.contains("Tap an entry to inspect ..."))
    }

    @Test
    fun savedEntriesUseOneSelectableStatsHeader() {
        val mealEntriesScreen = productionSource("MealEntriesScreen.kt")
        val mealTimeWindows = productionSource("MealTimeWindows.kt")

        assertTrue(mealEntriesScreen.contains("SelectableStatsHeader"))
        assertTrue(mealEntriesScreen.contains("StatsWindowSelectorDialog"))
        assertTrue(mealTimeWindows.contains("\"Since 00:00\""))
        assertTrue(mealTimeWindows.contains("\"Last 24h\""))
        assertTrue(mealTimeWindows.contains("\"Custom\""))
        assertFalse(mealEntriesSources().contains("TimelineStatsCards"))
        assertFalse(mealEntriesSources().contains("RollingStatsDurationDialog"))
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

    /**
     * The entries screen is split across cohesive files in `ui/entries`. Copy assertions that are
     * not tied to one specific file read the whole family, so moving a composable between these
     * files cannot make an `assertFalse` pass vacuously. `productionSource` fails loudly when a
     * name here stops resolving, so a rename shows up as an error rather than as silent coverage loss.
     */
    private fun mealEntriesSources(): String = listOf(
        "MealEntriesScreen.kt",
        "MealEntriesDayNavigator.kt",
        "MealEntriesStatsHeader.kt",
        "MealEntriesCharts.kt",
        "MealEntriesDialogs.kt",
        "MealEntriesRows.kt",
        "MealEntriesActionButtons.kt",
    ).joinToString("\n") { productionSource(it) }
}
