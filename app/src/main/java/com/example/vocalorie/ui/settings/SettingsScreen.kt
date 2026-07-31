package com.example.vocalorie.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.vocalorie.ai.KoogNutritionAgent
import com.example.vocalorie.settings.OpenAiModelChoice
import com.example.vocalorie.settings.ToolSettings
import com.example.vocalorie.settings.ToolSettingsLabels
import com.example.vocalorie.ui.components.SectionTitle
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

/** The three appearance cards, in render order, as (slot, row label) pairs. */
private val SHARED_SURFACE_SLOTS = listOf(
    ThemeColorSlot.BACKGROUND to "Background",
    ThemeColorSlot.SURFACE to "Surface",
    ThemeColorSlot.SURFACE_VARIANT to "Surface variant",
)

private val MEAL_PALETTE_SLOTS = listOf(
    ThemeColorSlot.MEAL_PRIMARY to "Primary",
    ThemeColorSlot.MEAL_SECONDARY to "Secondary",
    ThemeColorSlot.MEAL_ACCENT to "Accent",
    ThemeColorSlot.MEAL_OUTLINE to "Outline",
)

private val ACTIVITY_PALETTE_SLOTS = listOf(
    ThemeColorSlot.ACTIVITY_PRIMARY to "Primary",
    ThemeColorSlot.ACTIVITY_SECONDARY to "Secondary",
    ThemeColorSlot.ACTIVITY_ACCENT to "Accent",
    ThemeColorSlot.ACTIVITY_OUTLINE to "Outline",
)

