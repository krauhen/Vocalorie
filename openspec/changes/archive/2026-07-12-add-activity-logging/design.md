## Context

Vocalorie's entries screen (`ui/entries/MealEntriesScreen.kt`) is a single stateless `Composable` fed a `List<SavedMeal>`, laid out as a `LazyColumn`: `DayNavigator` → `SelectableStatsHeader` (daily calories + score) → `MealStatsOverview` (14-week heatmap) → `MealEntryRow` items. There is **no** `ViewModel`, repository, `NavHost`, or tab/`TabRow` anywhere in the repo — the stateful host `ui/MealCaptureScreen.kt` owns everything via `remember { mutableStateOf(...) }` and manually refreshes from Room (`dao.getAll()` on `Dispatchers.IO`) after each write. Meals persist through a Java Room stack: `data/VocalorieDatabase.java` (`@Database(entities={MealEntity.class}, version=5, exportSchema=false)`, migrations `MIGRATION_1_2..4_5`, no `fallbackToDestructiveMigration`) and a blocking `data/MealDao.java`.

Theming is a single global 7-color `ThemeColors` (`settings/ThemeSettingsStore.kt`; roles Primary/Secondary/Accent/Background/Surface/SurfaceVariant/Outline, stored as ARGB ints in SharedPreferences `"theme_settings_store"`). `ui/VocalorieTheme.kt` builds a full M3 `ColorScheme` via a reusable `buildColorScheme(colors, isDark)`, but the `VocalorieTheme` composable self-sources `store.get()` and is applied once at the app root (`MainActivity.kt` → `VocalorieApp { VocalorieTheme { Surface { MealCaptureScreen() } } }`). Note the live `theme-palette` spec says "4 colors" but the code has 7 — the code is authoritative.

This change adds a second entity, a tab split, an energy-balance readout, and a second theme scheme with a tab-driven swap — without touching the day nutrition score or the heatmap.

## Goals / Non-Goals

**Goals:**
- Log activities after the fact (type, title, description, kcal, duration, full timestamp), edit/delete them, and see them per day sorted like meals.
- Split the day list into Meals/Activities tabs below the shared header+grid, with a tab-aware "Add" button.
- Surface a daily energy balance (consumed − base burn − activities) plus a burned figure, driven by a configurable global base-burn default.
- Give the Activities tab its own full 7-color scheme (default black+blue) and swap the whole screen's theme by active tab.

**Non-Goals:**
- No `ViewModel`/repository/`NavHost` refactor — follow the existing `remember`+manual-refresh host pattern.
- No change to `nutritionScore()` or the heatmap grid (both stay meal-only).
- No per-day base-burn override; no activity heatmap/score; no voice/AI activity capture.
- No normalized schema gymnastics — one flat `activities` table, mirroring `MealEntity`'s flat shape.

## Decisions

**Activity persistence mirrors the meal stack, additively.**
Add `data/ActivityEntity.kt` (`@Entity(tableName="activities")`, `@PrimaryKey(autoGenerate=true) id: Long = 0`, `createdAtEpochMillis: Long`, `type: String`, `title: String`, `description: String`, `caloriesBurnedKcal: Double`, `durationMinutes: Int`), a Java `data/ActivityDao.java` matching `MealDao` (`getAll()` `ORDER BY createdAtEpochMillis DESC`, `getById`, `insert`, `update`, `delete`, `deleteById`), and `data/ActivityMappers.kt` for entity↔domain (`SavedActivity`/`EditableActivityDraft`). `VocalorieDatabase` adds the entity, `activityDao()`, bumps to `version = 6`, and registers `MIGRATION_5_6` — an additive `CREATE TABLE activities (...)` (contrast with the risky table-rebuild `MIGRATION_4_5`; this one is purely additive). Type is stored as the enum name `String` (same convention as `confidence` on meals). *Alternative considered:* normalized type table — rejected as overkill for a fixed 7-value enum.

**Activity type is a Kotlin enum with an icon mapping.**
`enum class ActivityType { RUNNING, WALKING, BIKE, KETTLEBELL, GYM, HIKING, SWIMMING }` with a composable/icon lookup (Material icons where a sensible match exists; otherwise the closest available glyph). The list row shows the icon + title + kcal + `1h 1m`-formatted duration. *Alternative:* free-text type — rejected; the requirement fixes the set and wants per-type icons.

