## Context

Vocalorie's meal-entries screen (`app/src/main/java/com/example/vocalorie/ui/entries/`) has a day navigator (`DayNavigator`, `MealEntriesScreen.kt`), a per-day stats header (kJ/kcal, histogram), and a 14-week heatmap (`ui/entries/stats/MealStatsOverview.kt` + `MealStatsCalculator.kt`). Meal generation runs through `KoogNutritionAgent.kt` against an OpenAI model, producing a `NutritionAgentResult` (`model/NutritionEstimateDtos.kt`) persisted via Room (`data/MealEntity.kt`, schema v4, `VocalorieDatabase.java`). Theming is a hardcoded Material3 `ColorScheme` in `ui/VocalorieTheme.kt`.

Exploration of this codebase (prior to this design) established several load-bearing facts used below:
- The heatmap grid has no click handling and no day-selection state today; day selection lives entirely in `MealEntriesScreen.kt`'s `selectedDayOffset`.
- The heatmap window is already hard-anchored to "today" as its ceiling (`heatmapStartDate`, `buildHeatmap`) — it structurally cannot show future dates without a code change, so "keep it locked to today" is a no-op to preserve, not a change to make.
- All 4 existing Room migrations are additive (`ADD COLUMN` only); there is no precedent for dropping a column.
- Almost all current "orange accent" UI already resolves through `MaterialTheme.colorScheme.primary`/`tertiary` (meal-row buckets via `mealCalorieStateStyle`, histogram, buttons); the only literal hardcoded colors outside the heatmap gradient are the `Color.White`/`Color.Black` blend endpoints in `DayNavigator`'s header gradient.
- Meal-level `source` and item-level `source` both exist today in the DTO/entity; item-level `source` is already persisted (inside the `itemsJson` blob), just not surfaced distinctly as the sole source of truth.

## Goals / Non-Goals

**Goals:**
- Let users select any visible heatmap day by tapping it, with a clear, non-cross visual indicator.
- Score each day's overall nutrition quality from calories + macros and surface that score both as heatmap color and as a number in the daily header.
- Make future-dated entries reachable and visible, with a lightweight visual cue that they're future.
- Let users retheme the whole app from 4 colors picked in Settings.
- Make meal-item sourcing reliable, granular, German-first, and item-scoped only.

**Non-Goals:**
- No offline/API food-composition database integration (F5 is prompt guidance only).
- No change to the heatmap's fixed 14-week window or its "locked to today" anchoring.
- No normalized `items` Room table — items stay serialized JSON inside `meals.itemsJson`.
- No migration/merge of old meal-level `source` values into item-level sources — they are simply dropped.
- No numeric score display on heatmap cells themselves (color only there; the number lives in the daily header).

## Decisions

**Day score formula — weighted, per-metric-normalized, triangular/plateau curves.**
Weights: calories ×10, protein ×3, carbs ×2, fat ×1 (sum 16); each metric first normalized to 0–100:
- Calories: triangular peak (100) at 2600 kcal, linear falloff to 0 at 2200 and at 3000, clamped at 0 beyond either bound.
- Protein: 0 at ≤90g, linear rise to 100 at 180g, plateaus at 100 above.
- Carbs: peak 100 at 0g, linear decay through 90g/180g reference points to 0 at 270g, clamped at 0 beyond.
- Fat: peak 100 at 0g, linear decay to 0 at 90g (the only "too much" boundary given for fat), clamped at 0 beyond.
Alternative considered: stepped/bucketed bands instead of continuous linear interpolation — rejected as coarser and harder to keep consistent between the heatmap color ramp and the numeric score shown in the header.
Days with zero logged meals are excluded from scoring entirely and rendered with the existing neutral "no data" `emptyColor`, distinct from a real 0 score.

**Heatmap selection state lives in the existing screen-level state, not new local state.**
`MealStatsOverview`/`MealStatsHeatmap` gain a `selectedDate: LocalDate?` + `onDateSelected: (LocalDate) -> Unit` parameter pair, driven by the same `selectedDayOffset` already owned by `MealEntriesScreen.kt` (converted to/from `LocalDate` at the call site). Tapping a cell calls `onDateSelected`, which the screen maps back to a `selectedDayOffset` update — this keeps a single source of truth for "which day is selected" shared between `DayNavigator` and the heatmap, rather than introducing a second, divergent selection concept.
The selected-day indicator is a new overlay drawn the same way the existing out-of-range diagonal-cross overlay is drawn (a `Canvas`/border modifier on the cell `Box`), but visually distinct (e.g. a colored ring/border) so it's never confused with the out-of-range cross.
If the selected day falls outside the fixed 14-week window (reachable via unlimited "Previous day" navigation), no cell renders the indicator; the grid's window never shifts to chase the selection.

**Future navigation removes the guard rather than special-casing it.**
`MealTimeWindows.kt`'s `require(dayOffset >= 0)` guards in `selectedDayWindow` and `selectedDayHistogramWindow` are removed (or relaxed to allow negative values), and `dayOffset == 0`'s window end changes from `now` to end-of-local-day, matching how every other offset already computes a full calendar-day window. `DayNavigator`'s `canGoNewer`/enabled logic for "Next day" no longer stops at `selectedDayOffset > 0`. The heatmap's own aggregation (`buildHeatmap`, `heatmapStartDate`) is untouched, since it already never looks past `today`.

