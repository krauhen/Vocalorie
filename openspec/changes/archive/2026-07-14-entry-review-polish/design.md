## Context

Four independent, small-surface fixes to the meal-entry review experience, all confirmed via a clarifying interview (see conversation/decisions summarized in the proposal). None require new dependencies, migrations, or architectural changes; all are prompt/mapper/UI adjustments to existing code paths.

## Goals / Non-Goals

**Goals:**
- Make the meal title AI-generated instead of a first-item-name heuristic, while keeping it user-editable.
- Make the balance-row coloring match user expectations (deficit reads as positive/green, surplus as red).
- Stop the meal-item Source field from silently coming up blank when the AI could have supplied a real URL.
- Make the future-entry visual treatment clearly readable and consistent between meals and activities.

**Non-Goals:**
- No change to the multi-round conversational meal-entry feature (separate future change).
- No relaxing of the URL-only filter in `MealMappers.kt` — `toConcreteSourceUrlOrBlank` stays as the single source of truth for what counts as a displayable source.
- No change to the per-card calorie-magnitude bucket color system (`mealCalorieStateStyle` / `activityCalorieStateStyle`) itself — it's reused as an input color, not modified.
- No change to the "future dates are navigable" or "heatmap stays anchored to today" requirements already covered by `future-entries`.

## Decisions

### 1. AI-generated title via new DTO field, not smarter local heuristic

**Current state:** `NutritionAgentResult` (`app/src/main/java/com/example/vocalorie/model/NutritionEstimateDtos.kt:10-23`) has no title field. `MealMappers.kt:21` derives it in `toEditableDraft()` via `query.toShortMealTitle(items)`, which (lines 162-168) just returns the first item's trimmed `name`, falling back to the trimmed query if there's no item. `KoogNutritionAgent.DEFAULT_SYSTEM_PROMPT` (lines 25-50) has no title-wording instruction at all.

**Change:**
- Add `@property:LLMDescription("A short, natural-language title for the whole meal, e.g. \"Chicken Caesar Salad\".") val title: String` to `NutritionAgentResult` in `NutritionEstimateDtos.kt`, positioned after `query` (before `items`) to match the DTO's declaration order.
- Add a title-generation instruction to `DEFAULT_SYSTEM_PROMPT`, e.g.: `"Generate a short, natural title (2-5 words) summarizing the whole meal, in German, e.g. \"Hähnchen Caesar Salat\"."` — placed near the existing "Always reply in German" instruction so title language stays consistent with item names/descriptions.
- Update both `sampleResult()` and `sampleCucumberResult()` in `KoogNutritionAgent.kt` (lines 140-206) to include a `title` value, since `JsonStructure.create` uses these as few-shot examples for the structured-output schema.
- Add a new phrase to `REQUIRED_SYSTEM_PROMPT_PHRASES` (line 52-65) covering the title instruction, and add the corresponding assertion in `NutritionPromptContractTest.kt`.
- In `MealMappers.kt`, change `toEditableDraft()` (line 21) from `title = query.toShortMealTitle(items)` to `title = result.title` (the extension receiver is already `NutritionAgentResult`, so this only needs the field access, not a signature change). Leave `toEntity()` (line 43) and `SavedMeal.toEditableDraft()` (line 81) untouched — both already route through `title.resolveMealTitle(query, items.firstOrNull()?.name)`, which keeps a manually-edited title unless it's "effectively the same" as the query (lines 170-180); that guard now compares against the query as before, so AI titles that happen to equal the query text still get replaced by the heuristic fallback, which is acceptable since the two would be visually indistinguishable anyway.
- `toShortMealTitle`/`resolveMealTitle` themselves are unchanged — only the *input* fed into `toEditableDraft()`'s `title` parameter changes, from `query` to `result.title`.

Alternative considered: improve `toShortMealTitle` locally (e.g., join first 2-3 item names). Rejected — still wouldn't produce natural phrasing, and the user explicitly wants AI-authored titles.

