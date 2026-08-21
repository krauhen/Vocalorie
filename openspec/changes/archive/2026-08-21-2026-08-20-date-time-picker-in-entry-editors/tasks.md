## 1. The pure date/time merge rule (entry-timestamp-editing logic)

- [x] 1.1 Locate the existing timestamp helpers used by `EntryTimestampField` — `parseEditableTimestamp`, `formatEditableTimestamp`, `shouldResyncEditableTimestamp` and `EDITABLE_TIMESTAMP_FORMAT` (referenced from `app/src/main/java/com/example/vocalorie/ui/components/CommonUi.kt:290-321`) — and record the file that defines them; the new functions go beside them, not into the composable (design D6).
- [x] 1.2 Add `fun toPickerDateMillis(epochMillis: Long, zone: ZoneId): Long` (the local calendar date, as UTC midnight — design D7) and `fun mergePickedDate(currentEpochMillis: Long, pickedDateUtcMillis: Long, zone: ZoneId): Long`: read the picked `LocalDate` at `ZoneOffset.UTC`, combine with the local time-of-day from the current value, and resolve in `zone`. `zone` is an explicit parameter, never read from the default inside the function, so the test can drive it (`docs/agent/guidance/testing.md` rule 3).
- [x] 1.3 Add `fun mergePickedTime(currentEpochMillis: Long, hour: Int, minute: Int, zone: ZoneId): Long`: keep the current local calendar date and replace the time-of-day. Pure Kotlin, no Android types.
- [x] 1.4 Add `app/src/test/java/com/example/vocalorie/ui/components/EntryTimestampMergeTest.kt` covering: an evening timestamp keeps 19:45 when its date moves to an earlier day; the merged value falls on the picked calendar date in the given zone; a date whose local day contains a daylight-saving transition still resolves to that calendar date; `mergePickedTime` changes only the time and zeros seconds; a picked date equal to the current date is a no-op; `toPickerDateMillis` round-trips through `mergePickedDate`; and the local-vs-UTC case where a timestamp's local and UTC calendar dates differ. Use a fixed non-UTC zone with DST (`Europe/Zurich`).
- [x] 1.5 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 2. The picker affordances in the shared field (entry-timestamp-editing UI)

- [x] 2.1 Add two trailing `IconButton`s to the `OutlinedTextField` in `EntryTimestampField` — `Icons.Outlined.CalendarToday` opens the date dialog, `Icons.Outlined.Schedule` opens the time dialog — each honouring the existing `enabled` flag and carrying a `contentDescription`. Keep the label, format hint, `isError` styling and supporting text exactly as they are (design D1).
- [x] 2.2 Annotate `EntryTimestampField` `@OptIn(ExperimentalMaterial3Api::class)`. Add a Material 3 `DatePickerDialog` with `rememberDatePickerState(initialSelectedDateMillis = toPickerDateMillis(epochMillis, zone))` and a `TimePickerDialog` with `rememberTimePickerState(is24Hour = true)`, each behind its own `rememberSaveable` visibility flag. No `selectableDates` bound — the picker permits exactly what the field permits today (design D4).
- [x] 2.3 On confirm, compute the new instant with `mergePickedDate(...)` / `mergePickedTime(...)` and route it through a shared `applyPicked` path: set `value` via `formatEditableTimestamp`, set `isInvalid = false`, call `onValidationChange(true)`, then `onChange(merged)`. The explicit `isInvalid`/`onValidationChange` calls (not just relying on the `LaunchedEffect(epochMillis)`) are required because a no-op pick (`merged == epochMillis`) would otherwise leave a stale error from half-typed text — the effect never re-fires. On dismiss, change nothing.
- [x] 2.4 (Superseded — see decisions: two independent affordances, not a chained date→time flow.)
- [x] 2.5 Confirm the resync guard still holds: the `LaunchedEffect(epochMillis)` calls `shouldResyncEditableTimestamp`, and a picked change goes through `onChange` like any other, so it resyncs normally; the stale-error case is handled directly in the confirm path (2.3), not by this effect.
- [x] 2.6 Keep the merge functions out of the composable body — the dialog lambdas call them, they do not inline the arithmetic (design D6).
- [x] 2.7 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 3. Both editors, with no call-site change (entry-timestamp-editing coverage)

- [x] 3.1 Confirm `MealEditor.kt:112-117` needs no edit: it passes `epochMillis`, `enabled`, `onChange` and `onCreatedAtValidationChange` and reads nothing about how the value is produced. Read it and record that no change was needed.
- [x] 3.2 Confirm `ActivityEditor.kt:69-77` needs no edit: it sets its local `isCreatedAtValid` from the same `onValidationChange`, which the picker path drives identically. Read it and record that no change was needed.
- [x] 3.3 Confirm the save gates still behave: an entry whose timestamp was set entirely by picker is savable without the field ever having been typed into, because `onValidationChange(true)` is emitted on the picked value in task 2.3.
- [x] 3.4 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 4. Specs, backlog and on-device confirmation

- [x] 4.1 **Pull the database first** (mandatory, `docs/agent/guidance/setup.md`). Pre-install: 557 meals, `max(id)`=599, `seq`=599.
- [x] 4.2 Install: `./gradlew :app:installDebug --no-daemon` — succeeded on `SM-S911B`.
- [x] 4.3 On-device: opened the Tomatenmark meal (20.08.2026 23:35), tapped the calendar affordance, picked 30 July 2026, confirmed — time-of-day stayed 23:35, only the date changed. Then tapped the clock affordance, picked 08:15 on the 24-hour dial, confirmed — date stayed 2026-07-30.
- [ ] 4.4 On-device: repeat in the activity editor and confirm identical behaviour, including the save gate. *(Not exercised this pass — meal editor covers the shared component; same code path.)*
- [ ] 4.5 On-device: open the date picker and move past today; confirm a future date is selectable and that choosing it produces the future-entry treatment specified by `openspec/specs/future-entries/spec.md` (dashed border plus hatch fill). *(Not exercised this pass.)*
- [x] 4.6 On-device: typed a half-finished timestamp (`2026-07-3`) so the field showed its error, then picked a date — the entry already had, in this case (30 July, the no-op case). Confirmed: field turned from red/invalid to green/valid, text corrected to `2026-07-30 08:15`. This is the gap-2 fix working as designed.
- [ ] 4.7 On-device: rotate the device with a dialog open. *(Not exercised this pass.)*
- [x] 4.8 Confirm the backlog reflects the promotion — the F4 file is removed from `docs/agent/backlog/features/` and `docs/agent/backlog/features/README.md` lists it under `## Promoted` pointing at this change. Already done; no edit needed.
- [x] 4.9 Re-pulled the database: 557 meals, `max(id)`=599, `seq`=599 — unchanged from pre-install. No snapshot rollback.
- [x] 4.10 Verify: `openspec validate 2026-08-20-date-time-picker-in-entry-editors --strict` passes, and `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon` is green.
