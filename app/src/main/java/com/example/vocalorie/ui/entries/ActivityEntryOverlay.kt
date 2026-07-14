package com.example.vocalorie.ui.entries

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.vocalorie.model.EditableActivityDraft
import com.example.vocalorie.model.SavedActivity
import com.example.vocalorie.model.displayName
import com.example.vocalorie.ui.components.EditableActivityEditor
import com.example.vocalorie.ui.components.ReadOnlyActivitySummary
import com.example.vocalorie.ui.components.formatDate

@Composable
fun ActivityEntryOverlay(
    activity: SavedActivity?,
    draft: EditableActivityDraft,
    enabled: Boolean,
    kcalPerStep: Double,
    message: String?,
    error: String?,
    onDraftChange: (EditableActivityDraft) -> Unit,
    onSave: (EditableActivityDraft, onSaved: () -> Unit) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var isEditing by remember(activity?.id) { mutableStateOf(activity == null) }
    var isCreatedAtValid by remember(activity?.id, isEditing) { mutableStateOf(true) }
    var isTypeValid by remember(activity?.id, isEditing) { mutableStateOf(activity != null || draft.type != null) }
    val createAction: (() -> Unit)? = if (activity == null) { { onSave(draft) { onDismiss() } } } else null

    LaunchedEffect(activity?.id) {
        if (activity == null) {
            isEditing = true
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isEditing) {
                    Text(if (activity == null) "Add activity" else "Edit activity")
                    draft.createdAtEpochMillis?.let { epochMillis ->
                        Text(formatDate(epochMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    Text(formatDate(activity!!.createdAtEpochMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isEditing) {
                    EditableActivityEditor(
                        draft = draft,
                        onDraftChange = onDraftChange,
                        enabled = enabled,
                        kcalPerStep = kcalPerStep,
                        actionLabel = if (activity == null) "Save activity" else null,
                        onAction = createAction,
                        actionEnabled = isCreatedAtValid && isTypeValid,
                        onCreatedAtValidationChange = { isCreatedAtValid = it },
                        onTypeValidationChange = { isTypeValid = it },
                    )
                } else {
                    ReadOnlyActivitySummary(
                        type = activity!!.type,
                        title = activity.title.ifBlank { activity.type.displayName() },
                        description = activity.description,
                        caloriesBurnedKcal = activity.caloriesBurnedKcal,
                        durationMinutes = activity.durationMinutes,
                        addedAtEpochMillis = activity.createdAtEpochMillis,
                        stepsCount = activity.stepsCount,
                    )
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (activity == null) {
                    TextButton(onClick = onDismiss, enabled = enabled) { Text("Cancel") }
                } else if (isEditing) {
                    OutlinedButton(onClick = onDelete, enabled = enabled) { Text("Delete") }
                    TextButton(onClick = { onSave(draft) { isEditing = false } }, enabled = enabled && isCreatedAtValid && isTypeValid) { Text("Save") }
                    TextButton(onClick = { isEditing = false }, enabled = enabled) { Text("Cancel") }
                } else {
                    OutlinedButton(onClick = onDelete, enabled = enabled) { Text("Delete") }
                    TextButton(onClick = { isEditing = true }, enabled = enabled) { Text("Edit") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        },
    )
}