**Duration is stored as Int minutes, formatted on display.**
Store `durationMinutes: Int`; a pure `formatDuration(minutes): String` renders `h`/`m` (`61 → "1h 1m"`, `45 → "45m"`, `120 → "2h"`). Keeps arithmetic and tests trivial and unit-tested independent of Compose.

**Tabs are a Material3 `TabRow` inside the existing `LazyColumn`, state hoisted to the host.**
Insert a `TabRow` (text+icon tabs) as a `LazyColumn` item after `MealStatsOverview` and before the list items; below it the screen renders either meal rows or activity rows based on the selected tab. The selected-tab state and the activity list are hoisted to `MealCaptureScreen` (alongside `savedMeals`), so `MealEntriesScreen` stays stateless and gains `activities`, `selectedTab`, `onSelectTab`, `onOpenActivity`, and `onAddActivity` parameters. Day filtering reuses the existing `selectedDayOffset` + `filterMealsForDay`-equivalent window (a parallel `filterActivitiesForDay` using the same window logic) so both lists follow the same selected day and sort. *Alternative:* a second screen / `NavHost` — rejected; the header+grid must stay shared and there's no nav infrastructure today.

**The bottom-right button becomes tab-aware "Add".**
The button currently injected as `voiceButton()` (bottom-right `Box`) is relabeled "Add" and branches on the active tab: Meals → existing voice/text meal capture; Activities → the manual activity form. The host owns the branch. *Note:* the user refers to this as the "Add meal" button; functionally it is the existing bottom-right capture entry point being generalized.

**Activity add/edit reuses the meal overlay pattern.**
Add `ActivityEntryOverlay` (an `AlertDialog` toggling read/edit, Delete/Save/Edit/Cancel — mirroring `MealEntryOverlay`) and an `EditableActivityEditor` form (date+time via the existing `EntryTimestampField`, a type picker, title, description, kcal, duration-in-minutes). Save/delete route through the host's `Dispatchers.IO` + `refreshHistory()`-style flow (a parallel `refreshActivities()`).

**Base burn + balance are display-only, computed at the header.**
Base burn is one Int in the existing settings store (`getBaseCaloriesBurned(): Int` default 2400, `saveBaseCaloriesBurned`), edited via the numeric-field pattern already used for "Max research tool calls" in `SettingsScreen`. `SelectableStatsHeader` gains two rows below consumed calories: **burned** = `baseBurn + sum(activity kcal for the day)` (always shown), and **balance** = `consumed − baseBurn − activitiesSum`, signed and color-coded (negative=deficit, positive=surplus). A pure `dailyEnergyBalance(consumed, baseBurn, activitiesSum)` function carries the logic for unit testing. `nutritionScore()` is not touched.

**Second theme scheme + tab-driven swap: hoist scheme selection above `VocalorieTheme`.**
`ThemeSettingsStore` gains a second 7-key set (`activity_theme_*`) with `getActivityColors()/saveActivity*`, defaulting to a black+blue analog (suggested: dark Background, blue Primary e.g. `0xFF1E88E5`, lighter blue Accent e.g. `0xFF00B0FF`; exact values finalized in implementation). The existing "Appearance" section is duplicated as "Activity Appearance" (seven `ColorPickerRow`s bound to the activity setters). Because `buildColorScheme` is already reusable, the swap is achieved by making `VocalorieTheme` accept the `ThemeColors` to render instead of self-sourcing; the "which scheme is active" state is hoisted from the tab selection up to `VocalorieApp` (the theme wraps the whole app, above `MealCaptureScreen` which owns the tab). *Alternative:* nest a second `MaterialTheme` scoped only around the Activities list — rejected because the requirement is a *whole-screen* swap (header, grid, tab bar included), which a scoped inner theme can't achieve.

## Risks / Trade-offs