**Future-entry highlight layers on top of, not instead of, calorie-bucket styling.**
`MealEntryRow`'s existing `mealCalorieStateStyle`-driven container/border stays; a future-dated row additionally gets a dotted `BorderStroke` (Compose supports dashed/dotted stroke via `PathEffect.dashPathEffect` on a custom border draw, since `BorderStroke` itself doesn't support dash patterns directly — this may require a custom `Modifier.drawBehind`/`border`-equivalent rather than the stock `border()` modifier). This is a UI-layer addition only; no new data field is needed since "future" is derivable from `createdAtEpochMillis` vs. now.

**Theme becomes 4-color-driven, still Material3-shaped.**
`VocalorieTheme.kt`'s hardcoded `VocalorieLightColorScheme`/`VocalorieDarkColorScheme` are replaced by a function that builds a full Material3 `ColorScheme` from 4 stored colors (Primary, Secondary, Background, Accent), generating the remaining ~16 roles (containers, on-colors, surface variants, outline) programmatically (e.g. via Material's tonal-palette/harmonization utilities or straightforward alpha/lightness derivation) so contrast stays reasonable in both light and dark. `DayNavigator`'s literal `Color.White`/`Color.Black` gradient blends switch to blending with the Background color instead. A new small third-party Compose color-picker dependency is added for the Settings picker UI (**requires explicit human approval before implementation**, per this project's dependency-approval rule) rather than hand-building an HSV/RGB picker.
Persisted via a new store in `app/src/main/java/com/example/vocalorie/settings/` (mirroring `ToolSettingsStore.kt`'s existing pattern), read at theme-application time in `VocalorieTheme.kt`.

**Meal-level `source` is removed via a table-rebuild migration, not left inert.**
Given this is a personal app with no server-side backup dependency on the meal-level `source` column, and the item-level `source` is the field of record going forward, `MIGRATION_4_5` performs the standard SQLite column-drop pattern: create a new `meals` table without `source`, copy all other columns, drop the old table, rename. This is the first non-additive migration in this codebase — call out explicitly in the PR/review that it establishes a new precedent, and that old meal-level `source` values are discarded, not merged into item-level `source` (**requires explicit human approval before implementation**, since it discards historical (if low-value) user data on migration).

**Prompt hardening is wording-only, contract-test-verified.**
`DEFAULT_SYSTEM_PROMPT` and `REQUIRED_SYSTEM_PROMPT_PHRASES` in `KoogNutritionAgent.kt` gain: (a) an explicit national-database preference list with German-first fallback ordering, (b) a stronger "always find a source, even for a generic single-food item" rule, (c) an explicit item-splitting instruction with the "coffee with milk → 2 items" example, (d) an unconditional German-output rule replacing the current bilingual "reply in query's language" rule. `NutritionPromptContractTest.kt` is updated in lockstep (including removing/relocating the assertion tied to the meal-level `source` `@LLMDescription`, since that field disappears).

## Risks / Trade-offs

- [Table-rebuild migration is new to this codebase and higher-risk than additive migrations] → Write a dedicated migration test that seeds a v4 database with rows (including populated `source` values) and asserts the v5 schema/data integrity post-migration, before touching production migration code.
- [Deriving 16 Material3 roles from 4 base colors can produce poor contrast for arbitrary user-picked colors] → Clamp/adjust derived on-colors for minimum contrast (e.g. pick black/white on-color per role based on luminance) rather than a fixed formula that assumes specific hue relationships.
- [Removing the bilingual language rule could regress non-German users who expect responses in their own language] → Explicitly confirmed as intended (B2): the app is German-only going forward for generated text; flag this as a deliberate behavior change in the PR description.
- [Fat's normalization curve only has two named reference points (45g good, 90g "too much"), unlike the other three metrics] → Documented as a modeled assumption (0 score at 90g) in the `day-nutrition-score` spec; flag for a quick sanity check once real numbers render in the heatmap.
- [Compose's stock `border()` modifier doesn't support dashed strokes directly] → Confirm during implementation whether a custom `Modifier.drawBehind` with `PathEffect.dashPathEffect` is needed, or whether a suitable Compose API/library helper already covers this; this is an implementation detail, not a spec-level concern.

## Migration Plan

1. Add `MIGRATION_4_5` (bump `@Database(version = 5)`), with a migration test seeded from realistic v4 data, before any DTO/UI changes land.
2. Land DTO/prompt/UI source changes together (removing meal-level `source` end-to-end) once the migration is verified.
3. Land heatmap/day-nav/score changes (F1–F3/B1) as they touch an overlapping but distinct code region.
4. Land the theme-palette feature last, since it's the most isolated (Settings + theme file) and depends on the approved color-picker dependency being added first.
No server-side rollback path exists (local Room DB only); a bad migration is recovered by the user reinstalling (data loss) since there's no `fallbackToDestructiveMigration` and no external backup — this is why the migration test in step 1 is called out as a prerequisite, not a nice-to-have.

## Open Questions

- Exact contrast-safe formula for deriving Material3 "on-color"/container roles from 4 arbitrary user-picked colors — left to implementation, per the risk above.
- Exact dashed-border implementation approach in Compose for the future-entry highlight — left to implementation.
- Which specific small color-picker dependency to add — left to implementation/approval step, not decided here.
