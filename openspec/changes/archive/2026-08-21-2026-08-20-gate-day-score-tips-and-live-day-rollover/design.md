## Context

Two defects on the entries/day surface, bundled because both are decided by what that surface believes about the day it shows: tips that never stop (B3) and a "Today" that goes stale across midnight (B4). The measured evidence is in `proposal.md`; the original backlog notes (`b3-*`, `b4-*`) were deleted on promotion and live in git history.

Constraints from `docs/agent/guidance/coding.md`: one-directional layering (UI → state holder → repository → DAO), no `Context` above the repository boundary, and extracted pure functions ship JVM-tested (`docs/agent/guidance/testing.md`).

## Decisions

### D1: The score gate lives in the pure `dayScoreTips` function, not in the header

`dayScoreTips` already owns every other condition on the list — data presence, ranking, late-day suppression (`ui/entries/stats/DayScoreTips.kt:79, 100-107`). Putting the gate there keeps one place that decides whether tips exist and makes it JVM-testable in `DayScoreTipsTest.kt`.

*Alternative — gate in `MealEntriesStatsHeader` beside the existing `if (tips.isEmpty()) return` (`:167`), where `dayScore` is already in hand (`:94-101`).* Lost because the condition would then be untestable Compose code, and a second consumer of the tip list would silently get ungated tips.

### D2: The gate reads the day score computed from the same inputs, inside the function

`dayScoreTips` already receives `totals`, `goals` and `activityBurnedKcal` — everything `nutritionScore` needs. It computes the score itself and returns empty at 50 or above.

*Alternative — pass the score in as a parameter from `withDayScoreTips` (`ui/capture/MealCaptureViewModel.kt:880-893`).* Lost because the caller could then pass a score derived from different totals than the tips, which is exactly the class of disagreement the `day-score-tips` capability exists to prevent. Computing it twice (header and tips) costs one arithmetic pass over already-summed totals.

### D3: Threshold is a hard 50 on the day's current score

*Alternative — a sliding band (fewer tips as the score rises).* Lost as unspecifiable: the backlog note asks for "below 50", and a curve would need a second table of counts per band with no user-visible reason for any particular one. *Alternative — evaluate against a projected day total.* Lost because the app has no projection model, and the projected number would be one the user cannot see on screen.

### D4: The count cap moves into the pure function, capped at three

`COLLAPSED_TIP_COUNT = 3` and `EXPANDED_TIP_COUNT = 5` (`ui/entries/MealEntriesStatsHeader.kt:149-150`) are rendering constants, so "show only the highest-impact ones" is currently an accident of the renderer. Returning at most three ranked tips makes it a property of the list, testable, and identical for every consumer.

*Alternative — keep the cap in the UI and only add the gate.* Lost because the expanded list would still show five, so the user's "only the ones that are of the most" would remain false on tap. *Alternative — cap at one.* Lost because rotation exists to show more than one and would become dead code.

### D5: The entries screen reads the state holder's clock instead of keeping its own

`MealEntriesScreen.kt:84` keeps a second, independent `now` next to `MealCaptureUiState.now` (`ui/capture/MealCaptureUiState.kt:43`). Deleting the local one removes the divergence rather than fixing it twice, and `refreshNow`/`advanceNow` (`MealCaptureViewModel.kt:160-168`) already publish new readings.

*Alternative — keep the local `now` and add a ticker to it.* Lost because the two clocks could still disagree, and the state holder's clock is the one that stamps saved entries.

### D6: Resume refresh plus a midnight-aligned tick

A lifecycle `ON_RESUME` effect calls `refreshNow()`; a coroutine computes the duration to the next local midnight, delays, advances the clock, and loops. Together they cover both ways the stale value is observed: the app was backgrounded, or it was left open on screen.

*Alternative — an `ACTION_DATE_CHANGED` receiver.* Lost because it needs a `Context` and a registered system receiver behind the entries UI, against the layering rule, for a screen-local display concern. *Alternative — a per-minute ticker.* Lost because every tick would invalidate `now`-keyed whole-history derivations (`MealEntriesScreen.kt:104-105`), which the `ui-responsiveness` capability requires be computed once per input change.

### D7: A day change re-anchors non-zero offsets so a picked date stays that date

The selection is an `Int` offset resolved against `now` (`ui/entries/MealTimeWindows.kt:77`), so advancing the clock silently reinterprets every selection. On a change of `daysPassed` days, offset 0 stays 0 (it means "today" and should follow the clock) and any other offset becomes `offset + daysPassed`, keeping its absolute date. Expressed as a pure `dayOffsetAfterDayChange(offset, daysPassed)` in `MealTimeWindows.kt` so the arithmetic, including the future-day negative-offset case, is unit-testable.

*Alternative — store the selection as a `LocalDate`.* Lost as too wide for a defect fix: the offset is threaded through the screen, the state holder, the heatmap and `selectedDayTimestampMillis` (`MealTimeWindows.kt:93`), and every one of those call sites would change. Worth doing later; not here. *Alternative — leave offsets alone at rollover.* Lost because a user reviewing yesterday would silently be moved to the day before.

## Risks

- **The gate hides tips the user previously saw.** Intended, and reversible by the threshold alone if 50 proves wrong in daily use.
- **Tips flicker around the boundary as meals are logged.** Accepted in D3; the alternative needs a projection the app cannot make.
- **The midnight coroutine and device sleep.** A delayed coroutine does not fire while the process is frozen; the `ON_RESUME` refresh is what covers that case, which is why both are needed rather than either.
