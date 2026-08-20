## Context

`EntryTimestampField` (`ui/components/CommonUi.kt:290-321`) is one composable, shared by both editors, holding three pieces of state:

- `value: String` in `rememberSaveable`, the text the user sees;
- `isInvalid`, keyed on `epochMillis`, driving the error styling and supporting text;
- a `LaunchedEffect(epochMillis)` that resyncs the text when the incoming instant changes, guarded by `shouldResyncEditableTimestamp(value, epochMillis)` so it does not stomp on what the user is mid-way through typing.

It reports upward through two callbacks: `onChange(Long)` with a parsed instant, and `onValidationChange(Boolean)`. Both editors wire the latter into their save gate — `MealEditor.kt:112-117` passes `onCreatedAtValidationChange` straight through, and `ActivityEditor.kt:69-77` sets a local `isCreatedAtValid` alongside it. So an unparseable string is not merely an ugly field; it blocks the save.

The field edits a **full timestamp**, date and time-of-day, in the `EDITABLE_TIMESTAMP_FORMAT` the supporting text advertises. That is the constraint that shapes every decision below: a date-only picker cannot replace the field, only feed it.

Material 3's picker APIs are unused in this repo — `DatePicker`, `DatePickerDialog`, `rememberDatePickerState`, `TimePicker` all have zero hits. This change is their first use here, so it is a new API surface even though it is not a new dependency.

## Goals / Non-Goals

**Goals:**
- A date weeks back is reachable by tapping, not typing.
- A mistyped date can no longer block a save, because the user never has to type one.
- The existing text path keeps working exactly as it does today.
- Both editors gain the affordance from one change, with no call-site edits.
- The date/time merge rule is pure and JVM-tested, not buried in a composable.

**Non-Goals:** the day navigator and heatmap, any change to which dates are permitted, removing the text field, changing stored precision, new dependencies, new-entry default timestamps. Reasons are in `proposal.md`.

## Decisions

### D1 — A modal `DatePickerDialog`, opened from a trailing icon on the existing field

**Decision.** Keep the `OutlinedTextField` and add a trailing calendar `IconButton` that opens a modal `DatePickerDialog` seeded from the current instant.

**Alternative rejected.** An inline docked date picker, always visible under the field.

**Why it lost.** Both editors are vertical `Column`s of stacked fields inside a `Card` (`ui/components/ActivityEditor.kt:65-78`, `ui/components/MealEditor.kt:100-120`); a permanently expanded calendar grid would dominate a form whose other rows are single-line fields, and would push the nutrition sections below the fold on every open. The timestamp is corrected occasionally, not on every edit, so it does not earn permanent vertical space.

**Alternative also rejected.** Replace the text field with a read-only field that only opens the dialog.

**Why it lost.** It removes the one path that works today, for the sake of tidiness. Typing is genuinely faster for a user who knows the format, and a read-only field leaves no fallback if the dialog misbehaves on a device.

### D2 — Picking a date changes only the date; the entry's time-of-day is preserved

**Decision.** The dialog returns a calendar date. The new instant is that date combined with the **time-of-day the field currently holds**, in the local zone. A separate time picker, reached after confirming the date, changes the time part the same way.

**Alternative rejected.** A single combined date-and-time dialog.

**Why it lost.** Material 3 offers no combined picker; building one means composing `DatePicker` and `TimePicker` into a custom dialog with its own state machine and its own validation — a much larger first use of an API surface this repo has never touched.

**Alternative also rejected.** Have the date pick reset the time to midnight, or to the current wall-clock time.

**Why it lost.** Both silently destroy information the user did not ask to change. Midnight is worse than it looks here: `openspec/specs/future-entries/spec.md:21` computes a day window midnight-to-midnight, and `activity-logging` already has a 23:59 default rule for a different case (`openspec/specs/activity-logging/spec.md:72`) — an entry snapping to a boundary time on a date correction is exactly the kind of quiet drift those rules exist to control.

**Consequence to call out.** The merge is a pure function over (existing instant, chosen date) and (existing instant, chosen time), evaluated in the device's local zone — the same zone the format helpers already assume. It ships with its own tests (task 1.4), because it is the one place a daylight-saving or zone mistake could move an entry to a different day than the one the user tapped.

### D3 — Change `EntryTimestampField` in place rather than adding a second component

