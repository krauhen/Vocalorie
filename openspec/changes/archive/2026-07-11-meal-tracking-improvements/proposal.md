## Why

Live device testing surfaced several gaps in how Vocalorie helps users understand and trust their own meal history: the day heatmap gives no way to select a day or judge nutrition quality beyond raw calories, future-dated entries silently vanish, the visual theme is hardcoded, and generated meal items lack a trustworthy per-item source. This change bundles the resulting feature requests and bug fixes because they were hardened together in one requirements pass and several share the same code regions (the meal-entries/stats screens, the AI prompt/data layer).

## What Changes

- Heatmap grid cells become tappable to select a day, with a visual selected-day indicator (not a cross, which already means "out of range" in this grid).
- The heatmap's calorie-only color gradient is replaced by a weighted day nutrition score (calories, protein, carbs, fat), which is also shown as a number in the daily stats header. **BREAKING**: removes the existing calorie-only heatmap coloring behavior.
- Day navigation is unblocked for future dates: users can navigate forward past today and future-dated entries become visible in the day list and per-day stats, with a dotted-border visual highlight on any entry with a future timestamp. The heatmap grid itself stays locked to today/past only (no change needed there).
- A new "Appearance" settings section lets users define 4 named colors (Primary, Secondary, Background, Accent) via color pickers with live previews; the entire app's Material3 theme is derived from these 4 colors instead of hardcoded hex values. Requires adding a new color-picker Compose dependency (**needs explicit approval** before implementation).
- The nutrition-estimation prompt is hardened: items are split into the maximum reasonable number of separate foods, German food-composition databases are preferred (with other listed international databases as fallback), every item must get a source (even a plain single-food meal), and all generated text is unconditionally German.
- The meal-level `source` field is removed entirely; source becomes exclusively an item-level concept, shown per item in the meal editor. **BREAKING**: requires a Room schema migration (v4 → v5) that drops the `meals.source` column via a table-rebuild migration — a new migration pattern for this codebase — and old meal-level source values are discarded (**needs explicit approval** given it discards historical data, even though it isn't recoverable/useful once item-level source is authoritative).

## Capabilities

### New Capabilities
- `day-nutrition-score`: weighted per-day nutrition quality scoring (calories/protein/carbs/fat) and its display as a number in the daily stats header.
- `future-entries`: allowing navigation to and display of future-dated meal entries, with a visual highlight distinguishing them from past/present entries.
- `theme-palette`: user-defined 4-color app theme, configurable from Settings, driving the entire Material3 `ColorScheme`.
- `food-sources`: per-item sourcing requirements for generated nutrition estimates (source preference order, always-cite-a-source rule, item-splitting rule, German-only output) and the item-level-only source data model.

### Modified Capabilities
- `meal-stats-overview`: the existing "Calendar heatmap with a fixed window" requirement is amended so heatmap cells are (a) tappable to select a day with a visual selected-day indicator, and (b) colored by the new day nutrition score instead of calories alone; a "no data" day keeps a distinct neutral color rather than being scored as worst-case.

## Impact

- **UI**: `MealStatsOverview.kt`, `MealStatsCalculator.kt`, `MealEntriesScreen.kt` (heatmap, `DayNavigator`, `MealEntryRow`, daily stats header), `MealEditor.kt` (remove meal-level source field), `SettingsScreen.kt` (new Appearance section), `VocalorieTheme.kt` (palette-driven `ColorScheme`), `CommonUi.kt` (unaffected in code but visually retinted).
- **Data/AI**: `NutritionEstimateDtos.kt` (remove meal-level `source`), `KoogNutritionAgent.kt` (prompt + required-phrases rewrite), `MealEntity.kt` / `MealMappers.kt` / `VocalorieDatabase.java` (drop `source` column via new `MIGRATION_4_5`, table-rebuild pattern).
- **Tests**: `NutritionPromptContractTest.kt`, `MealMappersTest.kt`, `MealTimeWindowsTest.kt`, `MealStatsCalculatorTest.kt` all need revisions for the new behavior.
- **Dependencies**: adds one new small third-party Compose color-picker library (approval required).
- **Settings storage**: new persisted theme-color settings, following the existing pattern in `app/src/main/java/com/example/vocalorie/settings/`.
