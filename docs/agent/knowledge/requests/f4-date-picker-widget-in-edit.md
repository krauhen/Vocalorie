---
description: Use a proper date-picker widget for date selection in edit mode instead of the current control.
tags: [knowledge, requests, ux, date-selection]
---

# F4: Date-picker widget in edit mode

**Status:** captured
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