- **Tab selection lives below the theme boundary today** → The tab state is owned by `MealCaptureScreen`, but the theme wraps it from `VocalorieApp`. Mitigation: hoist a single `activeScheme` state to `VocalorieApp` and pass a callback down to the tab so selecting a tab updates the scheme the theme reads; keep it one source of truth to avoid divergence.
- **Manual-refresh pattern can desync two lists** → Meals and activities both refresh manually after writes. Mitigation: add `refreshActivities()` symmetric to `refreshHistory()` and call it on every activity write, exactly as meals do.
- **Icon coverage for all seven types** → Not every type has an obvious Material icon (kettlebell, hiking). Mitigation: pick the closest bundled icon per type in a single `activityTypeIcon()` mapping; refine visually on device. Purely presentational, no spec impact.
- **Balance sign confusion** → Negative-as-deficit is the chosen convention; a positive surplus could be misread. Mitigation: color-code and sign explicitly, and cover both directions with unit tests on `dailyEnergyBalance`.
- **Black+blue default legibility** → arbitrary dark backgrounds can crush contrast. Mitigation: reuse the existing luminance-based `onColorFor` derivation in `buildColorScheme` (already handles this for the meal scheme).

## Migration Plan

1. Land the additive data layer first: `ActivityEntity` + `ActivityDao` + `MIGRATION_5_6` (`CREATE TABLE`) + `version = 6`, with a mapper unit test. Purely additive — existing meals untouched, no `fallbackToDestructiveMigration`.
2. Add the settings store extensions (activity 7-color scheme + base-burn Int) and the Settings UI sections.
3. Add the tab split, activity list/row, activity overlay+editor, and the tab-aware "Add" button; hoist activity + tab state into `MealCaptureScreen`.
4. Hoist the active-scheme state to `VocalorieApp` and make `VocalorieTheme` render the passed scheme; wire the tab to it.
5. Add the burned/balance rows to `SelectableStatsHeader`, fed by the base-burn setting and the day's activities.
Rollback: local Room DB only; the additive v6 migration has no data-loss path (unlike the v4→v5 rebuild), so a rollback is simply reverting the code — existing meals remain readable.

## Open Questions

- Exact black+blue default ARGB values for the activity scheme (finalize in implementation; must stay legible via the existing on-color derivation).
- Exact Material icon chosen per activity type where no perfect match exists (kettlebell, hiking) — presentational, decided during implementation.

## Reference: key source files (quick links)

Line anchors captured from the codebase quickmaps; treat as starting points, not exact post-edit positions.

### Data layer
- [data/VocalorieDatabase.java](../../../app/src/main/java/com/example/vocalorie/data/VocalorieDatabase.java) — `@Database(entities={MealEntity.class}, version=5, exportSchema=false)`, `mealDao()`, `MIGRATION_1_2..4_5` (4_5 is the table-rebuild), `Room.databaseBuilder(...).addMigrations(...)`, singleton `get(Context)`. Add `ActivityEntity.class`, `version=6`, `activityDao()`, `MIGRATION_5_6`.
- [data/MealDao.java](../../../app/src/main/java/com/example/vocalorie/data/MealDao.java) — blocking DAO (no Flow/suspend); mirror its shape for `ActivityDao`.
- [data/MealEntity.kt](../../../app/src/main/java/com/example/vocalorie/data/MealEntity.kt) — `@Entity(tableName="meals")`, `@PrimaryKey(autoGenerate=true) id: Long = 0`, `createdAtEpochMillis: Long`; template for `ActivityEntity`.
- [data/MealMappers.kt](../../../app/src/main/java/com/example/vocalorie/data/MealMappers.kt) — top-level entity↔domain ext funcs; template for `ActivityMappers`.
- [ui/entries/MealTimeWindows.kt:85](../../../app/src/main/java/com/example/vocalorie/ui/entries/MealTimeWindows.kt) — `filterMealsForDay(...)` day-window + sort; parallel `filterActivitiesForDay`.

