## 1. Confirm the investigation against the current source

The backlog file records device observation only; these checks confirm the root causes cited in `proposal.md` still hold before anything is edited.

- [ ] 1.1 Confirm the FAB layer and the list disagree: the buttons are children of the root `Box` with `.navigationBarsPadding()` (`app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt:226-231, 238-243`), the `LazyColumn` reserves a hardcoded `bottom = 116.dp` and consumes no insets (`:142-145`), and only the top inset is handled, on the `PullToRefreshBox` (`:140`). Record the actual heights: `SettingsActionButton` is a 52 dp `TextButton` and `ActivityAddButton` an `ExtendedFloatingActionButton` (`ui/entries/MealEntriesActionButtons.kt:15-33`); measure the meals-tab `voiceButton` too, since this screen does not own it (design "Risks").
- [ ] 1.2 Confirm the chart clipping: the y-axis label column and the plot `Canvas` are both `height(42.dp)` (`ui/entries/MealEntriesCharts.kt:70-80`) with the `"0"` label bottom-aligned (`:77`), and the chart is the last child of a card padded `vertical = 10.dp` (`ui/entries/MealEntriesStatsHeader.kt:66, 134-135`). Decide between the two local fixes in design D3 — bottom padding under the chart block, or a taller label column centring labels on their ticks — and record which holds the gridline alignment.
- [ ] 1.3 Confirm the date defect is one formatter, not two: `heatmapDateFormatter = ofPattern("d MMM")` with no `Locale` (`ui/entries/stats/MealStatsOverview.kt:48`) formats both ends (`:380-381`), so `18 Mai` vs `20 Aug.` is CLDR abbreviation data (design D4). Note the other locale-less formatters for task 4: `ui/entries/MealTimeWindows.kt:246-248` and `ui/entries/MealEntriesCharts.kt:140`.
- [ ] 1.4 Confirm there is no Compose UI test source — `app/src/androidTest/java/com/example/vocalorie/data/` holds only data-layer tests — so the layout fixes are device-verified (design D7) and only the formatters get unit tests.

## 2. Reserve room for the action buttons (entries-list-presentation)

- [ ] 2.1 Add `internal val ENTRIES_ACTION_BUTTON_BLOCK_HEIGHT: Dp` and `ENTRIES_ACTION_BUTTON_BOTTOM_PADDING: Dp` to `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesActionButtons.kt`, beside the buttons they describe, set from the heights measured in 1.1 using the taller variant (design D1).
- [ ] 2.2 Use `ENTRIES_ACTION_BUTTON_BOTTOM_PADDING` in both button `Box`es in place of the literal `bottom = 20.dp` (`ui/entries/MealEntriesScreen.kt:229, 241`), leaving `.align(...)` and `.navigationBarsPadding()` exactly as they are — the buttons do not move.
- [ ] 2.3 Replace the `bottom = 116.dp` in the `LazyColumn`'s `contentPadding` (`ui/entries/MealEntriesScreen.kt:144`) with `ENTRIES_ACTION_BUTTON_BLOCK_HEIGHT + ENTRIES_ACTION_BUTTON_BOTTOM_PADDING` plus a spacing gap plus `WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()`, so the list reserves the same space the buttons occupy including the inset they already apply.
- [ ] 2.4 Leave the start/top/end `contentPadding` values (`:144`) and `verticalArrangement` (`:145`) unchanged, so nothing but the bottom reserve changes visually.
- [ ] 2.5 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 3. Unclip the chart's zero label (meal-stats-overview)

- [ ] 3.1 Apply the fix chosen in 1.2 in `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesCharts.kt:70-80`, so the bottom `"0"` label (`:77`) has room for its line box while each label still sits beside its gridline (canvas gridlines at `y = 0, h/2, h`, `:89-91`).
- [ ] 3.2 Leave the plot `Canvas` height, the bar rendering, the tick values from `niceCaloriesAxisMax` and the x-axis label row (`:107-126`) untouched — the plot itself does not change.
- [ ] 3.3 If the fix needs space from the card, add it to the chart block in `ui/entries/MealEntriesStatsHeader.kt:134-135` rather than widening the card's own `vertical = 10.dp` padding (`:66`), so no other header content shifts.
- [ ] 3.4 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 4. One date format, one explicit locale (meal-stats-overview)

