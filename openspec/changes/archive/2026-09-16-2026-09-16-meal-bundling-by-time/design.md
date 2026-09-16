## Context

In `MealEntriesScreen.kt` (lines 88, 215-220), meals for the currently selected calendar day are obtained by calling `filterMealsForDay(meals, selectedDayOffset, now, zone)`, which filters and sorts `SavedMeal` records descending by `createdAtEpochMillis`. The screen's `LazyColumn` iterates over this list directly with `items(visibleMeals, key = { it.id }) { meal -> MealEntryRow(meal, now, onClick = { onOpenMeal(meal) }) }`.

When a user logs a multi-item meal (such as a beverage, main dish, and dessert), each item is captured as a distinct `SavedMeal` row with its own timestamp. Because entries are displayed as disconnected full-width cards, the relationship between items logged within the same eating occasion is lost, forcing the user to mentally sum nutrition values across adjacent cards.

This design introduces a pure grouping layer between `filterMealsForDay` and `LazyColumn` rendering, bundling consecutive entries whose time gap is 15 minutes or less, while preserving individual card detail and interaction.

## Goals / Non-Goals

**Goals:**
- Visually group food entries logged in close succession (gap <= 15 minutes) into cohesive meal bundles.
- Display aggregated calories and macronutrients for the bundle in a clear summary badge.
- Connect clustered items with a subtle vertical accent bracket along the leading edge.
- Retain full individual card information (title, query, category icon, macros, timestamp) and direct click-to-edit behavior.
- Implement pure, deterministic clustering with complete JVM unit test coverage.
- Preserve zero-database-impact architecture.

**Non-Goals:** Database persistence of bundles, manual bundle splitting/merging, activity clustering, cross-day bundling, or altering individual meal editing semantics. Detailed one-line rationales are in `proposal.md`.

## Decisions

### D1 — Consecutive 15-minute gap clustering algorithm

**Decision.** Group meals into the same bundle when the elapsed time between consecutive adjacent entries is 15 minutes (900,000 milliseconds) or less: `abs(t_i - t_{i+1}) <= 15.minutes.toMillis()`. Bundles chain transitively: if entry A is at 12:00, B is at 12:10, and C is at 12:22, all three belong to the same bundle because consecutive gaps (10 min, 12 min) are each <= 15 minutes.

**Alternative rejected.** Fixed clock-interval buckets (e.g., grouping by 12:00–12:15, 12:15–12:30).

**Why it lost.** Fixed clock intervals artificially split meals that straddle a clock boundary (e.g. logging a drink at 12:14 and a sandwich at 12:16 would end up in two separate bundles despite being 2 minutes apart).

**Alternative also rejected.** Fixed time limit from the first entry of a bundle (e.g. max 15 minutes from the initial item).

**Why it lost.** A multi-course lunch or dinner where courses or dessert are logged 10–12 minutes apart across a 30-minute span would be arbitrarily cut off after the first 15 minutes, failing to represent the actual meal session.

### D2 — Connected accent bracket and bundle summary badge

**Decision.** Multi-meal bundles are visually framed by a continuous vertical accent bracket along the leading edge (start padding) and topped with a bundle summary badge. The badge displays total calories, total macros (Protein, Carbs, Fat, Amount), and the bundle's time range (or entry count). Single-meal bundles (bundles with only one entry) render as standard standalone `MealEntryRow` cards without the bracket or redundant bundle badge, avoiding visual noise for solitary items.

**Alternative rejected.** Collapsible accordion/accordion group where meals are collapsed by default.

**Why it lost.** Hiding individual foods behind an expand tap degrades glanceability and adds tap friction. The primary purpose of the entries list is quick scannability of recent nutrition.

**Alternative also rejected.** Merge items into a single synthesized mega-card.

**Why it lost.** Synthesizing one card loses individual food category icons, distinct timestamps, individual query subtitles, and complicates clicking to edit a specific item.

### D3 — Retain individual entry cards and click-to-edit behavior

**Decision.** Each meal within a bundle remains an individual `MealEntryRow` card that preserves its distinct title, query, food category icon, individual calories, macros, timestamp, and click handler `onOpenMeal(meal)`.

**Alternative rejected.** Modal or bottom-sheet bundle editor where all meals in a bundle are edited together.

**Why it lost.** Vocalorie does not have a composite multi-meal entity in Room or the domain model. Editing individual meals directly reuses the well-tested, existing `MealEditor` without architectural disruption or risk of multi-record race conditions.

### D4 — Pure calculation function and immutable bundle model

**Decision.** Introduce a pure data class `MealBundle` and pure function `bundleMealsByTime` in `app/src/main/java/com/example/vocalorie/ui/entries/MealBundling.kt`:

```kotlin
data class MealBundle(
    val id: String,
    val meals: List<SavedMeal>,
    val totals: NutritionTotals,
    val startTimeEpochMillis: Long,
    val endTimeEpochMillis: Long,
) {
    val isMultiEntry: Boolean get() = meals.size > 1
}

fun bundleMealsByTime(
    meals: List<SavedMeal>,
    gapThresholdMillis: Long = 15 * 60 * 1000L,
): List<MealBundle>
```

The function runs over the day's meals, sums nutrition totals using the existing `NutritionTotals` aggregation logic, and is memoized via `remember(visibleMeals)` in `MealEntriesScreen.kt`.

**Alternative rejected.** Performing bundling logic inside the composable `LazyColumn` loop or Composable body without pure extraction.

**Why it lost.** Violates architecture rule 2 ("Composables render state and emit events. A rule that decides something is a pure function outside the composable so it is testable without either") and testing rule 3 ("A pure function extracted from a composable ships with its tests in the same commit").

### D5 — Preservation of reverse-chronological list ordering

**Decision.** `filterMealsForDay` provides meals in reverse-chronological order (newest first). The bundling algorithm consumes this sorted list and groups adjacent elements. Within each bundle, items retain their newest-first order, and the bundles themselves are ordered newest-first by their latest entry timestamp.

**Alternative rejected.** Sorting meals chronologically (oldest first) within each bundle while keeping the outer list reverse-chronological.

**Why it lost.** Mixing sort directions within the same screen is disorienting when scrolling. Consistent newest-first ordering across both the outer list and intra-bundle cards aligns with user expectation across the rest of the application.

## Risks / Trade-offs

- **Extended snacking sessions forming large bundles.** An individual continuously logging snacks every 12-14 minutes across several hours would form one large bundle. *Mitigation/Trade-off:* 15 minutes is a tight enough threshold that gaps between meals are normally 1-3+ hours; consecutive logging within 15 minutes is almost always a single meal session.
- **Visual crowding with the accent bracket on narrow screens.** A vertical bracket adds horizontal inset. *Mitigation:* Keep the bracket stroke slender (2-3 dp) with 8-12 dp start padding, leaving ample horizontal width for the card content.

## Open Questions

None. The threshold (15 minutes), visual presentation (accent bracket + bundle summary badge), data model, and pure testable architecture are fully settled.
