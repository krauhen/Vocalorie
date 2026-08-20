---
description: After midnight the "Today" selection still points at yesterday until a save forces it to refresh.
tags: [knowledge, todos, defect, date-selection]
---

# B4: "Today" is stale across midnight

**Status:** captured
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
