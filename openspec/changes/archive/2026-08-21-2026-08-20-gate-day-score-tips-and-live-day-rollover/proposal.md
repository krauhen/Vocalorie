## Why

Two defects on the same surface, both about the entries screen showing something that is no longer true.

**The tips never stop.** A day scoring 95 still gets a tip. `dayScoreTips` returns empty only when the day has no data at all (`ui/entries/stats/DayScoreTips.kt:79`), and the header renders whatever comes back, checking emptiness and nothing else (`ui/entries/MealEntriesStatsHeader.kt:167`) — it never reads `dayScore`, which it only uses for the score text (`:94-101`). One band violation is enough: calories outside 0.95–1.05, carbs or fat outside 0.8–1.2, or any quality overage above zero. So the section reads as permanent nagging instead of help, and the user learns to ignore it exactly when it would matter. There is **no score gate anywhere in the codebase** — the threshold the user wants does not exist yet, it is not broken.

**"Today" lies after midnight.** `ui/entries/MealEntriesScreen.kt:84` holds `var now by remember { mutableStateOf(Instant.now()) }`, advanced only when entries reload (`:85`) or on pull-to-refresh (`:127`). Leave the app open across midnight and `now` stays on yesterday, so the header pill reads `Today · 20.08.2026` while the date is the 21st — a visibly wrong date, not merely a stale word, because the label renders the resolved date beside it. Everything keyed on `now` goes stale together: visible entries (`:89-90`), the day window and its label (`:91`), stats (`:104-105`), the heatmap selection (`:189`). Saving a meal mutates `savedMeals`, retriggers `:85` and jumps the display forward — the user's "it switches when saving".

Saved data is not corrupted: `newEntryTimestampMillis` takes a fresh reading via `advanceNow()` (`ui/capture/MealCaptureViewModel.kt:174-175`), so an entry saved at offset 0 lands on the real today. Only the display lies. They ship together because both are decided by what the entries/day surface believes about the day it is showing.

## What Changes

- **Tips appear only on a day scoring below 50.** The gate lives in the pure `dayScoreTips` function, not in the header, so the tips and the score beside them can never disagree about the same day. At or above 50 the list SHALL be empty and the section absent.
- **The gate reads the day score itself, not a re-derived proxy.** `dayScoreTips` computes it with the same `nutritionScore` the header shows (`ui/entries/stats/MealStatsCalculator.kt`), from the same totals, goals and activity burn it already receives — no new threshold and no second formula.
- **The count cap moves out of the UI into the pure function.** At most **three** tips are returned, the three highest-ranked. Ranking is unchanged — `weight × (100 − subScore)` descending (`DayScoreTips.kt:104-107`) — so "the ones that are of the most" means the top three by score cost, and the cap becomes testable instead of being a rendering accident of `COLLAPSED_TIP_COUNT = 3` / `EXPANDED_TIP_COUNT = 5` (`MealEntriesStatsHeader.kt:149-150`).
- **The threshold is evaluated against the day's score as it stands.** Tips can therefore appear and vanish as the day is logged; that is the honest reading of the current score, and a projected-total threshold would need a projection model this app does not have.
- **The entries screen reads one clock, the state holder's.** The screen's local `now` (`MealEntriesScreen.kt:84`) is replaced by `MealCaptureUiState.now` (`ui/capture/MealCaptureUiState.kt:43`), so the two independent clocks the app keeps today collapse into one.
- **That clock advances on resume and at local midnight.** A lifecycle-aware effect refreshes it when the screen resumes, and a coroutine sleeping until the next local midnight advances it as the calendar day turns — so an app left open crosses midnight without a save or a pull.
- **A day change re-anchors the selection, keeping explicit choices on their date.** The selected day is an `Int` offset (`MealCaptureUiState.kt:48`), so a rollover would silently shift every selection by one day. On a day change, offset 0 SHALL stay 0 and follow the clock, while a non-zero offset SHALL be adjusted by the number of days that passed, so a deliberately picked date stays that date.
- **No stored timestamp is rewritten.** Entry dating is already correct.

## Capabilities

### Modified Capabilities

