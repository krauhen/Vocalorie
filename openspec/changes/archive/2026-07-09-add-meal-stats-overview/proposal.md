## Why

The main view currently shows only the meal capture form and a flat list of past meal entries, with no sense of logging activity over time. A stats overview at the top of the meal history gives at-a-glance feedback on engagement (meals logged, active days, streaks) so the user can see progress and stay motivated to keep logging, similar to a usage-dashboard summary.

## What Changes

- Add a stats overview section at the top of `MealEntriesScreen`, above the existing meal list.
- Add a segmented day-range toggle: **All / 30d / 7d**, controlling the window the stats below are computed over.
- Add stat tiles: meals logged, active days, current streak, longest streak, most-common meal/food.
- Add a calendar heatmap visualizing which days (within the selected range) have at least one logged meal.
- Add aggregation logic (new `MealDao` queries and/or in-memory computation over existing `getAll()`) to derive these stats from `MealEntity.createdAtEpochMillis`.

## Capabilities

### New Capabilities
- `meal-stats-overview`: Computing and displaying meal-logging activity stats (counts, streaks, most-common meal, calendar heatmap) for a user-selected day range, shown at the top of the meal history screen.

### Modified Capabilities
(none — no existing specs recorded yet in `openspec/specs/`)

## Impact

- `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt` — new stats section composed above the meal list.
- `app/src/main/java/com/example/vocalorie/ui/MealCaptureScreen.kt` — wiring/state hoisting if the day-range selection needs to live above `MealEntriesScreen`.
- `app/src/main/java/com/example/vocalorie/data/MealDao.java` — potential new query methods for date-ranged retrieval/aggregation (currently only `getAll/getById/insert/update/delete`).
- `app/src/main/java/com/example/vocalorie/data/MealMappers.kt` — potential new mapping helpers for stats DTOs, following existing conventions.
- New UI/domain files for the stats computation and composables (exact placement decided in design.md).
- No Room schema changes anticipated (uses existing `createdAtEpochMillis` column); to be confirmed in design.md.
