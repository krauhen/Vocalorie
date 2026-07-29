package com.example.vocalorie.ui.capture

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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.vocalorie.model.SavedMeal
import com.example.vocalorie.ui.components.ReadOnlyMealSummary
import com.example.vocalorie.ui.components.formatDate

/**
 * Asks whether a cached meal should be reused instead of paying for a new estimate.
 *
 * Previously this was the shared meal-entry dialog in "approval mode", reached by handing it four
 * extra optional parameters and three no-op callbacks (`onDraftChange`, `onSave`, `onDelete`) that
 * existed only to satisfy an editing dialog that must never edit here. It is its own dialog now:
 * the approval branch has no delete, no edit and no save, and pretending otherwise made both flows
 * harder to read.
 *
 * The rendering is deliberately identical to what that approval branch drew, down to the button
 * order and the read-only summary, because reusing a cached meal must look exactly as it did.
 */
@Composable
fun CachedMealApprovalDialog(
    meal: SavedMeal,
    enabled: Boolean,
    message: String,
    /** "Estimate instead" when rejecting runs an estimate, "Close" when there is nothing to run. */
    rejectLabel: String,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    formatDate(meal.createdAtEpochMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ReadOnlyMealSummary(
                    title = meal.title.ifBlank { meal.query },
                    query = meal.query,
                    items = meal.items,
                    totals = meal.totals,
                    addedAtEpochMillis = meal.createdAtEpochMillis,
                )
                Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {},
        dismissButton = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextButton(onClick = onReject, enabled = enabled) { Text(rejectLabel) }
                TextButton(onClick = onApprove, enabled = enabled) { Text("Use cached") }
            }
        },
    )
}
