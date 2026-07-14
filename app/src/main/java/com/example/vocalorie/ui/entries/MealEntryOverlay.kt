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
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.SavedMeal
import com.example.vocalorie.ui.components.EditableMealEditor
import com.example.vocalorie.ui.components.ReadOnlyMealSummary
import com.example.vocalorie.ui.components.formatDate

@Composable
fun MealEntryOverlay(
    meal: SavedMeal,
    draft: EditableMealDraft,
    enabled: Boolean,
    message: String?,
    error: String?,
    onDraftChange: (EditableMealDraft) -> Unit,
    onSave: (EditableMealDraft, onSaved: () -> Unit) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    approvalMessage: String? = null,
    approvalActionLabel: String? = null,
    onApprove: (() -> Unit)? = null,
    onReject: (() -> Unit)? = null,
) {
    var isEditing by remember(meal.id) { mutableStateOf(false) }
    var isCreatedAtValid by remember(meal.id, isEditing) { mutableStateOf(true) }
    val isApprovalMode = onApprove != null || onReject != null

    LaunchedEffect(isApprovalMode) {
        if (isApprovalMode) {
            isEditing = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isEditing) {
                    Text("Edit meal")
                    Text(formatDate(draft.createdAtEpochMillis ?: meal.createdAtEpochMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text(formatDate(meal.createdAtEpochMillis), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (isEditing) {
                    EditableMealEditor(
                        draft = draft,
                        onDraftChange = onDraftChange,
                        enabled = enabled,
                        actionLabel = null,
                        // actionLabel = "Save"
                        onAction = null,
                        actionEnabled = isCreatedAtValid,
                        onCreatedAtValidationChange = { isCreatedAtValid = it },
                    )
                } else {
                    ReadOnlyMealSummary(
                        title = meal.title.ifBlank { meal.query },
                        query = meal.query,
                        items = meal.items,
                        totals = meal.totals,
                        addedAtEpochMillis = meal.createdAtEpochMillis,
                    )
                }
                approvalMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isApprovalMode) {
                    TextButton(
                        onClick = {
                            onReject?.invoke()
                            onDismiss()
                        },
                        enabled = enabled,
                    ) {
                        Text(approvalActionLabel ?: if (onReject != null) "Estimate instead" else "Close")
                    }
                    onApprove?.let {
                        TextButton(onClick = { it(); onDismiss() }, enabled = enabled) { Text("Use cached") }
                    }
                } else {
                    OutlinedButton(onClick = onDelete, enabled = enabled) { Text("Delete") }
                    if (isEditing) {
                    TextButton(onClick = { onSave(draft) { isEditing = false } }, enabled = enabled && isCreatedAtValid) { Text("Save") }
                    }
                    if (!isEditing) {
                        TextButton(onClick = { isEditing = true }, enabled = enabled) { Text("Edit") }
                    }
                    TextButton(onClick = onDismiss) { Text(if (isEditing) "Cancel" else "Close") }
                }
            }
        },
    )
}