**Decision.** Modify the shared component. Both editors pick up the picker with no change at their call sites (`ui/components/MealEditor.kt:112`, `ui/components/ActivityEditor.kt:69`).

**Alternative rejected.** Add a new `EntryTimestampPickerField` and migrate the editors one at a time.

**Why it lost.** It would produce two timestamp fields with different capabilities and one comment at `ui/components/CommonUi.kt:286-289` claiming there is one, "so both stay in step on label, format hint and error state". That drift is the exact thing the shared component was created to prevent, and there is no staged-rollout benefit in a single-user app with no store release to hedge.

### D4 — Selectable dates are inherited from existing specs, never re-decided here

**Decision.** The picker imposes no bounds of its own. It permits exactly what the text field permits today, which includes future dates.

**Alternative rejected.** Bound the picker at today, on the reasonable-seeming ground that entries describe things that already happened.

**Why it lost.** It contradicts a specified feature. `openspec/specs/future-entries/spec.md` deliberately allows navigating to and logging future-dated entries, and `entry-day-targeting` explicitly covers "adding while viewing a future day". A picker that refused those dates would make the app's newest input control the only one that disagrees with its own spec, and would break planned entries with no error message — just an unselectable day.

**Consequence to call out.** If a future bound is ever wanted, it belongs in `future-entries` as a behaviour change, not in a picker as a UI detail. This change treats that spec as the authority and is deliberately silent about it.

### D5 — This is a new capability, not a delta on `entry-day-targeting` or `ui-responsiveness`

**Decision.** Add `openspec/specs/entry-timestamp-editing/spec.md`.

**Alternative rejected.** Amend `entry-day-targeting`, the backlog file's first guess.

**Why it lost.** Read in full, that spec governs which day a **newly created** entry is filed under, deriving it from `selectedDayOffset` on the entries screen (`openspec/specs/entry-day-targeting/spec.md:10`). Its only mention of editing is the requirement that editing *preserves* the stored timestamp against that defaulting rule (`:24-26`) — which this change does not alter. Adding "how the user picks a timestamp in an editor" there would widen a capability about new-entry defaulting into one about edit controls.

**Alternative also rejected.** Amend `ui-responsiveness`, the backlog file's second guess.

**Why it lost.** That spec is about main-thread work, recomposition scope, and typed parameter grouping. It contains no requirement about any control, and a picker requirement inside it would be filed where nobody would look for it.

**Consequence to call out.** The new capability owns editable-timestamp behaviour and must stay silent about which dates are permitted (D4), or it will end up contradicting `future-entries` the first time that spec changes.

### D6 — The date-merge rule is extracted pure, beside the existing timestamp helpers

**Decision.** The merge lives as a pure function next to `parseEditableTimestamp` / `formatEditableTimestamp` / `shouldResyncEditableTimestamp`, taking a clock zone as an explicit input rather than reading the default zone inside the composable.

**Alternative rejected.** Perform the merge inline in the dialog's confirm lambda.

**Why it lost.** It puts date arithmetic — the part most likely to be subtly wrong across a DST boundary — inside a composable where no JVM test can reach it. `docs/agent/guidance/testing.md` rule 3 makes extraction-with-tests the standard for exactly this shape of logic, and the neighbouring helpers already follow it.

## Risks / Trade-offs

- **First use of `material3` picker APIs in this repo.** No local precedent for their state handling, dialog theming, or `rememberSaveable` behaviour across configuration change. Bounded by the text field remaining as a working fallback (D1).
- **Two dialogs for one value.** Date then time is more taps than a single combined picker would be. Accepted: the alternative is a custom composed dialog on an API surface this repo has never used (D2).
- **The picker's theming may not match the app's palette out of the box.** The app has its own `ThemeColors` per tab (`ui/capture/MealCaptureUiState.kt:98-104`); a Material default dialog may read as foreign until it is styled. Cosmetic, and visible immediately on the on-device check.
- **Two editing paths for one field.** Text and picker can disagree mid-edit — the user types half a date, then opens the picker. The existing `shouldResyncEditableTimestamp` guard (`ui/components/CommonUi.kt:295-297`) already exists for precisely this class of conflict, and the picker's confirm must go through the same `onChange`/`onValidationChange` path so validity is recomputed exactly once.

## Open Questions

None. D5 closes the capability question the backlog file left as a guess, by reading both candidate specs.
