---
description: After midnight the "Today" selection still points at yesterday until a save forces it to refresh.
tags: [backlog, bugs, defect, date-selection]
---

# B4: "Today" is stale across midnight

**Status:** investigated
**Source:** personal note, 2026-08-20
**Likely capability:** `openspec/specs/entry-day-targeting/spec.md` (guess, not a commitment)

## Raw note (verbatim)
> B4: On a new day "Today" does not select today but still yesterday, when adding a meal/activity it switches when saving

## What it means
Observed: with the app left open across midnight, the "Today" selection keeps pointing at the
previous day; saving a meal or activity is what finally moves it to the real today. Expected:
"Today" always resolves to the current date, so an entry never lands on the wrong day and the
displayed totals are not yesterday's.

## Open questions
- Is the current date captured once at state-holder construction and never re-evaluated?
- What should trigger re-evaluation — process resume, an `ACTION_DATE_CHANGED` receiver, a ticker?
- If the user explicitly picked a date, midnight must not override that choice; only the
  "Today" selection should follow the clock.
- Were entries actually saved onto the wrong day, or only displayed that way? That decides whether
  existing data needs correcting.

## Investigation (2026-08-20)
Root cause: `ui/entries/MealEntriesScreen.kt:84` — `var now by remember { mutableStateOf(Instant.now()) }`.
It advances only via `LaunchedEffect(meals, activities)` (`:85`) or pull-to-refresh (`:127`). Across
midnight with no save and no pull, `now` stays on yesterday.

The selected day is never stored as a date. It is an `Int` offset (`MealCaptureUiState.kt:48`, set at
`MealCaptureViewModel.kt:185`) resolved against that stale `now`:
`LocalDate.ofInstant(now, zone).minusDays(dayOffset)` (`ui/entries/MealTimeWindows.kt:77, 93`). The
label text is chosen from the offset alone (`MealTimeWindows.kt:245-246`), so it reads "Today" even
when the resolved date is yesterday. Everything keyed on `now` goes stale with it: visible entries
(`MealEntriesScreen.kt:89-90`), day window and label (`:91`), stats (`:104-105`), heatmap selection
(`:189`).

A save mutates `savedMeals`, retriggering `:85` — exactly the reported "it switches when I save".

Nothing observes the clock: no `ACTION_DATE_CHANGED` or `TIME_TICK` receiver, no ticker, no
on-resume hook. The only lifecycle observer is voice `ON_STOP` (`VoiceInputOverlay.kt:483-484`).
The view model holds a second, independent `now` (`MealCaptureUiState.kt:43`, seeded
`MealCaptureViewModel.kt:95`, advanced by `refreshNow`/`advanceNow` at `:160-168`).

**Saved data is not corrupted:** `newEntryTimestampMillis` calls `advanceNow()` for a fresh reading
(`MealCaptureViewModel.kt:174-175`), so a save at offset 0 lands on the real today. Only the display
lies. Still UNVERIFIED: a save made while the stale UI showed a nonzero offset.

Device check (2026-08-20 16:37) — the header pill reads `Today · 20.08.2026`. Note it renders the
resolved **date** next to the word, so after midnight it shows a visibly wrong date, not just a
stale word.

## Files
`MealEntriesScreen.kt`, `MealTimeWindows.kt`, `MealCaptureViewModel.kt`, `MealCaptureUiState.kt`.
