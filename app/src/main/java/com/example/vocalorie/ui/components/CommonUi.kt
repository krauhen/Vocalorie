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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.DatePicker
import androidx.compose.material3.TimePicker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.vocalorie.model.ConfidenceLevel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle
import java.util.Date
import java.util.Locale
import java.time.temporal.ChronoField
import kotlin.math.roundToInt
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
fun mealCalorieStateStyle(caloriesKcal: Double?): MealStateStyle {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    return when (mealCalorieBucket(caloriesKcal)) {
        MealCalorieBucket.NEUTRAL -> MealStateStyle(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = onSurface,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f),
        )
        MealCalorieBucket.CREAM_YELLOW -> MealStateStyle(
            containerColor = primary.copy(alpha = 0.28f).compositeOver(surface),
            contentColor = onSurface,
            borderColor = primary.copy(alpha = 0.55f),
        )
        MealCalorieBucket.SOFT_YELLOW -> MealStateStyle(
            containerColor = primary.copy(alpha = 0.48f).compositeOver(surface),
            contentColor = onSurface,
            borderColor = primary.copy(alpha = 0.75f),
        )
        MealCalorieBucket.ORANGE -> MealStateStyle(
            containerColor = primary.copy(alpha = 0.72f).compositeOver(surface),
            contentColor = MaterialTheme.colorScheme.onPrimary,
            borderColor = primary,
        )
        MealCalorieBucket.DEEP_ORANGE -> MealStateStyle(
            containerColor = primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            borderColor = MaterialTheme.colorScheme.primaryContainer,
        )
    }
}

/**
 * Colours an activity by how many calories it burned: 200 kcal is the minimum tint,
 * 1000 kcal reaches full-strength blue (the active scheme's primary). Values in between
 * scale linearly.
 */
@Composable
fun activityCalorieStateStyle(caloriesBurnedKcal: Double?): MealStateStyle {
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val onSurface = MaterialTheme.colorScheme.onSurface
    val kcal = caloriesBurnedKcal?.takeIf { it.isFinite() } ?: 0.0
    val fraction = ((kcal - ACTIVITY_MIN_KCAL) / (ACTIVITY_MAX_KCAL - ACTIVITY_MIN_KCAL))
        .coerceIn(0.0, 1.0)
        .toFloat()
    val alpha = 0.18f + fraction * 0.82f
    return MealStateStyle(
        containerColor = primary.copy(alpha = alpha).compositeOver(surface),
        contentColor = if (fraction > 0.6f) MaterialTheme.colorScheme.onPrimary else onSurface,
        borderColor = primary.copy(alpha = (alpha + 0.2f).coerceAtMost(1f)),
    )
}