### Entries UI
- [ui/entries/MealEntriesScreen.kt:76](../../../app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt) — `MealEntriesScreen(meals, onOpenMeal, onOpenSettings, modifier, voiceButton)` signature.
- [ui/entries/MealEntriesScreen.kt:85](../../../app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt) — `selectedDayOffset` (shared selected-date state).
- [ui/entries/MealEntriesScreen.kt:167](../../../app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt) — bottom-right `Box` rendering the injected `voiceButton()` → the "Add" button to make tab-aware.
- [ui/entries/MealEntriesScreen.kt:225](../../../app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt) — `SelectableStatsHeader` (calories `:268`, score `:273`) → add burned/balance rows here.
- [ui/entries/MealEntriesScreen.kt:482](../../../app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt) — `DayNavigator` (gradient blend endpoints to derive from active Background).
- [ui/entries/MealEntriesScreen.kt:592](../../../app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt) — `MealEntryRow` (clickable `Card`, `onOpenMeal`); template for `ActivityEntryRow`.
- [ui/entries/MealEntryOverlay.kt:31](../../../app/src/main/java/com/example/vocalorie/ui/entries/MealEntryOverlay.kt) — `AlertDialog` toggling `isEditing`, Delete/Save/Edit/Cancel; template for `ActivityEntryOverlay`.
- [ui/components/MealEditor.kt:40](../../../app/src/main/java/com/example/vocalorie/ui/components/MealEditor.kt) — `EditableMealEditor` incl. `EntryTimestampField`; template for `EditableActivityEditor`.
- [ui/entries/stats/MealStatsOverview.kt:91](../../../app/src/main/java/com/example/vocalorie/ui/entries/stats/MealStatsOverview.kt) — `MealStatsRangeSelector` (closest existing segmented-control precedent for the `TabRow` styling).
- [ui/entries/stats/MealStatsCalculator.kt:120](../../../app/src/main/java/com/example/vocalorie/ui/entries/stats/MealStatsCalculator.kt) — `nutritionScore(totals): Double?` — **do not touch** (activities must not feed it).

### Stateful host
- [ui/MealCaptureScreen.kt](../../../app/src/main/java/com/example/vocalorie/ui/MealCaptureScreen.kt) — owns state via `remember{mutableStateOf}` (`savedMeals`, `selectedMeal`, `draft` ~`:62-66`), `dao` (`:45`), `refreshHistory()` (`:70`), renders `MealEntriesScreen` (`:230`) + `MealEntryOverlay` (`:331`,`:370`), writes at `:309`/`:386-408`. Add `activities`, `refreshActivities()`, `selectedTab` here; `showSettings` boolean toggles Settings vs entries (no `NavHost`).

### Settings + theming
- [settings/ThemeSettingsStore.kt](../../../app/src/main/java/com/example/vocalorie/settings/ThemeSettingsStore.kt) — `ThemeColors(primary, secondary, accent, background, surface, surfaceVariant, outline)`, SharedPreferences `"theme_settings_store"`, per-color ARGB key, `get()`/`save*()`/`registerListener`. Meal defaults: PRIMARY `0xFFF77605`, SECONDARY `0xFFE5E5E5`, ACCENT `0xFFFFA000`, BACKGROUND `0xFFFAFAFA`, SURFACE `0xFFFFFFFF`, SURFACE_VARIANT `0xFFE5E5E5`, OUTLINE `0xFF71717A`. Add `activity_theme_*` keys + base-burned Int.
- [ui/VocalorieTheme.kt](../../../app/src/main/java/com/example/vocalorie/ui/VocalorieTheme.kt) — reusable `buildColorScheme(colors, isDark)` (`onColorFor` luminance pick, `containerFor` lerp); `VocalorieTheme` composable self-sources `store.get()` (~`:179`) — change it to accept the `ThemeColors` to render.
- [ui/settings/SettingsScreen.kt](../../../app/src/main/java/com/example/vocalorie/ui/settings/SettingsScreen.kt) — "Appearance" `SectionTitle` + `Card` of 7 `ColorPickerRow`; the numeric-field pattern (`OutlinedTextField` `KeyboardType.Number` + Save `Button`, à la "Max research tool calls") for base-burned.
- [MainActivity.kt](../../../app/src/main/java/com/example/vocalorie/MainActivity.kt) — `VocalorieApp { VocalorieTheme { Surface { MealCaptureScreen() } } }`; hoist `activeScheme` state here so the theme can be told which scheme to render.
