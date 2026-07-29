package com.example.vocalorie.ui.entries

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cake
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Fastfood
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.vocalorie.model.MealCategory
import com.example.vocalorie.model.SavedActivity
import com.example.vocalorie.model.SavedMeal
import com.example.vocalorie.model.displayName
import com.example.vocalorie.ui.activityTypeIcon
import com.example.vocalorie.ui.components.activityCalorieStateStyle
import com.example.vocalorie.ui.components.formatDate
import com.example.vocalorie.ui.components.formatEnergy
import com.example.vocalorie.ui.components.formatNullable
import com.example.vocalorie.ui.components.mealCalorieStateStyle
import com.example.vocalorie.ui.macroColors
import java.time.Instant

@Composable
internal fun EmptyEntriesCard(hasSavedEntries: Boolean, emptyText: String) {
    val title = if (hasSavedEntries) "No entries in this time window" else emptyText

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private fun Modifier.futureEntryHighlight(isFuture: Boolean, color: Color): Modifier {
    if (!isFuture) return this
    return drawBehind {
        val cornerRadiusPx = 12.dp.toPx()
        val cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
        val hatchPath = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, cornerRadius)) }
        clipPath(hatchPath) {
            val spacingPx = 20.dp.toPx()
            val hatchStrokeWidthPx = 2.dp.toPx()
            var x = -size.height
            while (x < size.width) {
                drawLine(
                    color = color.copy(alpha = 0.25f),
                    start = Offset(x, size.height),
                    end = Offset(x + size.height, 0f),
                    strokeWidth = hatchStrokeWidthPx,
                )
                x += spacingPx
            }
        }
        val strokeWidthPx = 2.dp.toPx()
        val inset = strokeWidthPx / 2
        drawRoundRect(
            color = color,
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
            cornerRadius = cornerRadius,
            style = Stroke(
                width = strokeWidthPx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(16.dp.toPx(), 12.dp.toPx()), 0f),
            ),
        )
    }
}

@Composable
internal fun MealEntryRow(meal: SavedMeal, now: Instant, onClick: () -> Unit) {
    val style = mealCalorieStateStyle(meal.totals.caloriesKcal)
    val isFuture = Instant.ofEpochMilli(meal.createdAtEpochMillis).isAfter(now)
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = style.containerColor, contentColor = style.contentColor),
            border = if (isFuture) null else BorderStroke(1.dp, style.borderColor),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        meal.title.ifBlank { meal.query },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Icon(
                        imageVector = meal.category.categoryIcon(),
                        contentDescription = meal.category.name,
                        // Tint with the row's own contentColor (always legible on its container),
                        // not primary — high-calorie rows use primary as their background.
                        tint = style.contentColor,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Text(
                    meal.query,
                    style = MaterialTheme.typography.bodySmall,
                    color = style.contentColor.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    meal.totals.caloriesKcal.formatEnergy(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                val macros = macroColors()
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = macros.fat, fontWeight = FontWeight.SemiBold)) {
                            append("Fat ${meal.totals.fatG.formatNullable()}g")
                        }
                        append(" · ")
                        withStyle(SpanStyle(color = macros.carbs, fontWeight = FontWeight.SemiBold)) {
                            append("Carbs ${meal.totals.carbsG.formatNullable()}g")
                        }
                        append(" · ")
                        withStyle(SpanStyle(color = macros.protein, fontWeight = FontWeight.SemiBold)) {
                            append("Protein ${meal.totals.proteinG.formatNullable()}g")
                        }
                        append(" · Amount ${meal.totals.amountGml.formatNullable()}g/ml")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = style.contentColor,
                )
                Text(
                    formatDate(meal.createdAtEpochMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = style.contentColor.copy(alpha = 0.65f),
                )
            }
        }
        if (isFuture) {
            Box(modifier = Modifier.matchParentSize().futureEntryHighlight(true, style.borderColor))
        }
    }
}

@Composable
internal fun ActivityEntryRow(activity: SavedActivity, now: Instant, onClick: () -> Unit) {
    val style = activityCalorieStateStyle(activity.caloriesBurnedKcal)
    val isFuture = Instant.ofEpochMilli(activity.createdAtEpochMillis).isAfter(now)
    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
            colors = CardDefaults.cardColors(containerColor = style.containerColor, contentColor = style.contentColor),
            border = if (isFuture) null else BorderStroke(1.dp, style.borderColor),
        ) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(activity.type.activityTypeIcon(), contentDescription = null)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        activity.title.ifBlank { activity.type.displayName() },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (activity.description.isNotBlank()) {
                        Text(
                            activity.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = style.contentColor.copy(alpha = 0.82f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        "${activity.caloriesBurnedKcal.formatNullable()} kcal · ${formatDuration(activity.durationMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = style.contentColor.copy(alpha = 0.82f),
                    )
                }
            }
        }
        if (isFuture) {
            Box(modifier = Modifier.matchParentSize().futureEntryHighlight(true, style.borderColor))
        }
    }
}

/** Total mapping of a meal's food-type category to its list-row icon; OTHER is the neutral default. */
private fun MealCategory.categoryIcon() = when (this) {
    MealCategory.MEAL -> Icons.Outlined.Restaurant
    MealCategory.SNACK -> Icons.Outlined.Cookie
    MealCategory.DRINK -> Icons.Outlined.LocalCafe
    MealCategory.DESSERT -> Icons.Outlined.Cake
    MealCategory.OTHER -> Icons.Outlined.Fastfood
}