- `day-score-tips`: amend "Tips shown only for today, only with logged meals" so the day's score below 50 is a further condition, and add the top-three cap to "Rotating presentation with tap to expand" as a property of the ranked list rather than of the renderer.
- `entry-day-targeting`: add a requirement that the viewed day tracks the wall clock — the resolved date and its label follow the real calendar day, and a rollover preserves an explicitly selected date — alongside the existing dating rule, which is unchanged.

## Impact

- **Two fix points.** `dayScoreTips` (`ui/entries/stats/DayScoreTips.kt:75-110`) gains the score gate and the cap on its return; `MealEntriesScreen` (`ui/entries/MealEntriesScreen.kt:84-85`) drops its own `now` in favour of the state holder's, and `MealCaptureViewModel` gains the rollover handler beside `refreshNow`/`advanceNow` (`:160-168`).
- **Two new pure functions.** `dayScoreTips`'s gate is expressed as `private fun tipsAllowed(score: Int): Boolean` in `DayScoreTips.kt`, and `fun dayOffsetAfterDayChange(offset: Int, daysPassed: Long): Int` in `ui/entries/MealTimeWindows.kt` beside `selectedDayWindow` (`:76-86`). Both are plain Kotlin with no Android types, so they are JVM-testable as the testing guidance requires.
- **Reuses existing helpers.** `nutritionScore` (`ui/entries/stats/MealStatsCalculator.kt`, already called at `MealEntriesScreen.kt:109-111`) supplies the gate's score; `refreshNow`/`advanceNow` (`MealCaptureViewModel.kt:160-168`) already publish a new clock reading into state, so the midnight tick and the resume refresh have a landing point and add no second clock; `selectedDayWindow` and `dateLabel` (`MealTimeWindows.kt:76-86, 245-249`) keep resolving the offset with no change.
- **No Room change.** Both fixes are presentation and state-holder logic; no entity, DAO or migration is touched, so `BACKUP_SCHEMA_VERSION` (`data/VocalorieBackup.kt:14`) stays at 10 and no `connectedDebugAndroidTest` run is required.
- **Tests**: extend `app/src/test/java/com/example/vocalorie/ui/entries/stats/DayScoreTipsTest.kt` for the gate and the cap, `app/src/test/java/com/example/vocalorie/ui/capture/DayScoreTipsStateTest.kt` for the state-holder wiring, and `app/src/test/java/com/example/vocalorie/ui/entries/MealTimeWindowsTest.kt` for the rollover re-anchoring; add a `MealCaptureViewModelTest` case for the day-change handler.
- **Specs**: amend `openspec/specs/day-score-tips/spec.md` and `openspec/specs/entry-day-targeting/spec.md`.
- **Backlog**: B3 and B4 close as promoted to this change.

## Non-goals

- **No new or reworded tip copy.** The catalogue of twelve is fixed by `day-score-tips` and pinned by tests; this change decides when tips show, never what they say.
- **No scoring-formula change.** Weights, adherence curves and the quality penalty stay as `day-nutrition-score` defines them — the gate reads the score, it does not redefine it.
- **No projected-day-total threshold.** Gating on a projection would need a model of what the user will still eat; the current score is the value shown on screen and the only one that can be checked.
- **No change to the rotation or expand interaction.** Rotation, the crossfade and the tap-to-expand behaviour stay; only the size of the list they operate on is now decided upstream.
- **No `ACTION_DATE_CHANGED` broadcast receiver.** It would put a `Context` and a system receiver behind the entries UI for a screen-local concern, against the layering rule in `docs/agent/guidance/coding.md`, and a resume refresh plus a midnight tick already covers every way the screen can be looked at.
- **No minute ticker.** One wake-up per calendar day is what the defect needs; a per-minute recomposition of whole-history derivations would fight the `ui-responsiveness` capability.
- **No correction of stored timestamps.** `newEntryTimestampMillis` already re-reads the clock, so no entry was filed on the wrong day; there is nothing to migrate.
- **Nothing about B5 or B6.** Cache-key normalization and the entries-screen visual clipping touch unrelated capabilities and stay on the backlog for their own proposals.
