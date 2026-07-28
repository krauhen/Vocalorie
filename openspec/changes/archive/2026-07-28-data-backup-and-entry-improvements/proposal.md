## Why

Three issues surfaced in daily use, all touching how entries are dated, refreshed, and preserved:

- **B1 — future entries don't un-cross on their own.** Future-dated entries render with the dashed/hatch "planned" treatment. That treatment is derived from an `Instant.now()` that is memoized against the loaded meal list (`MealEntriesScreen.kt:111`, `remember(meals) { Instant.now() }`), so `now` only advances when a write changes the list. A planned meal whose time has since passed therefore stays crossed out until the next save happens to reload the list — it never updates just because time moved forward.
- **F1 — no user-controlled backup.** Data lives only in the local Room DB. Android auto-backup is enabled and does not exclude the DB, but it is opaque, account/quota-dependent, and gives the user no explicit control, so an uninstall/reinstall can silently lose everything.
- **F2 — new entries always land on today.** When viewing a past or future day in the history screen, adding a meal or activity still timestamps it "now", because `selectedDayOffset` is local to `MealEntriesScreen` and never reaches the save path (`MealMappers.kt:39` defaults to `System.currentTimeMillis()`).

These are bundled because B1 and F2 both hinge on the same date/refresh handling, and F1 rounds out durable persistence of the same entry data.

## What Changes

- **B1 — pull-to-refresh re-evaluates the current instant.** Add a pull-to-refresh (swipe-down, news-feed style) gesture to the entries screen. Pulling reloads entries from the DB **and** recomputes the current instant used for future/past classification, so entries whose timestamp has passed lose the crossed-out treatment and rejoin the present. Replaces the `remember(meals)`-memoized `now` with an instant that the refresh gesture (and any reload) advances.
- **F1 — JSON export/import in Settings, plus keep auto-backup.** Add "Export data" and "Import data" actions to Settings. Export writes a single JSON file (via the system file picker / SAF) containing all user data — meals, activities, and the reuse caches (`cached_meals`, `cached_items`) — and never includes secrets (OpenAI/Brave keys stay out). Import merges the file into the current DB, matching on each row's stable primary key and **skipping any row whose key already exists** (existing data is never overwritten; re-importing the same file is a no-op). Android auto-backup stays enabled as a passive net; the DB remains un-excluded from backup rules.
- **F2 — add to the viewed day.** New meals and activities take the currently-viewed day's date with the current wall-clock time. Viewing today is unchanged (offset 0 resolves to now). `selectedDayOffset` is hoisted so the save path can resolve the target timestamp.

Out of scope: replace-all or interactive-choice import modes (merge/skip only); content-based dedupe (id-based only); a graphical date/time picker during capture (time stays "now"); changing how the nutrition score or stats are computed; any Room schema/version change (F1 adds no table); a resume/midnight auto-refresh for B1 (pull-to-refresh is the only new trigger).

## Approvals obtained

- No new dependencies expected. Export/import reuses the JSON serialization already used for `itemsJson` and the SAF `ActivityResult` contracts from the framework. If a new serialization or file dependency turns out to be required, it will be flagged for explicit approval before adding.

## Capabilities

### New Capabilities
- `data-backup`: user-initiated JSON export of all entry and cache data (excluding secrets), and merge-on-import that inserts only rows whose primary key is not already present.
- `entry-day-targeting`: newly created meals and activities are dated to the day currently being viewed, at the current wall-clock time.

### Modified Capabilities
- `future-entries`: the future/past classification of entries re-evaluates when the entries screen is refreshed (via a new pull-to-refresh gesture), not only when a write reloads the list.

## Impact

- `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt` — replace `val now = remember(meals) { Instant.now() }` (`:111`) with an instant that advances on refresh; wrap the entries list in a pull-to-refresh container whose refresh reloads history and recomputes `now`. Hoist `selectedDayOffset` (`:108`) out to the parent (F2).
- `app/src/main/java/com/example/vocalorie/ui/MealCaptureScreen.kt` — own the refresh action (`refreshHistory()`), own the hoisted `selectedDayOffset`, and resolve the target timestamp for new meals (`onSave`, `:441–467`) and new activities (`openActivityEditor`, `:152–166`).
- `app/src/main/java/com/example/vocalorie/data/MealMappers.kt` — allow the new-meal save path to pass an explicit `createdAtEpochMillis` (the resolved selected-day timestamp) instead of always defaulting to now (`:39`).
- `app/src/main/java/com/example/vocalorie/ui/settings/SettingsScreen.kt` — add an "Export data" / "Import data" section (`SectionTitle` + `Card` + `Button`s), plumbed through new lambda params mirroring the existing callbacks; wire at the `SettingsScreen(...)` call site in `MealCaptureScreen`.
- New backup module (F1) — serialize/deserialize a versioned JSON envelope over `MealDao.getAll()`, `ActivityDao.getAll()`, `CacheDao.getAllMeals()/getAllItems()`, and single-row `insert`/`upsert`, filtering incoming rows by existing primary keys. Runs off the main thread; uses SAF `CreateDocument`/`OpenDocument` launchers.
- Tests — `MealTimeWindows`/offset→timestamp resolution (F2); future/past classification against a movable `now` (B1); export→import round-trip and merge-skip-on-existing-id (F1). No prompt/DTO contract changes; no Room migration.
