package com.example.vocalorie.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.vocalorie.model.ConfidenceLevel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Date
import java.util.Locale
import java.time.temporal.ChronoField
import kotlinx.coroutines.delay

const val EDITABLE_TIMESTAMP_FORMAT: String = "yyyy-MM-dd HH:mm"

private val mealDateTimeFormatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("dd.MM.yyyy HH:mm", Locale.getDefault())
    .withZone(ZoneId.systemDefault())

private val mealDateFormatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("dd.MM.yyyy", Locale.getDefault())
    .withZone(ZoneId.systemDefault())

private val editableTimestampFormatter: DateTimeFormatter = DateTimeFormatter
    .ofPattern("uuuu-MM-dd HH:mm")
    .withResolverStyle(ResolverStyle.STRICT)

private val editableTimestampParser: DateTimeFormatter = DateTimeFormatterBuilder()
    .appendPattern("uuuu-MM-dd ")
    .appendValue(ChronoField.HOUR_OF_DAY, 1, 2, java.time.format.SignStyle.NOT_NEGATIVE)
    .appendPattern(":mm")
    .toFormatter()
    .withResolverStyle(ResolverStyle.STRICT)

data class MealStateStyle(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
)

enum class MealCalorieBucket {
    NEUTRAL,
    CREAM_YELLOW,
    SOFT_YELLOW,
    ORANGE,
    DEEP_ORANGE,
}

fun mealCalorieBucket(caloriesKcal: Double?): MealCalorieBucket = when {
    caloriesKcal == null || !caloriesKcal.isFinite() -> MealCalorieBucket.NEUTRAL
    caloriesKcal < 150.0 -> MealCalorieBucket.CREAM_YELLOW
    caloriesKcal < 400.0 -> MealCalorieBucket.SOFT_YELLOW
    caloriesKcal < 800.0 -> MealCalorieBucket.ORANGE
    else -> MealCalorieBucket.DEEP_ORANGE
}

@Composable
fun mealCalorieStateStyle(caloriesKcal: Double?): MealStateStyle = when (mealCalorieBucket(caloriesKcal)) {
    MealCalorieBucket.NEUTRAL -> MealStateStyle(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
    )
    MealCalorieBucket.CREAM_YELLOW -> MealStateStyle(
        containerColor = Color(0xFFFFF8E1),
        contentColor = Color(0xFF1F1300),
        borderColor = Color(0xFFFFCC33),
    )
    MealCalorieBucket.SOFT_YELLOW -> MealStateStyle(
        containerColor = Color(0xFFFFF1B8),
        contentColor = Color(0xFF1F1300),
        borderColor = Color(0xFFFFB600),
    )
    MealCalorieBucket.ORANGE -> MealStateStyle(
        containerColor = Color(0xFFFFE2B0),
        contentColor = Color(0xFF1F1300),
        borderColor = Color(0xFFFF9500),
    )
    MealCalorieBucket.DEEP_ORANGE -> MealStateStyle(
        containerColor = Color(0xFFF77605),
        contentColor = Color(0xFF1F1300),
        borderColor = Color(0xFFB84A00),
    )
}

@Composable
fun mealStateStyle(confidence: ConfidenceLevel, needsHumanReview: Boolean): MealStateStyle = when {
    confidence == ConfidenceLevel.LOW -> MealStateStyle(
        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = MaterialTheme.colorScheme.error,
    )
    confidence == ConfidenceLevel.MEDIUM -> MealStateStyle(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = MaterialTheme.colorScheme.outline,
    )
    else -> MealStateStyle(
        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        borderColor = MaterialTheme.colorScheme.primary,
    )
}

@Composable
fun SectionTitle(text: String) {
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
fun HeaderDropdownAction(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    menuContent: @Composable () -> Unit,
) {
    Box(modifier = modifier) {
        Text(
            text = "▾",
            modifier = Modifier
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }) {
            menuContent()
        }
    }
}

@Composable
fun LoadingRow(message: String? = null) {
    var dotCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(message) {
        while (true) {
            delay(420)
            dotCount = (dotCount + 1) % 4
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 1.dp), strokeWidth = 2.5.dp)
                Text(message ?: "Working${".".repeat(dotCount)}", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SourceUrlRow(label: String, url: String) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            url,
            modifier = Modifier
                .weight(1f)
                .clickable {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun ErrorCard(message: String, diagnostic: String?, onRetry: (() -> Unit)? = null, enabled: Boolean = true) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(text = message, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            diagnostic?.let { Text(text = it, style = MaterialTheme.typography.bodySmall) }
            onRetry?.let { OutlinedButton(onClick = it, enabled = enabled) { Text("Try again") } }
        }
    }
}

fun Double?.formatNullable(): String = this?.let { value ->
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
} ?: "unknown"

fun formatDate(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(mealDateTimeFormatter)

fun formatDateOnly(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(mealDateFormatter)

fun formatEditableTimestamp(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String = LocalDateTime
    .ofInstant(Instant.ofEpochMilli(epochMillis), zone)
    .format(editableTimestampFormatter)

fun parseEditableTimestamp(value: String, zone: ZoneId = ZoneId.systemDefault()): Long? = try {
    LocalDateTime.parse(value.trim(), editableTimestampParser)
        .atZone(zone)
        .toInstant()
        .toEpochMilli()
} catch (_: DateTimeParseException) {
    null
}

fun shouldResyncEditableTimestamp(value: String, epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean =
    parseEditableTimestamp(value, zone) != epochMillis
