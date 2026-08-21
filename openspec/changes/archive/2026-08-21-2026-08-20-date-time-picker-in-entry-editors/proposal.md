## Why

Correcting when an entry happened means typing a timestamp. `EntryTimestampField` (`ui/components/CommonUi.kt:290-321`) is an `OutlinedTextField` labelled "Added date/time" with a format hint and parse validation, and it is the *only* way to change an entry's timestamp — both the meal editor (`ui/components/MealEditor.kt:112`) and the activity editor (`ui/components/ActivityEditor.kt:69`) use it.

So a routine correction — this meal was actually last Tuesday evening — is a text-entry task: read the hint, type the date in the exact expected format, and watch the field turn red until every character matches. Getting it wrong is easy and the failure is silent in the sense that matters: the field simply refuses to parse, and the save is gated on validity through the editors' `onValidationChange` callbacks, so a mistyped date blocks the save with no way forward except retyping. A calendar the user could tap would make the whole class of error impossible.

This is worse the further back the date is. There is no stepping affordance in the editor at all — the day navigator's step buttons (`ui/entries/MealEntriesDayNavigator.kt:83-98`) are on the entries screen, not here — so a date weeks back is exactly as much typing as one yesterday, with more digits to get wrong.

Material 3 `DatePicker` is used **nowhere** in this app today; zero hits repo-wide. The editor is where a picker is most obviously missing, and it is the one place where being wrong costs the user a save.

## What Changes

- **`EntryTimestampField` gains a picker.** The field keeps its text presentation, and a trailing calendar affordance opens a Material 3 `DatePickerDialog` seeded with the currently-held instant.
- **Date and time are handled separately, because the field edits both.** Picking a date changes only the date part; the entry's existing time-of-day is carried through untouched (design D2). A time picker follows in the same flow so the whole timestamp is reachable without typing.
- **The text field stays.** It remains editable as it is today, so nothing that works now stops working, and a user who prefers typing keeps the faster path.
- **Both editors move together.** The change is inside the shared component, so the meal editor and the activity editor gain the picker with no call-site change — the reason the component was shared in the first place (`ui/components/CommonUi.kt:286-289`).
- **Selectable dates are inherited, not re-decided.** `openspec/specs/future-entries/spec.md` already governs future dating and deliberately permits it; the picker's bounds match the existing field's, which imposes none (design D4).
- **The parse and format helpers become independently tested.** `parseEditableTimestamp`, `formatEditableTimestamp` and `shouldResyncEditableTimestamp` are already pure and already used by the field; the new date-merge rule joins them as a pure function with its own JVM tests (`docs/agent/guidance/testing.md` rule 3).
- **New capability**: `openspec/specs/entry-timestamp-editing/spec.md`. Neither `entry-day-targeting` nor `ui-responsiveness` covers this surface — see design D5.
- **Backlog**: F4 closes as promoted to this change.

## Non-goals

- **No change to date selection outside the editors.** The day navigator (`ui/entries/MealEntriesDayNavigator.kt:83-98`) and the heatmap grid (`ui/entries/stats/MealStatsOverview.kt:187-228`) are separate surfaces with their own interaction models; changing them is a different ask with a different risk profile.
- **No change to which dates are permitted.** Future dating is specified by `future-entries` and is a deliberate feature; a picker is a way of choosing, not a new rule about what may be chosen.
- **No removal of the text field.** It is the only path that survives if the dialog misbehaves on a device, and it is faster for a user who knows the format.
- **No change to the stored timestamp format or precision.** Timestamps stay epoch milliseconds; this changes how one is chosen, not how it is held.
- **No new dependency.** `DatePickerDialog` and `TimePicker` ship in the `material3` artifact already on the classpath.
- **No default-timestamp or pre-fill behaviour change.** `activity-logging` already specifies the defaults for new activities (`openspec/specs/activity-logging/spec.md:72`); this change touches editing only.
- **Nothing about B4.** It also touches day selection, but it fixes which day the *screen* is on, which is a different defect on a different surface.
