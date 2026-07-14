## 1. Activity data layer (additive Room migration)

- [x] 1.1 Create `data/ActivityEntity.kt` (`@Entity(tableName="activities")`, `@PrimaryKey(autoGenerate=true) id: Long = 0`, `createdAtEpochMillis: Long`, `type: String`, `title: String`, `description: String`, `caloriesBurnedKcal: Double`, `durationMinutes: Int`) — template: `data/MealEntity.kt`
- [x] 1.2 Add `enum class ActivityType { RUNNING, WALKING, BIKE, KETTLEBELL, GYM, HIKING, SWIMMING }` and an `activityTypeIcon()` mapping (one bundled Material icon per type)
- [x] 1.3 Create `data/ActivityDao.java` mirroring `data/MealDao.java` (`getAll()` `ORDER BY createdAtEpochMillis DESC`, `getById`, `insert`, `update`, `delete`, `deleteById`)
- [x] 1.4 In `data/VocalorieDatabase.java`: add `MIGRATION_5_6` (additive `CREATE TABLE activities (...)`), add `ActivityEntity.class` to `@Database` entities, bump `version = 6`, add `activityDao()`, register in `addMigrations(...)` — mirror the existing `MIGRATION_x_y` + builder pattern
- [x] 1.5 Create `SavedActivity` / `EditableActivityDraft` domain models and `data/ActivityMappers.kt` (entity↔domain ext funcs) — template: `data/MealMappers.kt`
- [x] 1.6 Add `filterActivitiesForDay(...)` reusing the same day-window logic as `filterMealsForDay` — `ui/entries/MealTimeWindows.kt:85`

## 2. Settings: base burn + activity theme scheme

- [x] 2.1 Extend `settings/ThemeSettingsStore.kt` with a second 7-key scheme (`activity_theme_*`) + `getActivityColors()` and seven `saveActivity*` methods, defaulting to black+blue (mirror the existing `ThemeColors`/keys/`get()`/`save*()`)
- [x] 2.2 Add `getBaseCaloriesBurned(): Int` (default 2400) and `saveBaseCaloriesBurned(Int)` to `settings/ThemeSettingsStore.kt`
- [x] 2.3 Add an "Activity Appearance" section to `ui/settings/SettingsScreen.kt` duplicating the seven `ColorPickerRow`s (existing "Appearance" section) bound to the activity setters
- [x] 2.4 Add a "Base calories burned per day" numeric field to `ui/settings/SettingsScreen.kt` using the existing `OutlinedTextField` + Save `Button` numeric pattern (à la "Max research tool calls"); parse/validate in `ui/MealCaptureScreen.kt`

## 3. Tab-driven whole-screen theme swap

- [x] 3.1 Change `VocalorieTheme` to accept the `ThemeColors` to render instead of self-sourcing `store.get()` (~`ui/VocalorieTheme.kt:179`), keeping the `DisposableEffect` listener refresh; reuse `buildColorScheme(colors, isDark)`
- [x] 3.2 Hoist an `activeScheme` state to `VocalorieApp` (`MainActivity.kt`, `VocalorieApp { VocalorieTheme { Surface { MealCaptureScreen() } } }`); pass the selected scheme into `VocalorieTheme` and a setter down to the tab
- [x] 3.3 Verify `DayNavigator`'s gradient blends toward the active scheme's Background, not literal white/black — `ui/entries/MealEntriesScreen.kt:482`

## 4. Meals/Activities tabs + activity list

- [x] 4.1 Add a Material3 `TabRow` (text+icon tabs "Meals"/"Activities") as a `LazyColumn` item after `MealStatsOverview`, before the list items — `ui/entries/MealEntriesScreen.kt` (styling precedent: `MealStatsRangeSelector`, `ui/entries/stats/MealStatsOverview.kt:91`)
- [x] 4.2 Add `activities`, `selectedTab`, `onSelectTab`, `onOpenActivity`, `onAddActivity` params to `MealEntriesScreen` (`:76`); render meal rows or activity rows below the tabs by `selectedTab`
- [x] 4.3 Create an `ActivityEntryRow` composable (type icon + title + kcal + `formatDuration` duration) — template: `MealEntryRow` (`ui/entries/MealEntriesScreen.kt:592`)
- [x] 4.4 Hoist activity state into `ui/MealCaptureScreen.kt` (`activities`, `refreshActivities()` on `Dispatchers.IO`, selected tab) alongside `savedMeals` (`:62-70`); wire `onSelectTab` to update the hoisted `activeScheme`
- [x] 4.5 Relabel the bottom-right button "Add" and branch by active tab: Meals → existing meal capture; Activities → the activity form — `ui/entries/MealEntriesScreen.kt:167`

## 5. Activity add/edit/delete flow

- [x] 5.1 Create `EditableActivityEditor` form (date+time via `EntryTimestampField`, type picker, title, description, kcal, duration-in-minutes) — template: `EditableMealEditor` (`ui/components/MealEditor.kt:40`)
- [x] 5.2 Create `ActivityEntryOverlay` (`AlertDialog` toggling read/edit, Delete/Save/Edit/Cancel + read-only summary) — template: `MealEntryOverlay` (`ui/entries/MealEntryOverlay.kt:31`)
- [x] 5.3 Wire add/update/delete through the host: `dao.insert/update/deleteById` on `Dispatchers.IO` then `refreshActivities()` — mirror `ui/MealCaptureScreen.kt:309,386-408`
- [x] 5.4 Add `formatDuration(minutes): String` pure helper (`61 → "1h 1m"`, `45 → "45m"`, `120 → "2h"`)

## 6. Energy balance in the stats header

- [x] 6.1 Add a pure `dailyEnergyBalance(consumed, baseBurn, activitiesSum): Double` helper (signed result)
- [x] 6.2 Add a "burned" row to `SelectableStatsHeader` (`ui/entries/MealEntriesScreen.kt:225`) = base burn + sum of the day's activity kcal (always shown)
- [x] 6.3 Add a "balance" row (deficit/surplus) below consumed (`:268`), signed and color-coded (negative=deficit, positive=surplus), fed by the base-burn setting and the day's activities
- [x] 6.4 Confirm `nutritionScore()` (`ui/entries/stats/MealStatsCalculator.kt:120`) and the heatmap grid are unchanged (no activity input)

## 7. Tests

- [x] 7.1 Unit-test `ActivityMappers` (entity↔domain round-trip) and `filterActivitiesForDay` day/sort behavior (alongside `app/src/test/java/com/example/vocalorie/data/MealMappersTest.kt` / `ui/entries/MealTimeWindowsTest.kt`)
- [x] 7.2 Unit-test `formatDuration` (61→"1h 1m", 45→"45m", 120→"2h", 0 case)
- [x] 7.3 Unit-test `dailyEnergyBalance` for deficit, surplus, and activity-deepens-deficit cases
- [x] 7.4 Update `app/src/test/java/com/example/vocalorie/ui/UiCopyContractTest.kt` if any asserted copy changed (tab labels, "Add" button)

## 8. Verification

- [x] 8.1 Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`
- [x] 8.2 Manually verify on emulator/device: tab switch + whole-screen theme swap, add/edit/delete an activity, duration display, burned/balance figures, base-burn setting, activity color-scheme persistence, and that score/heatmap are unaffected
