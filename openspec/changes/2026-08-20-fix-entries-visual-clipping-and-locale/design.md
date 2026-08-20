## Context

B6 (`docs/agent/backlog/bugs/b6-visual-clipping-and-locale.md`) was captured as a device observation with no source investigation. The investigation is now done and its findings are cited in `proposal.md`: a hand-placed FAB layer over a list with a magic bottom padding, a y-axis label column sharing the plot's exact height at the bottom edge of a card, and one locale-less `d MMM` formatter whose inconsistency comes from CLDR month abbreviations.

The binding constraint is the `visual-baseline` capability: a visual difference from the current appearance is a defect unless it is deliberately in scope. Everything here is therefore local and additive.

## Decisions

### D1: One shared definition of the action-button block's height

The buttons' height and bottom padding become named constants in `ui/entries/MealEntriesActionButtons.kt`, next to the buttons themselves, and both the buttons' `padding` and the list's `contentPadding` read them. The list additionally adds the navigation-bar inset, which the buttons already apply through `navigationBarsPadding()`.

*Alternative — bump the `116.dp` constant to a bigger number.* Lost because it is guesswork that drifts again the moment a button changes, and it still ignores the navigation-bar inset, which is device-dependent. *Alternative — measure the button layer with `onSizeChanged` and feed the height into the list.* Lost as state churn on every layout pass for a value that is static in practice.

### D2: No `Scaffold` refactor

Moving the buttons into a `Scaffold`'s `floatingActionButton` slot would let the framework handle the insets, but it restructures the whole screen — `PullToRefreshBox`, the tab row, the status-bar padding — and puts the entire entries appearance at risk. Rejected in favour of the local fix; worth revisiting only if the screen is redesigned for other reasons.

### D3: The chart's axis labels get their own vertical room

The `0` label is clipped because its column is exactly the canvas height (both 42 dp, `ui/entries/MealEntriesCharts.kt:70-80`) and the chart is the card's last child under 10 dp of padding (`ui/entries/MealEntriesStatsHeader.kt:66, 134-135`). The fix reserves room for the label's line box while keeping each label aligned with the gridline it names — the implementer picks between adding bottom padding under the chart block and centring the labels on their ticks with a taller label column, whichever holds the alignment; both are local and neither changes the plot.

*Alternative — draw the labels into the `Canvas` with `TextMeasurer`.* Lost as a rewrite of the chart's rendering for a clipping bug. *Alternative — shrink the label typography.* Lost because it changes appearance elsewhere and only postpones the clipping.

### D4: Numeric `dd.MM.` for the heatmap range labels, with an explicit locale

The observed inconsistency (`18 Mai` vs `20 Aug.`) is CLDR abbreviation data, so no amount of formatter unification fixes it — the two ends already share one formatter. A numeric pattern removes the month-name table from the picture entirely and matches the `dd.MM.yyyy` the day navigator already shows (`ui/entries/MealTimeWindows.kt:246-248`), so the screen reads consistently.

*Alternative — keep `d MMM` and pass an explicit locale.* Lost because it fixes nothing: German with an explicit locale still yields `Mai` and `Aug.`. *Alternative — full month names (`d. MMMM`).* Lost because "18. Mai" / "20. August" is too wide for a label under a 100-cell heatmap. *Alternative — a fixed German locale.* Lost because every other date in the app follows the system locale, and this label should not be the exception.

### D5: Locale stays the system locale, stated explicitly

`Locale.getDefault()` is passed at each formatter, matching `ui/components/CommonUi.kt:56-61`. The point is not to change the locale but to stop it being implicit, so the next reader can see that the choice was made.

### D6: The formatters are extracted before they are tested

`heatmapDateFormatter` and `formatHistogramTimeLabels` are `private` (`MealStatsOverview.kt:48`, `MealEntriesCharts.kt:138`), so no unit test can reach them today, which is why the locale defect shipped unnoticed. They become internal pure functions taking a `Locale`, and the tests pass several locales explicitly rather than depending on the JVM default.

### D7: Layout fixes are verified on the device, not in a test

There is no Compose UI test source in this project — `app/src/androidTest` holds only data-layer tests. Adding an instrumented UI-test source set is a tooling decision with its own cost and is out of scope; the on-device task group carries the verification instead, and this is stated rather than left implicit.

## Risks

- **The reserved bottom space is now larger, so the last row sits higher when scrolled to the end.** That is the intended change and the only appearance difference this change permits, alongside the newly visible `0` label and the new date format.
- **The meals tab's button is the injected `voiceButton` composable** (`ui/entries/MealEntriesScreen.kt:232-234`), whose height this screen does not own. The shared constant must be based on the taller of the two button variants, and the on-device check must cover both tabs.
- **A numeric date label loses the month name.** Accepted: the heatmap's two labels bound a 100-day window whose year is unambiguous, and consistency is worth more here than prose.
