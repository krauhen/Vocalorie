## Why

Three things on the entries screen hide or garble information the user is looking at. Observed on a Samsung Galaxy S23 (`docs/agent/backlog/bugs/b6-visual-clipping-and-locale.md`):

- **The action buttons sit on top of the content.** The settings gear covers a meal card's energy line, so it reads `…2 kJ / 500 kcal` with the leading digits gone; the Add button cuts the Amount and Fat values off the macro line. Both buttons are hand-placed children of the screen's root `Box` (`ui/entries/MealEntriesScreen.kt:119`) with `.align(BottomEnd)` / `.align(BottomStart)` and `.navigationBarsPadding()` (`:226-231, 238-243`), while the `LazyColumn` reserves a hardcoded `bottom = 116.dp` (`:142-145`) and consumes no insets of its own — only the top is handled, on the `PullToRefreshBox` (`:140`). So the buttons are pushed up by the navigation-bar inset that the list never accounts for, and the reserved space is a constant unrelated to the buttons' real height (52 dp gear, ~56 dp `ExtendedFloatingActionButton`, `ui/entries/MealEntriesActionButtons.kt:15-33). A calorie value the user has to guess at is a data read-out that does not read out.

- **The chart's zero line has no label.** `CaloriesHistogram` draws its y axis as three `Text`s in a `Column(Modifier.height(42.dp))` with `Arrangement.SpaceBetween` (`ui/entries/MealEntriesCharts.kt:70-78`), the same 42 dp as the plot `Canvas` (`:80`). The bottom `"0"` (`:77`) therefore bottom-aligns with the canvas and has nowhere to put its line box, and the chart is the last child of a card padded only `vertical = 10.dp` (`ui/entries/MealEntriesStatsHeader.kt:66, 134-135`), so the card edge cuts it.

- **The heatmap's two date labels disagree with each other.** `18 Mai` on the left, `20 Aug.` on the right. This is **one** formatter, not two: `heatmapDateFormatter = DateTimeFormatter.ofPattern("d MMM")` with **no `Locale` argument** (`ui/entries/stats/MealStatsOverview.kt:48`), applied to both ends (`:380-381`). The inconsistency is CLDR's own abbreviated-month data for the default locale — short German months like `Mai` carry no trailing period, longer ones like `Aug.` do. Unifying formatters would not fix it; the pattern and the locale have to change.

None of this touches data. It is the cheapest class of defect to fix and the most constantly visible.

## What Changes

- **Scrolling content SHALL clear the action buttons.** The list's reserved bottom space is derived from the buttons' own height, their padding and the navigation-bar inset, from one shared definition, instead of a magic constant on one layer and an inset on the other. No meal row's values can be covered at rest.
- **The chart's axis labels SHALL render fully inside the card.** The bottom `0` label is legible in full, with the y-axis labels still lining up with the gridlines they name.
- **The heatmap's two range labels SHALL use one pattern and one explicit locale.** They adopt the app's existing numeric date convention — `dd.MM.` — matching `dd.MM.yyyy` in the day navigator (`ui/entries/MealTimeWindows.kt:246-248`), so both ends read the same way and neither depends on CLDR month-abbreviation data.
- **Every user-facing date formatter SHALL name its locale explicitly.** `heatmapDateFormatter` (`MealStatsOverview.kt:48`), the day-navigator patterns (`MealTimeWindows.kt:246-248`) and the histogram's `HH:mm` (`MealEntriesCharts.kt:140`) currently take the default implicitly; making it explicit matches `ui/components/CommonUi.kt:56-61`, which already does, and makes the choice reviewable.
- **The formatters become testable.** `heatmapDateFormatter` and `formatHistogramTimeLabels` (`MealEntriesCharts.kt:138`) are `private`, so no test can reach them; they are extracted as internal pure functions, which is what the testing guidance requires of extracted pure functions anyway.

## Capabilities

### Modified Capabilities

