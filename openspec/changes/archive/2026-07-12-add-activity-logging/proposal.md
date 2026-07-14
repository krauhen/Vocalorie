## Why

Vocalorie today only tracks energy *consumed* (meals). Users have no way to record energy *burned*, so the app cannot show a daily energy balance — the single number most useful for someone managing weight. Adding after-the-fact activity logging (plus a per-day baseline burn) closes the loop and turns the daily view into a real deficit/surplus picture.

## What Changes

- Add a two-tab layout ("Meals" / "Activities") via a Material3 `TabRow` (text + icon) placed **below** the heatmap grid. `DayNavigator`, the daily stats header, and the heatmap grid stay shared and fixed above the tabs; only the list content below switches per tab.
- Add after-the-fact **activity logging**: a new Room `ActivityEntity` / `activities` table with a full timestamp, a required type (running, walking, bike, kettlebell, gym, hiking, swimming — each with its own icon shown on the list row), a free-text title, description, kcal burned, and duration (entered in minutes, displayed as `1h 1m`).
- Repurpose the bottom-right action button (currently the voice/text meal-capture entry point) to a context-aware **"Add"**: on the Meals tab it opens meal capture as today; on the Activities tab it opens a simple manual activity form. Activity entries are viewed/edited/deleted through the same overlay pattern as meals.
- Add a configurable **base calories burned per day** setting (default 2400), a single global value stored in the existing settings-store pattern.
- Show two new display-only rows in the daily stats header: **burned** calories (base burned + sum of that day's activities, always shown) and **balance** = `consumed − base burned − activities` (negative = deficit, positive = surplus; signed and color-coded).
- Add a full second 7-color theme scheme for the Activities tab, configurable in Settings, defaulting to a black+blue analog of the meal scheme. Selecting the Activity tab swaps the **entire** entries screen to the activity scheme; switching back restores the meal scheme (one active scheme at a time).

Non-goals / explicitly unchanged: the day nutrition **score** is not affected by activities, and the meal heatmap grid is not updated (it stays meal-only).

## Capabilities

### New Capabilities
- `activity-logging`: the activity data model, the Meals/Activities tab split, the manual add/edit/delete activity flow, the required typed-with-icon activity, and same-as-meals per-day sorting.
- `energy-balance`: the configurable base-calories-burned-per-day setting and the burned/balance (deficit/surplus) figures shown in the daily stats header.

### Modified Capabilities
- `theme-palette`: adds a second independent 7-color scheme (the activity scheme, default black+blue) and a whole-screen accent swap driven by the active tab. (Also reconciles the stale "4 colors" wording in the current spec with the 7-color reality already in the code.)

## Impact

- **UI**: `ui/entries/MealEntriesScreen.kt` (new `TabRow` below the heatmap; new burned/balance rows in `SelectableStatsHeader`; the bottom-right button becomes tab-aware "Add"), a new activity list row + activity editor/overlay mirroring `ui/entries/MealEntryOverlay.kt` and `ui/components/MealEditor.kt`, `ui/MealCaptureScreen.kt` (hosts activity state + refresh + which-tab state), `ui/settings/SettingsScreen.kt` (new "Activity Appearance" section + base-burned numeric field), `ui/VocalorieTheme.kt` and `MainActivity.kt` (active-scheme selection hoisted so the theme can be told which `ThemeColors` to use).
- **Data**: new `data/ActivityEntity.kt`, `data/ActivityDao.java`, `data/ActivityMappers.kt`; `data/VocalorieDatabase.java` gains the entity, an `activityDao()`, a `MIGRATION_5_6` (additive `CREATE TABLE`), and a version bump to 6. No destructive migration.
- **Settings storage**: `settings/ThemeSettingsStore.kt` gains a second 7-color scheme (activity), plus a base-calories-burned Int.
- **Explicitly untouched**: `stats/MealStatsCalculator.kt` `nutritionScore()` and the heatmap grid in `stats/MealStatsOverview.kt`.
- **Tests**: new unit tests for activity mapping/sorting, duration formatting, and the balance/burned calculation; `UiCopyContractTest.kt` if any asserted copy changes.
- **Dependencies**: none new (reuses the existing color-picker and Compose stack).
