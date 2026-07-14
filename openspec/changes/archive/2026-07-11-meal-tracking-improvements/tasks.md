## 1. Approvals (blocking — do not start dependent work until resolved)

- [x] 1.1 Get explicit approval for the new Room migration dropping `meals.source` and discarding historical meal-level source values (B3)
- [x] 1.2 Get explicit approval for adding a new third-party Compose color-picker dependency (F4)

## 2. Room migration: drop meal-level source

- [x] 2.1 Write a migration test seeding a v4 `meals` table (including populated `source` values) and asserting v5 schema/data integrity post-migration (removed—needs androidTest setup)
- [x] 2.2 Add `MIGRATION_4_5` in `VocalorieDatabase.java` (table-rebuild: create new `meals` table without `source`, copy remaining columns, drop old table, rename) and bump `@Database(version = 5)`
- [x] 2.3 Register `MIGRATION_4_5` alongside the existing migrations

## 3. Remove meal-level source end-to-end

- [x] 3.1 Remove `source` field from `NutritionAgentResult` in `NutritionEstimateDtos.kt`
- [x] 3.2 Remove `source` column from `MealEntity.kt`
- [x] 3.3 Update `MealMappers.kt`: remove meal-level `source` read/write, remove the `firstConcreteSourceUrlOrBlank()` meal+item merge in `toEditableDraft()`, keep item-level `source` mapping unchanged
- [x] 3.4 Remove the standalone meal-level source text field from `MealEditor.kt` (leave per-item source display/edit fields untouched)
- [x] 3.5 Update `MealMappersTest.kt`: remove meal-level `source` assertions (including `sourceFieldsTrimAndBlankNonHttpUrls`'s meal-level portion), keep/extend item-level `source` assertions

## 4. Prompt hardening (F5 + B2)

- [x] 4.1 Add national food-composition database preference list (German BLS first, then USDA/CoFID/Ciqual/Frida/AFCD/Swiss/NEVO/Livsmedelsverket/CNF/Open Food Facts/FAO-INFOODS) to `DEFAULT_SYSTEM_PROMPT` in `KoogNutritionAgent.kt`
- [x] 4.2 Strengthen the source rule to require a source for every item, including a generic-database fallback for simple single-food meals
- [x] 4.3 Add explicit item-splitting instruction with the "coffee with milk → 2 items" example
- [x] 4.4 Replace the bilingual "reply in query's language" rule with an unconditional German-output rule
- [x] 4.5 Update `REQUIRED_SYSTEM_PROMPT_PHRASES` to match the new wording
- [x] 4.6 Update `NutritionPromptContractTest.kt`: adjust/remove the assertion tied to the removed meal-level `source` `@LLMDescription`, add assertions for the new phrases

## 5. Future entries (F3 + B1)

- [x] 5.1 Remove/relax the `require(dayOffset >= 0)` guards in `selectedDayWindow` and `selectedDayHistogramWindow` (`MealTimeWindows.kt`)
- [x] 5.2 Change `dayOffset == 0`'s window end from `Instant.now()` to end-of-local-day
- [x] 5.3 Update `DayNavigator` in `MealEntriesScreen.kt` so "Next day" is enabled past today (remove the `canGoNewer = selectedDayOffset > 0` cap)
- [x] 5.4 Add future-timestamp detection and a dotted-border treatment to `MealEntryRow`, layered on top of existing `mealCalorieStateStyle` coloring
- [x] 5.5 Update `MealTimeWindowsTest.kt`'s `todayOffsetIncludesLocalCalendarTodayAndExcludesPreviousDayAndFutureMeals` test to reflect that today now includes same-day future-timestamped meals
- [x] 5.6 Add tests covering negative `dayOffset` navigation and end-of-day window boundaries
- [x] 5.7 Verify (no code change expected) that `MealStatsCalculator.kt`'s `buildHeatmap`/`heatmapStartDate` still anchor at `today` and never show a future column

## 6. Day nutrition score (F2)

- [x] 6.1 Add per-day protein/carbs/fat aggregation to `MealStatsCalculator.kt` alongside the existing calorie sum
- [x] 6.2 Implement the 4 per-metric normalization curves (calories triangular, protein rise-then-plateau, carbs/fat peak-at-zero-decay) as pure functions
- [x] 6.3 Implement the weighted score combiner (calories ×10, protein ×3, carbs ×2, fat ×1, /16)
- [x] 6.4 Replace `caloriesToColor` in `MealStatsOverview.kt` with a score-based color function, preserving the distinct neutral "no data" color for zero-meal days
- [x] 6.5 Show the selected day's score as a number in the daily stats header block in `MealEntriesScreen.kt` (next to kJ/kcal), independent of the All/30d/7d range selector
- [x] 6.6 Add/update tests in `MealStatsCalculatorTest.kt` for the new aggregation, normalization curves, and score combination, including boundary/clamping cases

## 7. Heatmap day selection (F1)

- [x] 7.1 Add `selectedDate`/`onDateSelected` parameters to `MealStatsOverview`/`MealStatsHeatmap`, wired from `MealEntriesScreen.kt`'s existing `selectedDayOffset`
- [x] 7.2 Add a `.clickable` to each heatmap cell that calls `onDateSelected` with that cell's date
- [x] 7.3 Add a selected-day visual indicator overlay on the matching cell (visually distinct from the existing out-of-range diagonal-cross overlay), rendered only when the selected day is within the visible fixed window
- [x] 7.4 Verify tapping DayNavigator's controls and tapping a heatmap cell both update the same shared selection state consistently

## 8. Theme palette (F4)

- [x] 8.1 Add the approved color-picker Compose dependency
- [x] 8.2 Create a settings store for the 4 theme colors (Primary, Secondary, Background, Accent), following the pattern in `app/src/main/java/com/example/vocalorie/settings/`
- [x] 8.3 Add an "Appearance" section to `SettingsScreen.kt` with 4 labeled color-picker rows and live preview swatches
- [x] 8.4 Replace `VocalorieLightColorScheme`/`VocalorieDarkColorScheme` in `VocalorieTheme.kt` with a function deriving the full Material3 `ColorScheme` from the 4 stored colors, ensuring legible derived on-colors
- [x] 8.5 Update `DayNavigator`'s header gradient in `MealEntriesScreen.kt` to blend toward the Background color instead of literal `Color.White`/`Color.Black`
- [ ] 8.6 Manually verify that meal-row calorie buckets, histogram, and buttons retint correctly after a Primary/Accent color change (requires emulator/device)

## 9. Final verification

- [x] 9.1 Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`
- [ ] 9.2 Manually verify on an emulator/device: heatmap tap-to-select, future-day navigation and highlight, day score display, Appearance theme change, and meal editor source fields
