## 1. The pure date/time merge rule (entry-timestamp-editing logic)

- [ ] 1.1 Locate the existing timestamp helpers used by `EntryTimestampField` — `parseEditableTimestamp`, `formatEditableTimestamp`, `shouldResyncEditableTimestamp` and `EDITABLE_TIMESTAMP_FORMAT` (referenced from `app/src/main/java/com/example/vocalorie/ui/components/CommonUi.kt:290-321`) — and record the file that defines them; the new functions go beside them, not into the composable (design D6).
- [ ] 1.2 Add `fun mergePickedDate(currentEpochMillis: Long, pickedDateEpochMillis: Long, zone: ZoneId): Long`: take the calendar date from the picked value and the local time-of-day from the current value, and resolve them in `zone`. `zone` is an explicit parameter, never read from the default inside the function, so the test can drive it (`docs/agent/guidance/testing.md` rule 3).
- [ ] 1.3 Add `fun mergePickedTime(currentEpochMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long`: keep the current local calendar date and replace the time-of-day. Pure Kotlin, no Android types.
- [ ] 1.4 Add `app/src/test/java/com/example/vocalorie/ui/components/EntryTimestampMergeTest.kt` covering: an evening timestamp keeps 19:45 when its date moves to an earlier day; the merged value falls on the picked calendar date in the given zone; a date whose local day contains a daylight-saving transition still resolves to that calendar date; `mergePickedTime` changes only the time; and a picked date equal to the current date is a no-op. Use a fixed non-UTC zone with DST (for example `Europe/Zurich`) so the transition case is real.
- [ ] 1.5 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 2. The picker affordance in the shared field (entry-timestamp-editing UI)

- [ ] 2.1 Add a trailing calendar `IconButton` to the `OutlinedTextField` in `EntryTimestampField` (`app/src/main/java/com/example/vocalorie/ui/components/CommonUi.kt:304-320`), honouring the existing `enabled` flag so it is inert while an estimate or save is in flight. Keep the label, the format hint, the `isError` styling and the supporting text exactly as they are (design D1).
- [ ] 2.2 Add a Material 3 `DatePickerDialog` with `rememberDatePickerState`, seeded from the field's current instant, opened by that button. Set no `selectableDates` bound — the picker permits exactly what the field permits today, and the future bound belongs to `openspec/specs/future-entries/spec.md`, not here (design D4).
- [ ] 2.3 On confirm, compute the new instant with `mergePickedDate(...)` from task 1.2, then route it through the field's **existing** `onChange` and `onValidationChange` path and update the displayed `value` via `formatEditableTimestamp`, so a picked value and a typed value are indistinguishable downstream and validity is recomputed exactly once. On dismiss, change nothing.
- [ ] 2.4 Chain a Material 3 time picker after the date confirm, applying `mergePickedTime(...)` from task 1.3 through the same path. Dismissing the time step SHALL keep the already-applied date change rather than reverting it.
- [ ] 2.5 Confirm the resync guard still holds: the `LaunchedEffect(epochMillis)` at `CommonUi.kt:294-302` calls `shouldResyncEditableTimestamp(value, epochMillis)`, so a picked change arriving back as a new `epochMillis` must refresh the text rather than be suppressed as mid-typing. Read that helper and record which branch a picked change takes; adjust only if it suppresses the update.
- [ ] 2.6 Keep the merge functions out of the composable body — the dialog lambdas call them, they do not inline the arithmetic (design D6).
- [ ] 2.7 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 3. Both editors, with no call-site change (entry-timestamp-editing coverage)

- [ ] 3.1 Confirm `MealEditor.kt:112-117` needs no edit: it passes `epochMillis`, `enabled`, `onChange` and `onCreatedAtValidationChange` and reads nothing about how the value is produced. Read it and record that no change was needed.
- [ ] 3.2 Confirm `ActivityEditor.kt:69-77` needs no edit: it sets its local `isCreatedAtValid` from the same `onValidationChange`, which the picker path drives identically. Read it and record that no change was needed.
- [ ] 3.3 Confirm the save gates still behave: an entry whose timestamp was set entirely by picker is savable without the field ever having been typed into, because `onValidationChange(true)` is emitted on the picked value in task 2.3.
- [ ] 3.4 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 4. Specs, backlog and on-device confirmation

- [ ] 4.1 Install: `./gradlew :app:installDebug --no-daemon`
- [ ] 4.2 On-device: open a saved meal, tap the calendar affordance, pick a date three weeks back, confirm, then confirm the time step — and check that the entry's time-of-day is unchanged and that it now appears under the picked day in the entries list.
- [ ] 4.3 On-device: repeat in the activity editor and confirm identical behaviour, including the save gate.
- [ ] 4.4 On-device: open the picker and move past today; confirm a future date is selectable and that choosing it produces the future-entry treatment specified by `openspec/specs/future-entries/spec.md` (dashed border plus hatch fill).
- [ ] 4.5 On-device: type a half-finished timestamp so the field shows its error, then pick a date and time; confirm the error clears and the entry saves. Then type a valid timestamp by hand and confirm the old path still works unchanged.
- [ ] 4.6 On-device: rotate the device with the date dialog open and confirm the pending selection is not lost, and that the dialog's theming is legible against the active tab palette.
- [ ] 4.7 Confirm the backlog reflects the promotion — `docs/agent/backlog/features/f4-date-picker-widget-in-edit.md` carries `Status: promoted → openspec/changes/2026-08-20-date-time-picker-in-entry-editors`, and `docs/agent/backlog/features/README.md` no longer lists it under open feature requests. Done when the proposal was written; no edit expected.
- [ ] 4.8 Verify: `openspec validate 2026-08-20-date-time-picker-in-entry-editors --strict` passes, and `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon` is green.
