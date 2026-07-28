# Design

## B1 — Refreshable "now"

Today the crossed-out treatment is a pure render-time derivation: `Instant.ofEpochMilli(meal.createdAtEpochMillis).isAfter(now)`, where `now = remember(meals) { Instant.now() }` (`MealEntriesScreen.kt:111`). Because the key is `meals`, `now` is frozen until the list reference changes, i.e. until a write triggers `refreshHistory()`.

Decision: keep the derivation, but make `now` a value that a refresh advances.

- Hold `now` in state (e.g. `mutableStateOf(Instant.now())`), not `remember(meals)`.
- On pull-to-refresh, and on any history reload, set `now = Instant.now()` in the same action that reloads entries. Reload + reclassify happen together, so a passed-time entry both reappears in the correct day window and drops its planned styling.
- Gesture: a swipe-down pull-to-refresh over the entries list (Compose `PullToRefresh`), matching the news-feed idiom the user asked for. Its `onRefresh` calls the existing `refreshHistory()` and bumps `now`.

Explicitly **not** doing (out of scope): a periodic ticker, an on-resume recompute, or a midnight-scheduled tick. Pull-to-refresh is the only new trigger; an app left open across midnight refreshes when the user pulls. This can be revisited later if the manual gesture proves insufficient.

## F1 — Export / import

### File shape
A single JSON envelope with a schema version and one array per table:

```json
{
  "format": "vocalorie-backup",
  "schemaVersion": 8,
  "exportedAtEpochMillis": 0,
  "meals": [ /* MealEntity rows, incl. id */ ],
  "activities": [ /* ActivityEntity rows, incl. id */ ],
  "cachedMeals": [ /* CachedMealEntity rows, keyed by normalizedKey */ ],
  "cachedItems": [ /* CachedItemEntity rows, keyed by normalizedName */ ]
}
```

- `schemaVersion` mirrors the Room DB version (currently 8). Import refuses (or warns) on a version it does not understand rather than corrupting data. Since this change adds no table, exports stay at 8.
- Secrets are never serialized — the envelope only covers the four data tables, not the SharedPreferences key stores (which backup rules already exclude).
- Reuse the JSON serialization already used for `itemsJson`; do not add a new dependency.

### Merge semantics (decided: match on stable id, skip if present)
For each table, read the existing primary keys once, then insert only incoming rows whose key is not already present:

- `meals`, `activities`: primary key is the `Long id`. Build the existing-id set from `getAll()`; insert rows with a new id via the existing single-row `insert`.
- `cachedMeals`, `cachedItems`: primary key is `normalizedKey` / `normalizedName`. Same skip-if-present rule (the cache DAO's REPLACE upsert would otherwise overwrite; we deliberately do not overwrite).

Existing rows are never modified or deleted. Re-importing the same file is a no-op.

### Autoincrement-id caveat (must be surfaced in-app)
`MealEntity` and `ActivityEntity` use `@PrimaryKey(autoGenerate = true)`, so two independent installs both start numbering at 1. Consequences:

- **Primary use case — restore into a fresh/empty install: correct.** Inserting rows with explicit non-zero ids preserves those ids (Room only autogenerates when id == 0). The existing-id set is empty, so everything imports and keeps its original id; a later re-import is a clean no-op.
- **Merging a file from a *different* install into a non-empty DB: lossy.** An incoming id that coincidentally equals an existing (but unrelated) row is skipped, silently dropping that incoming entry.

Decision: keep the id-skip rule as chosen, and make the risk visible — the import result reports counts (imported vs skipped), and the feature is documented as "best for restoring into a fresh install." A safer content-aware variant (insert-as-new-id when the id collides but content differs) is noted as a possible follow-up, deliberately out of scope here.

### File access
- Export: SAF `ActivityResultContracts.CreateDocument("application/json")`, suggested name like `vocalorie-backup-<date>.json`. No new SAF usage exists today (only `PickVisualMedia`), so both launchers are new.
- Import: SAF `ActivityResultContracts.OpenDocument` filtered to JSON.
- All serialization and DB I/O run off the main thread, consistent with existing DAO access.

### UI
A new Settings section ("Backup" / "Data") with an Export button and an Import button, plumbed through the existing `SettingsScreen` → `SettingsContent` callback pattern. Import shows a brief confirmation and a result summary (imported/skipped counts); a version-mismatch or unreadable file surfaces a clear error rather than partial application.

## F2 — Selected-day targeting

`selectedDayOffset` is local to `MealEntriesScreen` (`:108`), while the save lambdas (`onSave`, `openActivityEditor`) live in the parent `MealCaptureScreen`. Hoist the offset up to `MealCaptureScreen` (pass it down to `MealEntriesScreen` as a hoisted state param) so the save path can read it.

Target timestamp for a **new** entry, reusing the existing offset→date arithmetic (`MealTimeWindows.kt`) plus a time-of-day component:

```
LocalDate.ofInstant(now, zone)
  .minusDays(offset.toLong())
  .atTime(LocalTime.now(zone))
  .atZone(zone).toInstant().toEpochMilli()
```

- Offset 0 (today) resolves to ~now, preserving current behavior.
- Applies only to newly created meals/activities. Editing an existing entry keeps its stored timestamp (the edit path already uses `?: existing`).
- Adding to a future day yields a future-dated (crossed-out) entry — consistent with planned meals, and now clearable via B1's refresh once the time passes.