### 2. Balance-row color swap is a pure color-mapping flip, not a rethink of thresholds

**Current state:** `MealEntriesScreen.kt:341-345`, inside `SelectableStatsHeader`:
```kotlin
EnergySummaryRow(
    label = "Balance",
    value = if (balanceCaloriesKcal >= 0.0) "+${balanceCaloriesKcal.formatNullable()} kcal surplus" else "${balanceCaloriesKcal.formatNullable()} kcal deficit",
    valueColor = if (balanceCaloriesKcal >= 0.0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
)
```
`balanceCaloriesKcal` comes from `dailyEnergyBalance(consumed, baseBurn, activitiesBurned) = consumed - baseBurn - activitiesBurned` in `MealTimeWindows.kt:222-223` — negative means deficit (consumed less than burned).

**Change:** swap only the `valueColor` ternary branches — surplus (`>= 0.0`) → `MaterialTheme.colorScheme.error`, deficit (`< 0.0`) → a positive/favorable color. `VocalorieTheme.kt` currently derives its `ColorScheme` from 4 user-defined colors (`primary`, `secondary`, `accent`→`tertiary`, `background`) plus a fixed `error` red (`VocalorieTheme.kt:90`, `Color(0xFFBA1A1A)` light / `Color(0xFFFFB4AB)` dark) — there is no pre-existing "success/positive" token in the scheme. Use `MaterialTheme.colorScheme.tertiary` (backed by the user's "Accent" theme color, `VocalorieTheme.kt:106/145`) for the deficit case rather than introducing a new hardcoded green, so the color still respects the user's custom theme palette (per `theme-palette` spec) instead of hardcoding a green that could clash with a user-chosen palette. No changes to `EnergySummaryRow`'s signature (already takes a `valueColor` param), to `dailyEnergyBalance`, or to the label strings.

### 3. Source fix targets the prompt's fallback instruction, not the mapper filter

**Current state:** `KoogNutritionAgent.DEFAULT_SYSTEM_PROMPT:39-41,45`:
```
Every food item must include a source: either a concrete food-entry page URL from one of the recommended databases, or a generic database name (like USDA or German BLS) as fallback.
For simple single-food meals, use a generic database name if no specific URL is available.
...
Prefer concrete food-entry source URLs over generic database homepages; if you only have a homepage like https://fdc.nal.usda.gov/, use the database name as source.
```
`NutritionEstimateDtos.kt:37-38`'s `FoodItemEstimate.source` LLMDescription mirrors this: `"source must be a concrete http/https food-entry page URL, or a database name like 'German BLS' or 'USDA FoodData Central'"`. Both instruct a DB-name fallback, but `MealMappers.kt:262-268`'s `toConcreteSourceUrlOrBlank` only ever keeps values that start with `http://`/`https://` **and** parse as a `java.net.URI` with a non-root path, a query, or a fragment — a bare "USDA" always gets discarded to `""`. This mismatch is the actual bug: the AI dutifully returns a DB name per the prompt, and the mapper silently throws it away.

**Change:**
- Rewrite the three prompt lines above to drop the DB-name fallback entirely, e.g.: `"Every food item's source must be a concrete http/https food-entry page URL from one of the recommended databases. If you cannot confidently identify a real URL for an item, leave source empty rather than naming a database."` and remove the "use a generic database name if no specific URL is available" / "use the database name as source" fallback sentences.
- Update `FoodItemEstimate.source`'s `@property:LLMDescription` in `NutritionEstimateDtos.kt:37` to match: `"source must be a concrete http/https food-entry page URL, or empty if no confident URL is available"`.
- Update `REQUIRED_SYSTEM_PROMPT_PHRASES` (currently includes the literal string `"Every food item must include a source"`, line 62) and `NutritionPromptContractTest.kt` to match the new wording — this is a deliberate, tracked wording change per the test's own contract-guarding purpose.
- `MealMappers.kt:260-268` (`toSourceUrlOrBlank`/`toConcreteSourceUrlOrBlank`) is **not touched** — it already implements exactly the URL-only rule the new prompt aligns with.

Alternative considered: relax the mapper filter to accept plain DB names. Rejected per user decision — URL-only stays the bar for what's "concrete" enough to show.

### 4. Future-entry treatment: additive hatch fill, same color source, extended to activities

**Current state:** `MealEntryRow` (`MealEntriesScreen.kt:677-697`) computes `style = mealCalorieStateStyle(meal.totals.caloriesKcal)` and, when `isFuture` (`Instant.ofEpochMilli(meal.createdAtEpochMillis).isAfter(now)`), applies `Modifier.drawBehind { drawRoundRect(color = style.borderColor, ..., style = Stroke(width = strokeWidthPx, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()), 0f))) }` — a dashed border only, no fill. `ActivityEntryRow` (`MealEntriesScreen.kt:738-`) computes `style = activityCalorieStateStyle(activity.caloriesBurnedKcal)` but has no future-timestamp check or highlighting at all; it doesn't even receive a `now: Instant` parameter today (only `MealEntryRow` does).

**Change:**
- Add a private composable/extension in `MealEntriesScreen.kt`, e.g. `Modifier.futureEntryHighlight(isFuture: Boolean, color: Color): Modifier`, that — when `isFuture` — draws both: (a) the existing dashed `drawRoundRect` border stroke, and (b) a diagonal hatch fill via repeated `drawLine` calls (or a `Path` of parallel diagonal segments) clipped to the row's rounded-rect bounds, both using the passed-in `color`. Spacing/stroke width tuned to resemble the user-provided reference (evenly spaced ~45° lines, dashed border on top).
- Replace `MealEntryRow`'s inline `drawBehind` block (lines 683-696) with `rowModifier = rowModifier.futureEntryHighlight(isFuture, style.borderColor)`.
- Give `ActivityEntryRow` a `now: Instant` parameter (threaded from its caller the same way `MealEntryRow` already receives `now`), compute `val isFuture = Instant.ofEpochMilli(activity.createdAtEpochMillis).isAfter(now)` (`createdAtEpochMillis: Long` is confirmed present on both `ActivityEntity.kt:9` and `SavedActivity` in `ActivityModels.kt:54`, mirroring `MealEntity`), and apply the same `Modifier.futureEntryHighlight(isFuture, style.borderColor)` using `activityCalorieStateStyle`'s `borderColor`.
- No changes to `mealCalorieStateStyle` / `activityCalorieStateStyle` themselves (`CommonUi.kt:91-145`) — both already expose a `borderColor` on their returned `MealStateStyle`, which is exactly the color the hatch/border should reuse.

Alternative considered: a neutral gray hatch, decoupled from calorie-magnitude — explicitly reverted after user feedback; magnitude visibility for planned future meals/activities is a deliberate requirement, not an oversight.

## Risks / Trade-offs

- [Risk] AI-generated titles may occasionally be lower quality than a curated heuristic for edge cases (single generic ingredient, ambiguous query) → Mitigation: user-editable field with the existing manual-edit-preserving guard covers correction; not a hard requirement for perfection.
- [Risk] Removing the DB-name fallback may increase how often Source is blank (rather than showing *a* value) → Mitigation: this is the explicitly accepted trade-off — blank-but-honest beats a fabricated/discarded value; matches user's stated preference.
- [Risk] Reusing calorie-bucket color for both border and hatch could look visually busy on already-colored rows → Mitigation: this mirrors the user-provided reference image (dashed border + diagonal hatch), and is a deliberate, twice-confirmed decision.
- [Trade-off] Sharing hatch/border drawing logic between meal and activity rows only pays off if their existing row composables can accept a shared modifier without deeper refactor; if their styling APIs diverge significantly, the shared logic may need to stay as a small utility function called from both instead of a single composable wrapper.

## Open Questions

None outstanding — all four items were resolved via the clarifying interview prior to this proposal.
