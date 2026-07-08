package com.example.vocalorie.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.vocalorie.model.EditableFoodItem
import com.example.vocalorie.model.EditableMealDraft
import com.example.vocalorie.model.FoodItemEstimate
import com.example.vocalorie.model.NutritionTotals
import com.example.vocalorie.model.portionScaleFactor
import com.example.vocalorie.model.withItemsScaledByPortionFromBaseline
import com.example.vocalorie.model.withTotalsSummedFromItems
import java.math.BigDecimal
import kotlin.math.roundToInt

@Composable
fun EditableMealEditor(
    draft: EditableMealDraft,
    onDraftChange: (EditableMealDraft) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    actionLabel: String? = "Save locally",
    onAction: (() -> Unit)? = null,
    actionEnabled: Boolean = true,
    onCreatedAtValidationChange: (Boolean) -> Unit = {},
) {
    val style = mealStateStyle(draft.confidence, draft.needsHumanReview)
    var itemsExpanded by rememberSaveable(draft.title, draft.query) { mutableStateOf(false) }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(if (draft.needsHumanReview) 2.dp else 1.dp, style.borderColor),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Meal title", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = draft.title,
                onValueChange = { onDraftChange(draft.copy(title = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Meal title") },
                enabled = enabled,
                singleLine = true,
            )
            OutlinedTextField(
                value = draft.query,
                onValueChange = { onDraftChange(draft.copy(query = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Meal description") },
                enabled = enabled,
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            if (draft.createdAtEpochMillis != null) {
                EntryTimestampField(
                    epochMillis = draft.createdAtEpochMillis,
                    enabled = enabled,
                    onChange = { onDraftChange(draft.copy(createdAtEpochMillis = it)) },
                    onValidationChange = onCreatedAtValidationChange,
                )
            }

            SectionTitle("Totals")
            NutritionFields(
                calories = draft.caloriesKcal,
                amount = draft.amountGml,
                fat = draft.fatG,
                saturatedFat = draft.saturatedFatG,
                carbs = draft.carbsG,
                sugar = draft.sugarG,
                protein = draft.proteinG,
                salt = draft.saltG,
                enabled = enabled,
                readOnly = true,
                onChange = { calories, amount, fat, saturatedFat, carbs, sugar, protein, salt ->
                    onDraftChange(
                        draft.copy(
                            caloriesKcal = calories,
                            amountGml = amount,
                            fatG = fat,
                            saturatedFatG = saturatedFat,
                            carbsG = carbs,
                            sugarG = sugar,
                            proteinG = protein,
                            saltG = salt,
                        ),
                    )
                },
            )
            OutlinedTextField(
                value = draft.source,
                onValueChange = { onDraftChange(draft.copy(source = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Source URL") },
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            PortionScalingControls(
                draft = draft,
                enabled = enabled,
                onDraftChange = onDraftChange,
            )

            SectionTitle("Items")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${draft.items.size} item${if (draft.items.size == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { itemsExpanded = !itemsExpanded }, enabled = enabled) {
                    Text(if (itemsExpanded) "Hide" else "Show")
                }
            }
            if (itemsExpanded) {
                draft.items.forEachIndexed { index, item ->
                    EditableFoodItemCard(
                        item = item,
                        enabled = enabled,
                        onChange = { updated ->
                            onDraftChange(draft.copy(items = draft.items.toMutableList().also { it[index] = updated }))
                        },
                        onNutritionChange = { updated ->
                            onDraftChange(
                                draft.copy(items = draft.items.toMutableList().also { it[index] = updated })
                                    .withTotalsSummedFromItems(),
                            )
                        },
                    )
                }
            }

            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction, enabled = enabled && actionEnabled, modifier = Modifier.fillMaxWidth()) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun PortionScalingControls(
    draft: EditableMealDraft,
    enabled: Boolean,
    onDraftChange: (EditableMealDraft) -> Unit,
) {
    var recipeMakes by remember { mutableStateOf("") }
    var iAte by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var baselineItems by remember { mutableStateOf<List<EditableFoodItem>?>(null) }
    var lastScaledItems by remember { mutableStateOf<List<EditableFoodItem>?>(null) }
    var appliedScaleFactor by remember { mutableStateOf<BigDecimal?>(null) }

    fun baselineForCurrentItems(): List<EditableFoodItem> =
        if (lastScaledItems != null && draft.items == lastScaledItems) {
            baselineItems ?: draft.items
        } else {
            draft.items
        }

    fun canApplyScale(factor: BigDecimal?): Boolean =
        enabled && (factor == null || factor != appliedScaleFactor || draft.items != lastScaledItems)

    fun applyScale(makes: String, ate: String) {
        val factor = portionScaleFactor(recipeMakes = makes, ate = ate)
        val nextBaselineItems = baselineForCurrentItems()
        val scaled = draft.withItemsScaledByPortionFromBaseline(
            recipeMakes = makes,
            ate = ate,
            baselineItems = nextBaselineItems,
        )
        if (scaled == null) {
            error = "Enter positive numbers for Recipe makes and I ate."
        } else {
            recipeMakes = makes
            iAte = ate
            error = null
            baselineItems = nextBaselineItems
            lastScaledItems = scaled.items
            appliedScaleFactor = factor
            onDraftChange(scaled)
        }
    }

    val enteredFactor = portionScaleFactor(recipeMakes = recipeMakes, ate = iAte)
    val quarterFactor = portionScaleFactor(recipeMakes = "4", ate = "1")
    val halfFactor = portionScaleFactor(recipeMakes = "2", ate = "1")
    val threeQuarterFactor = portionScaleFactor(recipeMakes = "4", ate = "3")
    val allFactor = portionScaleFactor(recipeMakes = "1", ate = "1")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Scale recipe portion", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Enter the whole recipe yield and what you ate; item nutrition will be multiplied and totals recomputed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = recipeMakes,
                    onValueChange = { recipeMakes = it; error = null },
                    modifier = Modifier.weight(1f),
                    label = { Text("Recipe makes") },
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = iAte,
                    onValueChange = { iAte = it; error = null },
                    modifier = Modifier.weight(1f),
                    label = { Text("I ate") },
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            Button(onClick = { applyScale(recipeMakes, iAte) }, enabled = canApplyScale(enteredFactor), modifier = Modifier.fillMaxWidth()) { Text("Apply portion") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { applyScale("4", "1") }, enabled = canApplyScale(quarterFactor), modifier = Modifier.weight(1f)) { Text("¼") }
                OutlinedButton(onClick = { applyScale("2", "1") }, enabled = canApplyScale(halfFactor), modifier = Modifier.weight(1f)) { Text("½") }
                OutlinedButton(onClick = { applyScale("4", "3") }, enabled = canApplyScale(threeQuarterFactor), modifier = Modifier.weight(1f)) { Text("¾") }
                OutlinedButton(onClick = { applyScale("1", "1") }, enabled = canApplyScale(allFactor), modifier = Modifier.weight(1f)) { Text("All") }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
    )
}

@Composable
fun ReadOnlyMealSummary(
    title: String,
    query: String,
    items: List<FoodItemEstimate>,
    totals: NutritionTotals,
    source: String,
    addedAtEpochMillis: Long,
    modifier: Modifier = Modifier,
) {
    var itemsExpanded by rememberSaveable(title, query, addedAtEpochMillis) { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(query, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        NutritionRow("Added at", formatDate(addedAtEpochMillis))
        NutritionLabel(totals, source)

        if (items.isNotEmpty()) {
            SectionTitle("Items")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("${items.size} item${if (items.size == 1) "" else "s"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { itemsExpanded = !itemsExpanded }) {
                    Text(if (itemsExpanded) "Hide" else "Show")
                }
            }
            if (itemsExpanded) {
                items.forEach { item ->
                    ReadOnlyFoodItemCard(item = item)
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyFoodItemCard(item: FoodItemEstimate) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (item.quantity.isNotBlank()) {
                Text(item.quantity, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            NutritionRow("Amount", "${item.amountGml.formatNullable()} g/ml")
            NutritionRow("Energy", item.caloriesKcal.formatEnergy())
            NutritionRow("Fat", "${item.fatG.formatNullable()} g")
            NutritionRow("of which saturates", "${item.saturatedFatG.formatNullable()} g")
            NutritionRow("Carbohydrate", "${item.carbsG.formatNullable()} g")
            NutritionRow("of which sugars", "${item.sugarG.formatNullable()} g")
            NutritionRow("Protein", "${item.proteinG.formatNullable()} g")
            NutritionRow("Salt", "${item.saltG.formatNullable()} g")
            if (item.source.isNotBlank()) SourceUrlRow("Source URL", item.source)
        }
    }
}

@Composable
private fun EditableFoodItemCard(
    item: EditableFoodItem,
    enabled: Boolean,
    onChange: (EditableFoodItem) -> Unit,
    onNutritionChange: (EditableFoodItem) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = item.name,
                onValueChange = { onChange(item.copy(name = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Food") },
                enabled = enabled,
            )
            OutlinedTextField(
                value = item.quantity,
                onValueChange = { onChange(item.copy(quantity = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Quantity") },
                enabled = enabled,
            )
            NutritionFields(
                calories = item.caloriesKcal,
                amount = item.amountGml,
                fat = item.fatG,
                saturatedFat = item.saturatedFatG,
                carbs = item.carbsG,
                sugar = item.sugarG,
                protein = item.proteinG,
                salt = item.saltG,
                enabled = enabled,
                onChange = { calories, amount, fat, saturatedFat, carbs, sugar, protein, salt ->
                    onNutritionChange(
                        item.copy(
                            caloriesKcal = calories,
                            amountGml = amount,
                            fatG = fat,
                            saturatedFatG = saturatedFat,
                            carbsG = carbs,
                            sugarG = sugar,
                            proteinG = protein,
                            saltG = salt,
                        ),
                    )
                },
            )
            OutlinedTextField(
                value = item.source,
                onValueChange = { onChange(item.copy(source = it)) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Source URL") },
                enabled = enabled,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
        }
    }
}

@Composable
private fun NutritionFields(
    calories: String,
    amount: String,
    fat: String,
    saturatedFat: String,
    carbs: String,
    sugar: String,
    protein: String,
    salt: String,
    enabled: Boolean,
    readOnly: Boolean = false,
    onChange: (String, String, String, String, String, String, String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = calories,
            onValueChange = { onChange(it, amount, fat, saturatedFat, carbs, sugar, protein, salt) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Energy kcal") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            enabled = enabled,
            readOnly = readOnly,
        )
        OutlinedTextField(
            value = amount,
            onValueChange = { onChange(calories, it, fat, saturatedFat, carbs, sugar, protein, salt) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Amount (g/ml)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            enabled = enabled,
            readOnly = readOnly,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = fat,
                onValueChange = { onChange(calories, amount, it, saturatedFat, carbs, sugar, protein, salt) },
                modifier = Modifier.weight(1f),
                label = { Text("Fat g") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = enabled,
                readOnly = readOnly,
            )
            OutlinedTextField(
                value = saturatedFat,
                onValueChange = { onChange(calories, amount, fat, it, carbs, sugar, protein, salt) },
                modifier = Modifier.weight(1f),
                label = { Text("Saturates g") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = enabled,
                readOnly = readOnly,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = carbs,
                onValueChange = { onChange(calories, amount, fat, saturatedFat, it, sugar, protein, salt) },
                modifier = Modifier.weight(1f),
                label = { Text("Carbohydrate g") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = enabled,
                readOnly = readOnly,
            )
            OutlinedTextField(
                value = sugar,
                onValueChange = { onChange(calories, amount, fat, saturatedFat, carbs, it, protein, salt) },
                modifier = Modifier.weight(1f),
                label = { Text("Sugars g") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = enabled,
                readOnly = readOnly,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = protein,
                onValueChange = { onChange(calories, amount, fat, saturatedFat, carbs, sugar, it, salt) },
                modifier = Modifier.weight(1f),
                label = { Text("Protein g") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = enabled,
                readOnly = readOnly,
            )
            OutlinedTextField(
                value = salt,
                onValueChange = { onChange(calories, amount, fat, saturatedFat, carbs, sugar, protein, it) },
                modifier = Modifier.weight(1f),
                label = { Text("Salt g") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                enabled = enabled,
                readOnly = readOnly,
            )
        }
    }
}

@Composable
private fun NutritionLabel(totals: NutritionTotals, source: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        NutritionRow("Amount", "${totals.amountGml.formatNullable()} g/ml")
        NutritionRow("Energy", totals.caloriesKcal.formatEnergy())
        NutritionRow("Fat", "${totals.fatG.formatNullable()} g")
        NutritionRow("of which saturates", "${totals.saturatedFatG.formatNullable()} g")
        NutritionRow("Carbohydrate", "${totals.carbsG.formatNullable()} g")
        NutritionRow("of which sugars", "${totals.sugarG.formatNullable()} g")
        NutritionRow("Protein", "${totals.proteinG.formatNullable()} g")
        NutritionRow("Salt", "${totals.saltG.formatNullable()} g")
        if (source.isNotBlank()) SourceUrlRow("Source URL", source)
    }
}

@Composable
private fun NutritionRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
    }
}

private fun Double?.formatEnergy(): String = this?.let { kcal ->
    "${(kcal * 4.184).roundToInt()} kJ / ${kcal.formatNullable()} kcal"
} ?: "unknown"
