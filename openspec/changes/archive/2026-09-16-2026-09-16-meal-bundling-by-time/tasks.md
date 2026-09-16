## 1. Pure bundling logic & unit tests (meal-bundling logic)

- [ ] 1.1 Create `app/src/main/java/com/example/vocalorie/ui/entries/MealBundling.kt` with pure data class `MealBundle(val id: String, val meals: List<SavedMeal>, val totals: NutritionTotals, val startTimeEpochMillis: Long, val endTimeEpochMillis: Long)` and property `val isMultiEntry: Boolean get() = meals.size > 1`.
- [ ] 1.2 Implement pure function `fun bundleMealsByTime(meals: List<SavedMeal>, gapThresholdMillis: Long = 15 * 60 * 1000L): List<MealBundle>` in `MealBundling.kt`. Ensure it groups consecutive meals whose pairwise time difference is `<= gapThresholdMillis`, sums aggregate `NutritionTotals`, derives bundle time bounds, and produces a unique deterministic bundle ID.
- [ ] 1.3 Add pure-JVM unit tests in `app/src/test/java/com/example/vocalorie/ui/entries/MealBundlingTest.kt` verifying:
  - Empty input produces empty bundle list.
  - Single meal produces single single-entry bundle with matching totals.
  - Two meals with 10-minute gap group into one multi-entry bundle.
  - Two meals with 16-minute gap split into two distinct single-entry bundles.
  - Exactly 15-minute gap (`900_000L` ms) groups into one bundle (boundary test).
  - Chained meals (e.g. 0m, 12m, 24m) group into a single 3-entry bundle.
  - Multi-bundle sequence (e.g. breakfast cluster, 4-hour gap, lunch cluster) correctly forms two separate bundles.
  - Aggregate calories and macronutrients equal the sum of constituent meal totals.
- [ ] 1.4 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 2. Composable bundle UI components (entries-list-presentation UI)

- [ ] 2.1 In `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesRows.kt` (after line 180), create `MealBundleHeader(bundle: MealBundle, modifier: Modifier = Modifier)` displaying the bundle's aggregated calories, macronutrient chips/text (Fat, Carbs, Protein, Amount using `macroColors()`), entry count badge, and time span label.
- [ ] 2.2 In `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesRows.kt`, create `MealBundleBracketContainer(modifier: Modifier = Modifier, content: @Composable () -> Unit)` that draws a theme-tinted vertical accent bracket along the leading edge connecting the bundled cards, with rounded top-left and bottom-left ends.
- [ ] 2.3 Create composable `MealBundleView(bundle: MealBundle, now: Instant, onOpenMeal: (SavedMeal) -> Unit, modifier: Modifier = Modifier)` in `MealEntriesRows.kt` combining the header badge, bracket container, and child `MealEntryRow` items with appropriate spacing.
- [ ] 2.4 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 3. Screen integration in MealEntriesScreen (entries-list-presentation integration)

- [ ] 3.1 In `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt` (around line 88), derive `visibleMealBundles = remember(visibleMeals) { bundleMealsByTime(visibleMeals) }`.
- [ ] 3.2 In `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt` (lines 216-220), update the `EntriesTab.MEALS` branch of the `LazyColumn` to render `visibleMealBundles` using bundle key `bundle.id`.
- [ ] 3.3 For multi-entry bundles (`bundle.isMultiEntry`), render `MealBundleView` with the header badge and connected accent bracket; for single-entry bundles, render standard standalone `MealEntryRow(bundle.meals.first(), now, onClick = { onOpenMeal(bundle.meals.first()) })`.
- [ ] 3.4 Confirm scrolling reserve, pull-to-refresh, empty cards, and activity tab remain completely unchanged.
- [ ] 3.5 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 4. Specification & OpenSpec validation

- [ ] 4.1 Update main spec `openspec/specs/entries-list-presentation/spec.md` with the added requirements from `openspec/changes/2026-09-16-meal-bundling-by-time/specs/entries-list-presentation/spec.md`.
- [ ] 4.2 Validate change files with OpenSpec CLI: `openspec validate 2026-09-16-meal-bundling-by-time --strict`.
- [ ] 4.3 Verify full suite: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`.

## 5. On-device confirmation

- [ ] 5.1 Install debug build to connected device: `./gradlew :app:installDebug --no-daemon`.
- [ ] 5.2 Log two meals 5 minutes apart and observe they form a connected bundle with an accent bracket and aggregate calorie/macro summary badge.
- [ ] 5.3 Tap each individual meal row within the bundle and confirm it opens the correct meal editor.
- [ ] 5.4 Log an entry >15 minutes later and confirm it renders as a standalone row without bracket decoration.
- [ ] 5.5 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`