- `entries-list-presentation`: add a requirement that the entries list reserves space for the floating action buttons, so no row's values are occluded at rest. The existing icon, macro-colour and row-polish requirements are unchanged.
- `meal-stats-overview`: add two requirements — chart axis labels render inside their card, and the heatmap range labels use one pattern with an explicit locale. The heatmap's window, colours, tap behaviour and the existing tiles are unchanged.

## Impact

- **Three fix points.** The list's `contentPadding` and the two button `Box`es (`ui/entries/MealEntriesScreen.kt:142-145, 226-243`); the y-axis label column and its card neighbour (`ui/entries/MealEntriesCharts.kt:70-80`, `ui/entries/MealEntriesStatsHeader.kt:134-135`); and the heatmap formatter with its two call sites (`ui/entries/stats/MealStatsOverview.kt:48, 380-381`).
- **One shared dimension definition.** `internal val ENTRIES_ACTION_BUTTON_BLOCK_HEIGHT` and `ENTRIES_ACTION_BUTTON_BOTTOM_PADDING` in `ui/entries/MealEntriesActionButtons.kt`, beside the buttons they describe, consumed by both the buttons' padding and the list's `contentPadding` so the two layers cannot drift apart again. The list adds `WindowInsets.navigationBars` on top, which the buttons already apply.
- **Two extracted pure functions.** `internal fun heatmapRangeLabel(date: LocalDate, locale: Locale): String` in `MealStatsOverview.kt` and `internal fun formatHistogramTimeLabels(...)` promoted from `private` in `MealEntriesCharts.kt:138`. Plain JVM types, so both are directly unit-testable.
- **Reuses existing helpers.** The `dd.MM.` pattern follows `MealTimeWindows.kt:246-248`; the explicit-locale idiom follows `mealDateFormatter` in `ui/components/CommonUi.kt:56-61`; `navigationBarsPadding()` is already imported and used in this screen.
- **No Room change.** Nothing outside the UI layer is touched, so `BACKUP_SCHEMA_VERSION` (`data/VocalorieBackup.kt:14`) stays at 10 and no `connectedDebugAndroidTest` run is required.
- **Tests**: a new `app/src/test/java/com/example/vocalorie/ui/entries/stats/HeatmapLabelFormatTest.kt` for the range-label format under several locales, and histogram-label cases beside `app/src/test/java/com/example/vocalorie/ui/entries/MealTimeWindowsTest.kt`. There is no Compose UI test source in this project (`app/src/androidTest` holds only data-layer tests), so the layout and clipping fixes are verified on the device, per the on-device group in `tasks.md`.
- **Specs**: amend `openspec/specs/entries-list-presentation/spec.md` and `openspec/specs/meal-stats-overview/spec.md`.
- **Backlog**: B6 closes as promoted to this change.

## Non-goals

- **No redesign of the entries screen.** No `Scaffold` refactor, no repositioning or restyling of the buttons, no change to the card hierarchy — the buttons stay exactly where they are, and only the space beneath the content changes. A structural refactor would put the whole screen's appearance at risk for three local defects, against the `visual-baseline` capability.
- **No new charts or chart restyling.** Bar colours, the 42 dp plot height, the tick values and the gridlines stay; only the bottom label's legibility changes.
- **No fixed German locale.** The app's other date formatters follow the system locale (`ui/components/CommonUi.kt:56-61`); hard-coding German here would make this one label diverge from the rest for a user who switches the device language.
- **No month names anywhere.** The numeric pattern is chosen precisely so no CLDR abbreviation table can reintroduce the inconsistency.
- **No change to the heatmap's window, colours, tap behaviour or the stats tiles.** They are specified by `meal-stats-overview` and are not defective.
- **No Compose UI test infrastructure.** Adding an instrumented UI-test source set for three visual defects is a tooling decision of its own; the pure formatters are unit-tested and the layout is checked on the device.
- **Nothing about B3, B4 or B5.** Tip gating, the midnight rollover and cache-key normalization live in their own changes.
