---
description: Use a proper date-picker widget for date selection in edit mode instead of the current control.
tags: [backlog, features, ux, date-selection]
---

# F4: Date-picker widget in edit mode

**Status:** promoted → `openspec/changes/2026-08-20-date-time-picker-in-entry-editors`
**Source:** personal note, 2026-08-20
**Likely capability:** `openspec/specs/entry-day-targeting/spec.md`, `openspec/specs/ui-responsiveness/spec.md` (guess, not a commitment)

## Raw note (verbatim)
> F4: Use a widget for the date selection in edit mode for better UX

## What it means
When editing an entry, picking its date should use a real date-picker widget — a calendar surface
the user can jump around in — rather than the current control. Reaching a date several days or
weeks back should not mean repeated stepping.

## Open questions
- Which control is in place today, and is the problem reach, precision, or discoverability?
- Material 3 `DatePickerDialog` is the obvious fit — modal dialog or inline docked picker?
- Should the same widget replace date selection outside edit mode, or is this edit-mode only?
- Are future dates selectable here? `openspec/specs/future-entries/spec.md` already governs that
  and the picker must not widen it.

## Investigation (2026-08-20)
Confirmed: edit mode uses a **free-text field**, not a picker.

- `EntryTimestampField` (`ui/components/CommonUi.kt:290-321`) is an `OutlinedTextField` with a
  format hint and parse validation, shared by `MealEditor.kt:112` and `ActivityEditor.kt:69`.
- Material 3 `DatePicker` / `DatePickerDialog` is used **nowhere** in the codebase — zero hits
  repo-wide. This would be the first, so it is a new dependency surface on `material3` APIs.
- Outside edit mode, date picking is the three-button `DayNavigator`
  (`ui/entries/MealEntriesDayNavigator.kt:83-98`) plus a hand-rolled heatmap grid
  (`ui/entries/stats/MealStatsOverview.kt:187-228, 344-372`).

The field edits a full timestamp, date *and* time, so a date-only picker cannot replace it outright
— it needs pairing with a time picker or a date-only picker plus the retained time text.

## Pairs with B4
Both touch day selection and both are display-layer only. B4 fixes which day the screen thinks it
is on; F4 fixes how the user picks one in edit mode.

## Files
`ui/components/CommonUi.kt`, `ui/components/MealEditor.kt`, `ui/components/ActivityEditor.kt`.
