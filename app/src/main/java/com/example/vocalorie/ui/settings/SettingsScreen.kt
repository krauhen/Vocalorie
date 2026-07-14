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
import com.example.vocalorie.settings.ThemeColors
import com.example.vocalorie.settings.ToolSettings
import com.example.vocalorie.settings.ToolSettingsLabels
import com.example.vocalorie.ui.components.SectionTitle
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeColors: ThemeColors,
    onSavePrimaryColor: (Color) -> Unit,
    onSaveSecondaryColor: (Color) -> Unit,
    onSaveAccentColor: (Color) -> Unit,
    onSaveBackgroundColor: (Color) -> Unit,
    onSaveSurfaceColor: (Color) -> Unit,
    onSaveSurfaceVariantColor: (Color) -> Unit,
    onSaveOutlineColor: (Color) -> Unit,
    activityColors: ThemeColors,
    onSaveActivityPrimaryColor: (Color) -> Unit,
    onSaveActivitySecondaryColor: (Color) -> Unit,
    onSaveActivityAccentColor: (Color) -> Unit,
    onSaveActivityOutlineColor: (Color) -> Unit,
    baseCaloriesBurned: Int,
    onSaveBaseCaloriesBurned: (String) -> Unit,
    kcalPerStep: Double,
    onSaveKcalPerStep: (String) -> Unit,
    savedKeyLabel: String?,
    runtimeApiKey: String,
    onRuntimeApiKeyChange: (String) -> Unit,
    braveKeyLabel: String?,
    toolSettings: ToolSettings,
    message: String?,
    enabled: Boolean,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onSaveBraveKey: (String) -> Unit,
    onClearBraveKey: () -> Unit,
    onSaveMaxResearchToolCalls: (String) -> Unit,
    onSaveMaxAgentIterations: (String) -> Unit,
    onSaveOpenAiModelChoice: (String) -> Unit,
    onSaveSystemPrompt: (String) -> Unit,
    onResetSystemPrompt: () -> Unit,
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
        SettingsContent(
            padding = padding,
            themeColors = themeColors,
            onSavePrimaryColor = onSavePrimaryColor,
            onSaveSecondaryColor = onSaveSecondaryColor,
            onSaveAccentColor = onSaveAccentColor,
            onSaveBackgroundColor = onSaveBackgroundColor,
            onSaveSurfaceColor = onSaveSurfaceColor,
            onSaveSurfaceVariantColor = onSaveSurfaceVariantColor,
            onSaveOutlineColor = onSaveOutlineColor,
            activityColors = activityColors,
            onSaveActivityPrimaryColor = onSaveActivityPrimaryColor,
            onSaveActivitySecondaryColor = onSaveActivitySecondaryColor,
            onSaveActivityAccentColor = onSaveActivityAccentColor,
            onSaveActivityOutlineColor = onSaveActivityOutlineColor,
            baseCaloriesBurned = baseCaloriesBurned,
            onSaveBaseCaloriesBurned = onSaveBaseCaloriesBurned,
            kcalPerStep = kcalPerStep,
            onSaveKcalPerStep = onSaveKcalPerStep,
            savedKeyLabel = savedKeyLabel,
            runtimeApiKey = runtimeApiKey,
            onRuntimeApiKeyChange = onRuntimeApiKeyChange,
            braveKeyLabel = braveKeyLabel,
            toolSettings = toolSettings,
            message = message,
            enabled = enabled,
            onSaveKey = onSaveKey,
            onClearKey = onClearKey,
            onSaveBraveKey = onSaveBraveKey,
            onClearBraveKey = onClearBraveKey,
            onSaveMaxResearchToolCalls = onSaveMaxResearchToolCalls,
            onSaveMaxAgentIterations = onSaveMaxAgentIterations,
            onSaveOpenAiModelChoice = onSaveOpenAiModelChoice,
            onSaveSystemPrompt = onSaveSystemPrompt,
            onResetSystemPrompt = onResetSystemPrompt,
        )
    }
}

