package com.example.vocalorie.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.vocalorie.ai.KoogNutritionSpike
import com.example.vocalorie.settings.OpenAiModelChoice
import com.example.vocalorie.settings.ToolSettings
import com.example.vocalorie.settings.ToolSettingsLabels
import com.example.vocalorie.ui.components.SectionTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
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
    var newApiKey by remember { mutableStateOf("") }
    var newBraveApiKey by remember { mutableStateOf("") }
    var maxResearchToolCallsInput by remember { mutableStateOf(toolSettings.maxResearchToolCalls.toString()) }
    var maxAgentIterationsInput by remember { mutableStateOf(toolSettings.maxAgentIterations.toString()) }
    var showModelDialog by remember { mutableStateOf(false) }
    var pendingModelName by rememberSaveable(toolSettings.openAiModelChoiceName) { mutableStateOf(toolSettings.openAiModelChoiceName) }
    var systemPromptInput by remember(toolSettings.systemPromptOverride) {
        mutableStateOf(toolSettings.systemPromptOverride ?: KoogNutritionSpike.DEFAULT_SYSTEM_PROMPT)
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
                        systemPromptInput = KoogNutritionSpike.DEFAULT_SYSTEM_PROMPT
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
