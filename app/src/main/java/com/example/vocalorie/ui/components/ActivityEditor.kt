package com.example.vocalorie.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import com.example.vocalorie.model.ActivityType
import com.example.vocalorie.model.EditableActivityDraft
import com.example.vocalorie.model.SELECTABLE_ACTIVITY_TYPES
import com.example.vocalorie.model.displayName
import com.example.vocalorie.model.activityTypeIcon
import com.example.vocalorie.model.stepsBurnKcal
import com.example.vocalorie.ui.entries.formatDuration

@Composable
fun EditableActivityEditor(
    draft: EditableActivityDraft,
    onDraftChange: (EditableActivityDraft) -> Unit,
    enabled: Boolean,
    kcalPerStep: Double,
    modifier: Modifier = Modifier,
    actionLabel: String? = "Save activity",
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
    onCreatedAtValidationChange: (Boolean) -> Unit = {},
    onTypeValidationChange: (Boolean) -> Unit = {},
) {
    var isCreatedAtValid by remember(draft.createdAtEpochMillis) { mutableStateOf(true) }

    LaunchedEffect(draft.createdAtEpochMillis) {
        draft.createdAtEpochMillis ?: return@LaunchedEffect
        isCreatedAtValid = true
        onCreatedAtValidationChange(isCreatedAtValid)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Activity", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)

            draft.createdAtEpochMillis?.let { epochMillis ->
                EntryTimestampField(
                    epochMillis = epochMillis,
                    enabled = enabled,
                    onChange = { onDraftChange(draft.copy(createdAtEpochMillis = it)) },
                    onValidationChange = {
                        isCreatedAtValid = it
                        onCreatedAtValidationChange(it)
                    },
                )
            }

            ActivityTypePicker(
                type = draft.type,
                enabled = enabled,
                onTypeSelected = { onDraftChange(draft.copy(type = it)) },
                onTypeValidationChange = onTypeValidationChange,
            )

            OutlinedTextField(
                value = draft.title,
                onValueChange = { onDraftChange(draft.copy(title = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Title") },
                enabled = enabled,
                singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            OutlinedTextField(
                value = draft.description,
                onValueChange = { onDraftChange(draft.copy(description = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Description") },
                enabled = enabled,
                minLines = 2,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            if (draft.type == ActivityType.STEPS) {
                OutlinedTextField(
                    value = draft.steps,
                    onValueChange = { onDraftChange(draft.copy(steps = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Steps") },
                    enabled = enabled,
                    singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = {
                        val steps = draft.steps.trim().toIntOrNull()
                        val derived = steps?.let { stepsBurnKcal(it, kcalPerStep).formatNullable() }
                        Text(if (derived != null) "≈ $derived kcal burned" else "Enter your step count")
                    },
                )
            } else {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.caloriesBurnedKcal,
                        onValueChange = { onDraftChange(draft.copy(caloriesBurnedKcal = it)) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Burned kcal") },
                        enabled = enabled,
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                    OutlinedTextField(
                        value = draft.durationMinutes,
                        onValueChange = { onDraftChange(draft.copy(durationMinutes = it)) },
                        modifier = Modifier.weight(1f),
                        label = { Text("Duration mins") },
                        enabled = enabled,
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }

            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction,
                    enabled = enabled && actionEnabled && isCreatedAtValid && draft.type != null,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun ReadOnlyActivitySummary(
    type: ActivityType,
    title: String,
    description: String,
    caloriesBurnedKcal: Double,
    durationMinutes: Int,
    addedAtEpochMillis: Long,
    modifier: Modifier = Modifier,
    stepsCount: Int? = null,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(type.activityTypeIcon(), contentDescription = null)
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        if (description.isNotBlank()) {
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        ActivitySummaryRow("Added at", formatDate(addedAtEpochMillis))
        if (stepsCount != null) {
            ActivitySummaryRow("Steps", stepsCount.toString())
        }
        ActivitySummaryRow("Burned", "${caloriesBurnedKcal.formatNullable()} kcal")
        if (stepsCount == null) {
            ActivitySummaryRow("Duration", formatDuration(durationMinutes))
        }
    }
}

@Composable
private fun ActivityTypePicker(
    type: ActivityType?,
    enabled: Boolean,
    onTypeSelected: (ActivityType) -> Unit,
    onTypeValidationChange: (Boolean) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    LaunchedEffect(type) { onTypeValidationChange(type != null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Type", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        OutlinedButton(
            onClick = { expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    type?.let { Icon(it.activityTypeIcon(), contentDescription = null) }
                    Text(type?.displayName() ?: "Select activity type")
                }
                Text("▾")
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            // ActivityType.OTHER is excluded: it exists only to represent unreadable persisted data.
            SELECTABLE_ACTIVITY_TYPES.forEach { activityType ->
                DropdownMenuItem(
                    text = { Text(activityType.displayName()) },
                    leadingIcon = { Icon(activityType.activityTypeIcon(), contentDescription = null) },
                    onClick = {
                        onTypeSelected(activityType)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun EntryTimestampField(epochMillis: Long, enabled: Boolean, onChange: (Long) -> Unit, onValidationChange: (Boolean) -> Unit) {
    var value by rememberSaveable { mutableStateOf(formatEditableTimestamp(epochMillis)) }
    var isInvalid by remember(epochMillis) { mutableStateOf(false) }

    LaunchedEffect(epochMillis) {
        if (shouldResyncEditableTimestamp(value, epochMillis)) {
            value = formatEditableTimestamp(epochMillis)
        }
        val isValid = parseEditableTimestamp(value) != null
        isInvalid = !isValid
        onValidationChange(isValid)
    }

    OutlinedTextField(
        value = value,
        onValueChange = { updated ->
            value = updated
            val parsed = parseEditableTimestamp(updated)
            val isValid = parsed != null
            isInvalid = !isValid
            onValidationChange(isValid)
            if (parsed != null) onChange(parsed)
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Added date/time") },
        enabled = enabled,
        isError = isInvalid,
        supportingText = { Text(if (isInvalid) "Enter a real date/time as $EDITABLE_TIMESTAMP_FORMAT" else "Format: $EDITABLE_TIMESTAMP_FORMAT") },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Text),
    )
}

@Composable
private fun ActivitySummaryRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}
