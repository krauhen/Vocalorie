## 1. B1 — Pull-to-refresh re-evaluates future/past state

- [x] 1.1 In `MealEntriesScreen.kt`, replace `val now = remember(meals) { Instant.now() }` (`:111`) with `now` held in state that a refresh updates
- [x] 1.2 Wrap the entries list in a pull-to-refresh container (Compose `PullToRefresh`); its `onRefresh` reloads history and sets `now = Instant.now()`
- [x] 1.3 Confirm the reload path (`refreshHistory()` in `MealCaptureScreen.kt`) is reachable from the refresh action and that both meals and activities reload
- [ ] 1.4 Verify a future entry whose timestamp has passed loses the dashed/hatch treatment and moves into the correct day after a pull-to-refresh

## 2. F1 — Backup export/import

- [x] 2.1 Define the versioned JSON envelope (`format`, `schemaVersion` = DB version 8, `exportedAtEpochMillis`, `meals`, `activities`, `cachedMeals`, `cachedItems`); reuse existing JSON serialization, no new dependency
- [x] 2.2 Export: serialize all four tables via `MealDao.getAll()`, `ActivityDao.getAll()`, `CacheDao.getAllMeals()/getAllItems()`; exclude all secrets; write via SAF `CreateDocument("application/json")` off the main thread
- [x] 2.3 Import: parse the envelope, reject/warn on an unrecognized `schemaVersion`; read via SAF `OpenDocument`
- [x] 2.4 Import merge: build existing-key sets (ids for meals/activities, `normalizedKey`/`normalizedName` for caches) and insert only rows whose key is absent — never overwrite existing rows
- [x] 2.5 Report an imported/skipped count summary; surface a clear error on unreadable/mismatched files (no partial application)
- [x] 2.6 Add "Export data" / "Import data" to `SettingsScreen.kt` (new `SectionTitle` + `Card` + `Button`s) plumbed through the existing callback pattern; wire the launchers at the `SettingsScreen(...)` call site in `MealCaptureScreen`
- [x] 2.7 Confirm no Room schema/version change and that auto-backup rules still do NOT exclude `vocalorie.db`

## 3. F2 — Add to the selected day

- [x] 3.1 Hoist `selectedDayOffset` from `MealEntriesScreen.kt:108` up to `MealCaptureScreen`, passing it down as a hoisted state param
- [x] 3.2 Add a helper that resolves an offset to "that day at current wall-clock time" epoch millis (reusing the `MealTimeWindows` offset→date arithmetic + `LocalTime.ofInstant(now, zone)`)
- [x] 3.3 New meal save (`onSave`, `MealCaptureScreen.kt:441–467`): pass the resolved timestamp into `toEntity(createdAtEpochMillis = …)` (`MealMappers.kt:39`) instead of defaulting to now
- [x] 3.4 New activity (`openActivityEditor`, `:152–166`): seed `createdAtEpochMillis` from the resolved timestamp instead of `System.currentTimeMillis()`
- [x] 3.5 Confirm editing an existing entry still keeps its stored timestamp, and offset 0 still resolves to ~now

## 4. Tests

- [x] 4.1 F2: unit test the offset→timestamp helper (offset 0 ≈ now; past/future offsets land on the right calendar day at current time)
- [~] 4.2 B1: no pure unit seam — classification is `Instant.isAfter(now)` inline in the composable, driven by Compose state (pull-to-refresh / `LaunchedEffect`). Covered by manual verification (1.4 / 5.2) instead.
- [x] 4.3 F1: envelope round-trip preserves all four tables incl. ids; `selectNewBackupRows` skips existing keys (re-import is a no-op); unknown format / schema version / garbage are rejected (`VocalorieBackupTest`)

## 5. Verification

- [x] 5.1 Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon` and confirm all tests pass
- [ ] 5.2 Manually on emulator/device: pull-to-refresh un-crosses a just-passed entry; export then reinstall then import restores data; adding while viewing a past/future day dates the entry to that day