/**
 * The settings screen: it renders [SettingsUiState] and emits [SettingsEvent]s.
 *
 * Holds only the transient text-field and dialog state that belongs to a text field or a dialog.
 * Every value it shows and every decision it triggers comes from outside.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹") } },
            )
        },
    ) { padding ->
        SettingsContent(padding = padding, state = state, onEvent = onEvent)
    }
}

@Composable
private fun SettingsContent(
    padding: PaddingValues,
    state: SettingsUiState,
    onEvent: (SettingsEvent) -> Unit,
) {
    val enabled = state.enabled
    val toolSettings = state.toolSettings

    // A picked colour shows immediately rather than waiting for the save to round-trip through the
    // preference store and arrive back on `state`; the overrides are dropped as soon as it does.
    val pickedColors = remember(state.mealColors, state.activityColors) {
        mutableStateMapOf<ThemeColorSlot, Color>()
    }
    val colorOf: (ThemeColorSlot) -> Color = { slot -> pickedColors[slot] ?: state.color(slot) }
    val onColorPicked: (ThemeColorSlot, Color) -> Unit = { slot, color ->
        pickedColors[slot] = color
        onEvent(SettingsEvent.SaveColor(slot, color))
    }

    var baseCaloriesBurnedInput by remember(state.baseCaloriesBurned) {
        mutableStateOf(state.baseCaloriesBurned.toString())
    }
    // Edited as kcal per 1,000 steps (friendlier than tiny per-step decimals).
    var kcalPer1000StepsInput by remember(state.kcalPerStep) {
        mutableStateOf((state.kcalPerStep * 1000).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() })
    }
    var tipRotationSecondsInput by remember(state.tipRotationSeconds) {
        mutableStateOf(state.tipRotationSeconds.toString())
    }
    var calorieGoalInput by remember(state.nutritionGoals.calorieGoalKcal) {
        mutableStateOf(state.nutritionGoals.calorieGoalKcal.toString())
    }
    var proteinPercentInput by remember(state.nutritionGoals.proteinPercent) {
        mutableStateOf(state.nutritionGoals.proteinPercent.toString())
    }
    var carbsPercentInput by remember(state.nutritionGoals.carbsPercent) {
        mutableStateOf(state.nutritionGoals.carbsPercent.toString())
    }
    var newApiKey by remember { mutableStateOf("") }
    var newBraveApiKey by remember { mutableStateOf("") }
    var maxResearchToolCallsInput by remember { mutableStateOf(toolSettings.maxResearchToolCalls.toString()) }
    var maxAgentIterationsInput by remember { mutableStateOf(toolSettings.maxAgentIterations.toString()) }
    var showModelDialog by remember { mutableStateOf(false) }
    var pendingModelName by rememberSaveable(toolSettings.openAiModelChoiceName) {
        mutableStateOf(toolSettings.openAiModelChoiceName)
    }
    var systemPromptInput by remember(toolSettings.systemPromptOverride) {
        mutableStateOf(toolSettings.systemPromptOverride ?: KoogNutritionAgent.DEFAULT_SYSTEM_PROMPT)
    }

    LaunchedEffect(toolSettings.maxResearchToolCalls) {
        maxResearchToolCallsInput = toolSettings.maxResearchToolCalls.toString()
    }

    LaunchedEffect(toolSettings.maxAgentIterations) {
        maxAgentIterationsInput = toolSettings.maxAgentIterations.toString()
    }

    LaunchedEffect(toolSettings.openAiModelChoiceName) {
        pendingModelName = toolSettings.openAiModelChoiceName
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle("Appearance")
        PaletteCard(SHARED_SURFACE_SLOTS, enabled, colorOf, onColorPicked)

        SectionTitle("Meal Appearance")
        PaletteCard(MEAL_PALETTE_SLOTS, enabled, colorOf, onColorPicked)

        SectionTitle("Activity Appearance")
        PaletteCard(ACTIVITY_PALETTE_SLOTS, enabled, colorOf, onColorPicked)

        SectionTitle("Energy baseline")
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Base calories burned per day") },
                supportingContent = { Text("Current: ${state.baseCaloriesBurned} kcal") },
            )
            OutlinedTextField(
                value = baseCaloriesBurnedInput,
                onValueChange = { baseCaloriesBurnedInput = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Base calories burned per day") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = enabled,
            )
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onEvent(SettingsEvent.SaveBaseCaloriesBurned(baseCaloriesBurnedInput)) },
                    enabled = enabled && baseCaloriesBurnedInput.isNotBlank(),
                ) { Text("Save base burn") }
            }
            ListItem(
                headlineContent = { Text("Day-score tip rotation") },
                supportingContent = {
                    Text(
                        if (state.tipRotationSeconds == 0) {
                            "Current: no rotation — top tip only"
                        } else {
                            "Current: every ${state.tipRotationSeconds} s"
                        },
                    )
                },
            )
            OutlinedTextField(
                value = tipRotationSecondsInput,
                onValueChange = { tipRotationSecondsInput = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Tip rotation seconds (0 = no rotation, else 2–60)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = enabled,
            )
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onEvent(SettingsEvent.SaveTipRotationSeconds(tipRotationSecondsInput)) },
                    enabled = enabled && tipRotationSecondsInput.isNotBlank(),
                ) { Text("Save tip rotation") }
            }
            ListItem(
                headlineContent = { Text("Calories burned per 1,000 steps") },
                supportingContent = { Text("Current: ${state.kcalPerStep * 1000} kcal / 1,000 steps") },
            )
            OutlinedTextField(
                value = kcalPer1000StepsInput,
                onValueChange = { kcalPer1000StepsInput = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Calories burned per 1,000 steps (kcal)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = enabled,
            )
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onEvent(SettingsEvent.SaveKcalPer1000Steps(kcalPer1000StepsInput)) },
                    enabled = enabled && kcalPer1000StepsInput.isNotBlank(),
                ) { Text("Save step burn") }
            }
        }

        SectionTitle("Nutrition goals")
        Card(modifier = Modifier.fillMaxWidth()) {
            val proteinPct = proteinPercentInput.trim().toIntOrNull()
            val carbsPct = carbsPercentInput.trim().toIntOrNull()
            val fatPct = if (proteinPct != null && carbsPct != null) 100 - proteinPct - carbsPct else null
            val splitValid = proteinPct != null && carbsPct != null && fatPct != null &&
                proteinPct >= 0 && carbsPct >= 0 && fatPct >= 0
            ListItem(
                headlineContent = { Text("Daily calorie goal") },
                supportingContent = { Text("Current: ${state.nutritionGoals.calorieGoalKcal} kcal") },
            )
            OutlinedTextField(
                value = calorieGoalInput,
                onValueChange = { calorieGoalInput = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Daily calorie goal (kcal)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = enabled,
            )
            ListItem(
                headlineContent = { Text("Macro split") },
                supportingContent = {
                    Text(
                        "Current: ${state.nutritionGoals.proteinPercent}% protein / " +
                            "${state.nutritionGoals.carbsPercent}% carbs / ${state.nutritionGoals.fatPercent}% fat. " +
                            "Fat is derived so the split sums to 100%.",
                    )
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = proteinPercentInput,
                    onValueChange = { proteinPercentInput = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Protein %") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = enabled,
                )
                OutlinedTextField(
                    value = carbsPercentInput,
                    onValueChange = { carbsPercentInput = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Carbs %") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    enabled = enabled,
                )
                OutlinedTextField(
                    value = fatPct?.let { "$it%" } ?: "—",
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                    label = { Text("Fat % (derived)") },
                    singleLine = true,
                    readOnly = true,
                    enabled = false,
                )
            }
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onEvent(
                            SettingsEvent.SaveNutritionGoals(
                                calorieGoal = calorieGoalInput,
                                proteinPercent = proteinPercentInput,
                                carbsPercent = carbsPercentInput,
                            ),
                        )
                    },
                    enabled = enabled && calorieGoalInput.isNotBlank() && splitValid,
                ) { Text("Save goals") }
            }
        }

        SectionTitle("Backup")
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Export / import data") },
                supportingContent = {
                    Text(
                        "Export all meals, activities, and reuse caches to a JSON file (no API keys). " +
                            "Import merges a file in, skipping entries that already exist — best for restoring into a fresh install.",
                    )
                },
            )
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onEvent(SettingsEvent.ExportData) }, enabled = enabled) { Text("Export data") }
                OutlinedButton(
                    onClick = { onEvent(SettingsEvent.ImportData) },
                    enabled = enabled,
                ) { Text("Import data") }
            }
        }

        SectionTitle("OpenAI")
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Saved key") },
                supportingContent = { Text(state.savedKeyLabel ?: "No saved OpenAI API key") },
            )
            OutlinedTextField(
                value = newApiKey,
                onValueChange = { newApiKey = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("OpenAI API key to save") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = enabled,
            )
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onEvent(SettingsEvent.SaveOpenAiKey(newApiKey))
                        newApiKey = ""
                    },
                    enabled = enabled && newApiKey.isNotBlank(),
                ) { Text(if (state.savedKeyLabel == null) "Save key" else "Update key") }
                OutlinedButton(
                    onClick = { onEvent(SettingsEvent.ClearOpenAiKey) },
                    enabled = enabled && state.savedKeyLabel != null,
                ) { Text("Remove") }
            }
            Text(
                "Saved keys are encrypted with Android Keystore-backed AES-GCM and kept in local SharedPreferences excluded from Android backup.",
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionTitle("Session fallback")
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Runtime OpenAI key") },
                supportingContent = { Text("Used only when no encrypted saved key exists. Not persisted by this screen.") },
            )
            OutlinedTextField(
                value = state.runtimeApiKey,
                onValueChange = { onEvent(SettingsEvent.RuntimeApiKeyChanged(it)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 16.dp),
                label = { Text("OpenAI API key for this session") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = enabled,
            )
        }

        SectionTitle("Agent tools")
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Used AI model") },
                supportingContent = { Text(ToolSettingsLabels.openAiModelLabel(toolSettings.openAiModelChoice)) },
            )
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                OutlinedButton(onClick = { showModelDialog = true }, enabled = enabled) { Text("Change model") }
            }
            ListItem(
                headlineContent = { Text("Brave Search") },
                supportingContent = { Text(ToolSettingsLabels.braveModeLabel(toolSettings.hasBraveApiKey)) },
            )
            ListItem(
                headlineContent = { Text("WebFetch") },
                supportingContent = { Text(ToolSettingsLabels.webFetchModeLabel()) },
            )
            ListItem(
                headlineContent = { Text("Max research tool calls") },
                supportingContent = {
                    Text(
                        "Current: ${toolSettings.maxResearchToolCalls} actual Brave/WebFetch calls per estimate. " +
                            "Allowed range: ${ToolSettings.MIN_MAX_RESEARCH_TOOL_CALLS}-${ToolSettings.MAX_MAX_RESEARCH_TOOL_CALLS}; 0 disables research calls.",
                    )
                },
            )
            OutlinedTextField(
                value = maxResearchToolCallsInput,
                onValueChange = { maxResearchToolCallsInput = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Max research tool calls") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = enabled,
            )
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onEvent(SettingsEvent.SaveMaxResearchToolCalls(maxResearchToolCallsInput)) },
                    enabled = enabled && maxResearchToolCallsInput.isNotBlank(),
                ) { Text("Save research limit") }
            }
            ListItem(
                headlineContent = { Text("Agent workflow step limit") },
                supportingContent = {
                    Text(
                        "This is an advanced internal safety fuse. Current: ${toolSettings.maxAgentIterations}. " +
                            "Allowed range: ${ToolSettings.MIN_MAX_AGENT_ITERATIONS}-${ToolSettings.MAX_MAX_AGENT_ITERATIONS}.",
                    )
                },
            )
            OutlinedTextField(
                value = maxAgentIterationsInput,
                onValueChange = { maxAgentIterationsInput = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Agent workflow step limit") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = enabled,
            )
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onEvent(SettingsEvent.SaveMaxAgentIterations(maxAgentIterationsInput)) },
                    enabled = enabled && maxAgentIterationsInput.isNotBlank(),
                ) { Text("Save workflow limit") }
            }
            ListItem(
                headlineContent = { Text("Brave API key") },
                supportingContent = { Text(state.braveKeyLabel ?: "No saved Brave API key") },
            )
            OutlinedTextField(
                value = newBraveApiKey,
                onValueChange = { newBraveApiKey = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                label = { Text("Brave API key to save") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = enabled,
            )
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        onEvent(SettingsEvent.SaveBraveKey(newBraveApiKey))
                        newBraveApiKey = ""
                    },
                    enabled = enabled && newBraveApiKey.isNotBlank(),
                ) { Text(if (state.braveKeyLabel == null) "Save Brave key" else "Update Brave key") }
                OutlinedButton(
                    onClick = { onEvent(SettingsEvent.ClearBraveKey) },
                    enabled = enabled && state.braveKeyLabel != null,
                ) { Text("Remove") }
            }
        }

        SectionTitle("Prompt")
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("System prompt") },
                supportingContent = { Text("Instructions sent to the AI model before every meal estimate.") },
            )
            OutlinedTextField(
                value = systemPromptInput,
                onValueChange = { systemPromptInput = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 220.dp),
                label = { Text("System prompt") },
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Text,
                ),
                enabled = enabled,
            )
            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onEvent(SettingsEvent.SaveSystemPrompt(systemPromptInput)) },
                    enabled = enabled,
                ) { Text("Save prompt") }
                OutlinedButton(
                    onClick = {
                        onEvent(SettingsEvent.ResetSystemPrompt)
                        systemPromptInput = KoogNutritionAgent.DEFAULT_SYSTEM_PROMPT
                    },
                    enabled = enabled,
                ) { Text("Reset to default") }
            }
        }

        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
    }

    if (showModelDialog) {
        val pendingModel = OpenAiModelChoice.fromName(pendingModelName)

        AlertDialog(
            onDismissRequest = { showModelDialog = false },
            title = { Text("Used AI model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OpenAiModelChoice.values().forEach { choice ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RadioButton(selected = pendingModel == choice, onClick = { pendingModelName = choice.name })
                            Column(modifier = Modifier.weight(1f)) {
                                Text(choice.label)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onEvent(SettingsEvent.SaveOpenAiModelChoice(pendingModelName))
                    showModelDialog = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showModelDialog = false }) { Text("Cancel") } },
        )
    }
}

/** One appearance card: a colour-picker row per slot, in the given order. */
@Composable
private fun PaletteCard(
    slots: List<Pair<ThemeColorSlot, String>>,
    enabled: Boolean,
    colorOf: (ThemeColorSlot) -> Color,
    onColorPicked: (ThemeColorSlot, Color) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            slots.forEach { (slot, label) ->
                ColorPickerRow(
                    label = label,
                    color = colorOf(slot),
                    enabled = enabled,
                    onColorSelected = { onColorPicked(slot, it) },
                )
            }
        }
    }
}

@Composable
private fun ColorPickerRow(
    label: String,
    color: Color,
    enabled: Boolean,
    onColorSelected: (Color) -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }

    ListItem(
        headlineContent = { Text(label) },
        trailingContent = {
            Row(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                    .clickable(enabled = enabled) { showDialog = true },
            ) {}
        },
        modifier = Modifier.clickable(enabled = enabled) { showDialog = true },
    )

    if (showDialog) {
        ColorPickerDialog(
            initialColor = color,
            onDismiss = { showDialog = false },
            onConfirm = { selected ->
                onColorSelected(selected)
                showDialog = false
            },
        )
    }
}

@Composable
private fun ColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit,
) {
    val controller = rememberColorPickerController()
    var selectedColor by remember { mutableStateOf(initialColor) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick a color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HsvColorPicker(
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    controller = controller,
                    initialColor = initialColor,
                    onColorChanged = { envelope -> selectedColor = envelope.color },
                )
                BrightnessSlider(
                    modifier = Modifier.fillMaxWidth().height(35.dp),
                    controller = controller,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedColor) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
