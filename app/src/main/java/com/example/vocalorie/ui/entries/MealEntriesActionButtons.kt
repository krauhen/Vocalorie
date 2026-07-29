package com.example.vocalorie.ui.entries

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsActionButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = CircleShape, color = MaterialTheme.colorScheme.surface, tonalElevation = 6.dp, shadowElevation = 6.dp) {
        TextButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
            Text("⚙", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
internal fun ActivityAddButton(onClick: () -> Unit) {
    androidx.compose.material3.ExtendedFloatingActionButton(
        onClick = onClick,
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) {
        Text("Add", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
    }
}
