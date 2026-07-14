## 1. Stats domain model & calculator

- [x] 1.1 Add `MealStats` domain data class (meals count, active days, current streak, longest streak, top meal title, day→count map for heatmap) in a new file alongside `MealMappers.kt` conventions.
- [x] 1.2 Implement `MealStatsCalculator` (pure Kotlin, no Android deps) that takes `List<SavedMeal>` (the screen's existing domain model — no raw `MealEntity` list is available at this layer) plus a selected range (`All`/`30d`/`7d`) and returns `MealStats`, bucketing by device-local `LocalDate` via `Instant.ofEpochMilli(...).atZone(zone).toLocalDate()`.
- [x] 1.3 Implement current/longest streak logic over the *full* meal history (not range-clipped), per design.md.
- [x] 1.4 Implement most-common-meal-title logic reusing existing title normalization from `MealMappers.kt`, with deterministic tie-break (most recent among tied titles).
- [x] 1.5 Implement heatmap day-range derivation: exact last N days for 7d/30d; earliest-logged-day-through-today for "All".
- [x] 1.6 Unit tests for the calculator: empty history, single day, gap-breaking streak, exact N-day boundary, tie-break on most-common meal, multi-meal-same-day active-days counting.

## 2. Stats overview UI

- [x] 2.1 Add a segmented All/30d/7d range-selector composable (or reuse an existing Material 3 pattern if one exists in the codebase).
- [x] 2.2 Add stat-tile composables for meals logged, active days, current streak, longest streak, most-common meal — following existing Compose/Material 3 styling conventions in `MealEntriesScreen.kt`/`MealCaptureScreen.kt`.
- [x] 2.3 Add a small caption/label near the streak tiles clarifying streaks aren't limited by the range selector (per design.md risk mitigation).
- [x] 2.4 Add the calendar heatmap composable rendering day cells with intensity for meal-logged vs. not.
- [x] 2.5 Compose the stats overview section (range selector + tiles + heatmap) as a new top-level composable, e.g. `MealStatsOverview`.

## 3. Wiring

- [x] 3.1 Hoist range-selector state (`rememberSaveable`, default 30d) in `MealEntriesScreen` (or `MealCaptureScreen` only if needed for sharing).
- [x] 3.2 Insert `MealStatsOverview` above the existing meal entry list in `MealEntriesScreen.kt`, wired to the screen's existing `savedMeals` (mapped from `MealDao.getAll()` in `MealCaptureScreen`) and the range selection.
- [x] 3.3 Handle empty-state rendering (zero meals) for all tiles and the heatmap per spec scenarios.

## 4. Verification

- [x] 4.1 Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon` and fix any failures.
- [x] 4.2 Manually ran the app (`installDebug` on `emulator-5554`) and visually confirmed the stats overview, range selector (All/30d/7d), streak caption, and heatmap render with correct day spans (e.g. 7d shows exactly "3 Jul"–"9 Jul") and no crashes when switching ranges. Caught and fixed a real crash in this pass (see note below) — could not seed multi-day/streak/gap meal data in this environment (requires a live OpenAI API key to estimate+save a meal), so streak/gap-specific visuals are verified via unit tests (task 1.6) rather than on-device.
- [x] 4.3 Confirmed empty-state (no meals) renders cleanly with no crashes or error placeholders — all tiles show 0/"—" and the heatmap collapses to a single today cell.