private const val ACTIVITY_MIN_KCAL = 200.0
private const val ACTIVITY_MAX_KCAL = 1000.0

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
            Text(message ?: "Working${".".repeat(dotCount)}")
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun SourceUrlRow(label: String, url: String) {
    val context = LocalContext.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Sourced · ${url.sourceDomainOrUrl()}",
            modifier = Modifier
                .weight(1f)
                .clickable {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
            color = MaterialTheme.colorScheme.primary,
            textDecoration = TextDecoration.Underline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Subtle indicator for an item whose nutrition values are an unsourced LLM estimate (blank source).
 * Pairs with [SourceUrlRow] so the two states — sourced vs estimate — are always visible.
 */
@Composable
fun SourceEstimateRow(label: String = "Source") {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Estimate · not sourced",
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Best-effort host for display, e.g. "https://fdc.nal.usda.gov/x" -> "fdc.nal.usda.gov". */
fun String.sourceDomainOrUrl(): String =
    runCatching { java.net.URI(trim()).host }.getOrNull()?.removePrefix("www.")?.takeIf { it.isNotBlank() } ?: trim()

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

/**
 * The one editable timestamp field, shared by the meal and activity editors so both stay in step on
 * label, format hint and error state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryTimestampField(epochMillis: Long, enabled: Boolean, onChange: (Long) -> Unit, onValidationChange: (Boolean) -> Unit) {
    val zone = remember { ZoneId.systemDefault() }
    var value by rememberSaveable { mutableStateOf(formatEditableTimestamp(epochMillis)) }
    var isInvalid by remember(epochMillis) { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }

    fun applyPicked(merged: Long) {
        value = formatEditableTimestamp(merged, zone)
        isInvalid = false
        onValidationChange(true)
        onChange(merged)
    }

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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        trailingIcon = {
            Row {
                IconButton(onClick = { showDatePicker = true }, enabled = enabled) {
                    Icon(Icons.Outlined.CalendarToday, contentDescription = "Pick date")
                }
                IconButton(onClick = { showTimePicker = true }, enabled = enabled) {
                    Icon(Icons.Outlined.Schedule, contentDescription = "Pick time")
                }
            }
        },
    )

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = toPickerDateMillis(epochMillis, zone))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { pickedDateUtcMillis ->
                        applyPicked(mergePickedDate(epochMillis, pickedDateUtcMillis, zone))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val currentTime = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalTime()
        val timePickerState = rememberTimePickerState(initialHour = currentTime.hour, initialMinute = currentTime.minute, is24Hour = true)
        TimePickerDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    applyPicked(mergePickedTime(epochMillis, timePickerState.hour, timePickerState.minute, zone))
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            title = {},
        ) {
            TimePicker(state = timePickerState)
        }
    }
}

fun Double?.formatNullable(): String = this?.let { value ->
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
} ?: "unknown"

fun Double?.formatEnergy(): String = this?.let { kcal ->
    "${(kcal * 4.184).roundToInt()} kJ / ${kcal.formatNullable()} kcal"
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

/**
 * Whether the field's text should be overwritten from [epochMillis].
 *
 * Text that does not parse is text the user is still typing — half a date, a cleared field, a minute
 * with one digit so far. Overwriting it would swallow their keystrokes, so only a complete value that
 * genuinely disagrees with the stored instant triggers a resync.
 */
fun shouldResyncEditableTimestamp(value: String, epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Boolean {
    val parsed = parseEditableTimestamp(value, zone) ?: return false
    return parsed != epochMillis
}

/** The local calendar date of [epochMillis] in [zone], expressed as UTC midnight — what `DatePickerState` expects. */
fun toPickerDateMillis(epochMillis: Long, zone: ZoneId): Long = Instant.ofEpochMilli(epochMillis)
    .atZone(zone)
    .toLocalDate()
    .atStartOfDay(ZoneOffset.UTC)
    .toInstant()
    .toEpochMilli()

/**
 * Combine the calendar date the picker returned (UTC midnight, per `DatePickerState`) with the
 * time-of-day [currentEpochMillis] already has in [zone]. `ZonedDateTime.of` resolution shifts a
 * time falling in a DST gap forward within the same calendar date — the behaviour the DST scenario
 * wants, not an accident to work around.
 */
fun mergePickedDate(currentEpochMillis: Long, pickedDateUtcMillis: Long, zone: ZoneId): Long {
    val pickedDate = Instant.ofEpochMilli(pickedDateUtcMillis).atZone(ZoneOffset.UTC).toLocalDate()
    val currentTime = Instant.ofEpochMilli(currentEpochMillis).atZone(zone).toLocalTime()
    return LocalDateTime.of(pickedDate, currentTime).atZone(zone).toInstant().toEpochMilli()
}

/** Keep the local calendar date of [currentEpochMillis] in [zone], replace the time-of-day; seconds/nanos are zeroed. */
fun mergePickedTime(currentEpochMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long {
    val currentDate = Instant.ofEpochMilli(currentEpochMillis).atZone(zone).toLocalDate()
    return LocalDateTime.of(currentDate, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
}