@Composable
private fun SettingsContent(
    padding: PaddingValues,
    themeColors: ThemeColors,
    onSavePrimaryColor: (Color) -> Unit,
    onSaveSecondaryColor: (Color) -> Unit,
    onSaveAccentColor: (Color) -> Unit,
    onSaveBackgroundColor: (Color) -> Unit,
    onSaveSurfaceColor: (Color) -> Unit,
    onSaveSurfaceVariantColor: (Color) -> Unit,
    onSaveOutlineColor: (Color) -> Unit,
    activityColors: ThemeColors,
    onSaveActivityPrimaryColor: (Color) -> Unit,
    onSaveActivitySecondaryColor: (Color) -> Unit,
    onSaveActivityAccentColor: (Color) -> Unit,
    onSaveActivityOutlineColor: (Color) -> Unit,
    baseCaloriesBurned: Int,
    onSaveBaseCaloriesBurned: (String) -> Unit,
    kcalPerStep: Double,
    onSaveKcalPerStep: (String) -> Unit,
    savedKeyLabel: String?,
    runtimeApiKey: String,
    onRuntimeApiKeyChange: (String) -> Unit,
    braveKeyLabel: String?,
    toolSettings: ToolSettings,
    message: String?,
    enabled: Boolean,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onSaveBraveKey: (String) -> Unit,
    onClearBraveKey: () -> Unit,
    onSaveMaxResearchToolCalls: (String) -> Unit,
    onSaveMaxAgentIterations: (String) -> Unit,
    onSaveOpenAiModelChoice: (String) -> Unit,
    onSaveSystemPrompt: (String) -> Unit,
    onResetSystemPrompt: () -> Unit,
) {
    var primaryColor by remember(themeColors.primary) { mutableStateOf(themeColors.primary) }
    var secondaryColor by remember(themeColors.secondary) { mutableStateOf(themeColors.secondary) }
    var accentColor by remember(themeColors.accent) { mutableStateOf(themeColors.accent) }
    var backgroundColor by remember(themeColors.background) { mutableStateOf(themeColors.background) }
    var surfaceColor by remember(themeColors.surface) { mutableStateOf(themeColors.surface) }
    var surfaceVariantColor by remember(themeColors.surfaceVariant) { mutableStateOf(themeColors.surfaceVariant) }
    var outlineColor by remember(themeColors.outline) { mutableStateOf(themeColors.outline) }
    var activityPrimaryColor by remember(activityColors.primary) { mutableStateOf(activityColors.primary) }
    var activitySecondaryColor by remember(activityColors.secondary) { mutableStateOf(activityColors.secondary) }
    var activityAccentColor by remember(activityColors.accent) { mutableStateOf(activityColors.accent) }
    var activityOutlineColor by remember(activityColors.outline) { mutableStateOf(activityColors.outline) }
    var baseCaloriesBurnedInput by remember(baseCaloriesBurned) { mutableStateOf(baseCaloriesBurned.toString()) }
    // Edited as kcal per 1,000 steps (friendlier than tiny per-step decimals).
    var kcalPer1000StepsInput by remember(kcalPerStep) {
        mutableStateOf((kcalPerStep * 1000).let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() })
    }
    var newApiKey by remember { mutableStateOf("") }
    var newBraveApiKey by remember { mutableStateOf("") }
    var maxResearchToolCallsInput by remember { mutableStateOf(toolSettings.maxResearchToolCalls.toString()) }
    var maxAgentIterationsInput by remember { mutableStateOf(toolSettings.maxAgentIterations.toString()) }
    var showModelDialog by remember { mutableStateOf(false) }
    var pendingModelName by rememberSaveable(toolSettings.openAiModelChoiceName) { mutableStateOf(toolSettings.openAiModelChoiceName) }
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
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                ColorPickerRow(
                    label = "Background",
                    color = backgroundColor,
                    enabled = enabled,
                    onColorSelected = { backgroundColor = it; onSaveBackgroundColor(it) },
                )
                ColorPickerRow(
                    label = "Surface",
                    color = surfaceColor,
                    enabled = enabled,
                    onColorSelected = { surfaceColor = it; onSaveSurfaceColor(it) },
                )
                ColorPickerRow(
                    label = "Surface variant",
                    color = surfaceVariantColor,
                    enabled = enabled,
                    onColorSelected = { surfaceVariantColor = it; onSaveSurfaceVariantColor(it) },
                )
            }
        }

        SectionTitle("Meal Appearance")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                ColorPickerRow(
                    label = "Primary",
                    color = primaryColor,
                    enabled = enabled,
                    onColorSelected = { primaryColor = it; onSavePrimaryColor(it) },
                )
                ColorPickerRow(
                    label = "Secondary",
                    color = secondaryColor,
                    enabled = enabled,
                    onColorSelected = { secondaryColor = it; onSaveSecondaryColor(it) },
                )
                ColorPickerRow(
                    label = "Accent",
                    color = accentColor,
                    enabled = enabled,
                    onColorSelected = { accentColor = it; onSaveAccentColor(it) },
                )
                ColorPickerRow(
                    label = "Outline",
                    color = outlineColor,
                    enabled = enabled,
                    onColorSelected = { outlineColor = it; onSaveOutlineColor(it) },
                )
            }
        }

        SectionTitle("Activity Appearance")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                ColorPickerRow(
                    label = "Primary",
                    color = activityPrimaryColor,
                    enabled = enabled,
                    onColorSelected = { activityPrimaryColor = it; onSaveActivityPrimaryColor(it) },
                )
                ColorPickerRow(
                    label = "Secondary",
                    color = activitySecondaryColor,
                    enabled = enabled,
                    onColorSelected = { activitySecondaryColor = it; onSaveActivitySecondaryColor(it) },
                )
                ColorPickerRow(
                    label = "Accent",
                    color = activityAccentColor,
                    enabled = enabled,
                    onColorSelected = { activityAccentColor = it; onSaveActivityAccentColor(it) },
                )
                ColorPickerRow(
                    label = "Outline",
                    color = activityOutlineColor,
                    enabled = enabled,
                    onColorSelected = { activityOutlineColor = it; onSaveActivityOutlineColor(it) },
                )
            }
        }

        SectionTitle("Energy baseline")
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Base calories burned per day") },
                supportingContent = { Text("Current: $baseCaloriesBurned kcal") },
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
                    onClick = { onSaveBaseCaloriesBurned(baseCaloriesBurnedInput) },
                    enabled = enabled && baseCaloriesBurnedInput.isNotBlank(),
                ) { Text("Save base burn") }
            }
            ListItem(
                headlineContent = { Text("Calories burned per 1,000 steps") },
                supportingContent = { Text("Current: ${kcalPerStep * 1000} kcal / 1,000 steps") },
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
                    onClick = { onSaveKcalPerStep(kcalPer1000StepsInput) },
                    enabled = enabled && kcalPer1000StepsInput.isNotBlank(),
                ) { Text("Save step burn") }
            }
        }

        SectionTitle("OpenAI")
        Card(modifier = Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("Saved key") },
                supportingContent = { Text(savedKeyLabel ?: "No saved OpenAI API key") },
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
                        onSaveKey(newApiKey)
                        newApiKey = ""
                    },
                    enabled = enabled && newApiKey.isNotBlank(),
                ) { Text(if (savedKeyLabel == null) "Save key" else "Update key") }
                OutlinedButton(onClick = onClearKey, enabled = enabled && savedKeyLabel != null) { Text("Remove") }
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
                value = runtimeApiKey,
                onValueChange = onRuntimeApiKeyChange,
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
                    onClick = { onSaveMaxResearchToolCalls(maxResearchToolCallsInput) },
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
                    onClick = { onSaveMaxAgentIterations(maxAgentIterationsInput) },
                    enabled = enabled && maxAgentIterationsInput.isNotBlank(),
                ) { Text("Save workflow limit") }
            }
            ListItem(
                headlineContent = { Text("Brave API key") },
                supportingContent = { Text(braveKeyLabel ?: "No saved Brave API key") },
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
                        onSaveBraveKey(newBraveApiKey)
                        newBraveApiKey = ""
                    },
                    enabled = enabled && newBraveApiKey.isNotBlank(),
                ) { Text(if (braveKeyLabel == null) "Save Brave key" else "Update Brave key") }
                OutlinedButton(onClick = onClearBraveKey, enabled = enabled && braveKeyLabel != null) { Text("Remove") }
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
                Button(onClick = { onSaveSystemPrompt(systemPromptInput) }, enabled = enabled) { Text("Save prompt") }
                OutlinedButton(
                    onClick = {
                        onResetSystemPrompt()
                        systemPromptInput = KoogNutritionAgent.DEFAULT_SYSTEM_PROMPT
                    },
                    enabled = enabled,
                ) { Text("Reset to default") }
            }
        }

        message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
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
                    onSaveOpenAiModelChoice(pendingModelName)
                    showModelDialog = false
                }) { Text("Apply") }
            },
            dismissButton = { TextButton(onClick = { showModelDialog = false }) { Text("Cancel") } },
        )
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