- [ ] 4.1 Replace `heatmapDateFormatter` (`app/src/main/java/com/example/vocalorie/ui/entries/stats/MealStatsOverview.kt:48`) with `internal fun heatmapRangeLabel(date: LocalDate, locale: Locale = Locale.getDefault()): String` formatting `dd.MM.` — numeric, no month name, matching the day navigator's `dd.MM.yyyy` convention (`ui/entries/MealTimeWindows.kt:246-248`; design D4/D5).
- [ ] 4.2 Use it for both ends at `MealStatsOverview.kt:380-381`, so `firstLabel` and `lastLabel` are produced by one call path.
- [ ] 4.3 Pass an explicit `Locale.getDefault()` to the remaining locale-less formatters found in 1.3 — `MealTimeWindows.kt:246-248` and `MealEntriesCharts.kt:140` — following the idiom already used by `mealDateFormatter` in `ui/components/CommonUi.kt:56-61`. These patterns are numeric, so no visible output changes.
- [ ] 4.4 Promote `formatHistogramTimeLabels` (`MealEntriesCharts.kt:138`) from `private` to `internal` so it is reachable from a test (design D6). No behaviour change.
- [ ] 4.5 Add `app/src/test/java/com/example/vocalorie/ui/entries/stats/HeatmapLabelFormatTest.kt`: 18 May and 20 August produce labels of the same shape with no trailing abbreviation period on either; the same two dates render identically under `Locale.GERMAN`, `Locale.US` and `Locale.FRENCH`; no output contains a letter.
- [ ] 4.6 Add histogram-label cases beside `app/src/test/java/com/example/vocalorie/ui/entries/MealTimeWindowsTest.kt` covering `formatHistogramTimeLabels` for a normal bucket list and an empty one, with an explicit locale and zone.
- [ ] 4.7 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 5. Specs and backlog (documentation)

- [ ] 5.1 Apply this change's `entries-list-presentation` delta to `openspec/specs/entries-list-presentation/spec.md`, adding "Scrolling content clears the floating action buttons" after the existing "Row visual polish" requirement (`:31`), which is unchanged.
- [ ] 5.2 Apply this change's `meal-stats-overview` delta to `openspec/specs/meal-stats-overview/spec.md`, adding "Chart axis labels render inside their card" and "Heatmap range labels use one pattern and an explicit locale" after the existing "Calendar heatmap with a fixed window" requirement (`:62`), which is unchanged.
- [ ] 5.3 Confirm the backlog bookkeeping is already done — the `b6-*` file is deleted and `docs/agent/backlog/bugs/README.md` lists B6 under `## Promoted`, pointing at this change. The device observations and the source investigation live in this change's `proposal.md`. No edit expected.
- [ ] 5.4 Verify: `openspec validate 2026-08-20-fix-entries-visual-clipping-and-locale --strict` passes, and the main specs still parse (`openspec list --specs`).

## 6. On-device confirmation

This group is the verification for tasks 2 and 3, there being no Compose UI test source (design D7).

- [ ] 6.1 Install: `./gradlew :app:installDebug --no-daemon` on the Galaxy S23 (SM_S911B) the defect was observed on.
- [ ] 6.2 Scroll the meals list to its end and confirm the last row's energy line and macro line are fully visible, with neither the settings control nor the voice button covering any digits; repeat on the activities tab, whose Add button is taller.
- [ ] 6.3 Confirm the "Calories over time" chart's bottom `0` label is fully legible and still aligned with the chart's zero gridline, and that nothing else in the daily stats card moved.
- [ ] 6.4 Confirm the heatmap's two range labels read in the same numeric format with no month names, then switch the device language and confirm they still match each other.
- [ ] 6.5 Compare the rest of the entries screen against the pre-change appearance — rows, icons, macro colours, tiles, heatmap colours — and confirm the only differences are the larger bottom reserve, the newly visible `0`, and the new date format, per the `visual-baseline` capability.
- [ ] 6.6 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`
