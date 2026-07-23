## Why

The meal "cache" today is not a cache at all — it is the `meals` history table itself. `findCachedMealMatch()` scans all saved meals and matches on a key that mixes the meal title, the raw query, and every item name, using subset/prefix matching. As a result unrelated meals and items cross-match (B3), and the entries list fills with many near-identical rows for the same food (e.g. 5+ "Buttermilch") because every logged instance is a history row that also acts as a match source (B4). There is also no item-level reuse: an item that recurs across different meals still costs a full LLM call (F1).

This change introduces a **dedicated cache subsystem, separate from the meal history**. History keeps every logged instance untouched; the cache holds exactly one authoritative entry per normalized key for reuse. Both concerns live in one subsystem (`MealMappers.kt` + new Room storage), so they ship together.

## What Changes

- **New separate cache (B4):** Add cache storage distinct from `meals`, so de-duplication applies to the cache, not to the user's logging history. The entries list and every historical row are unchanged; only prefill/reuse reads the cache.
- **Two caches (B3 + F1):**
  - A **meal-key cache** keyed on the normalized meal query, matched by **exact token-set equality** (order-insensitive, amount tokens stripped; item names excluded from the key). Fixes cross-matches.
  - An **item-name cache** keyed on the normalized item name, storing nutrition **normalized to 100 g/ml**, scaled to the requested amount on reuse. After an estimate, recurring items auto-resolve from this cache.
- **Write policy:** Both caches are **upserted only when a reviewed meal is saved** (last-saved-wins per key). Unreviewed estimates never write to the cache.
- **Lifecycle:** One row per key, **unbounded, no eviction**. On the v4→v5 migration the cache **starts empty** and fills from new reviewed-saves (no backfill from existing history).

Out of scope: modifying, merging, or deleting existing history rows; backfilling the cache from history; eviction/size caps; a Settings "clear cache" action; any LLM prompt or estimate-DTO wording change.

## Approvals obtained

- Additive Room migration **v4→v5** adding the cache table(s), no `fallbackToDestructiveMigration` (consistent with existing convention) — approved.

## Capabilities

### New Capabilities
- `meal-caching`: a dedicated cache subsystem, separate from meal history, providing exact-key whole-meal reuse and per-item reuse (nutrition stored per 100 g/ml and scaled to the requested amount), populated only on reviewed-save.

## Impact

- `app/src/main/java/com/example/vocalorie/data/` — new Room entity/entities + DAO for the meal-key cache and item-name cache; `VocalorieDatabase.java` gains `MIGRATION_4_5` (additive) and bumps to version 5.
- `app/src/main/java/com/example/vocalorie/data/MealMappers.kt` — `toStableNormalizedMealKey` (keep normalization: lowercase, strip amounts, order-insensitive, de-duplicated token set), `matchesNormalizedMealKey` (exact equality), remove item-name folding from the meal key; add per-item normalization to 100 g/ml and scaling; `findCachedMealMatch` reads the cache instead of the history list.
- `app/src/main/java/com/example/vocalorie/ui/MealCaptureScreen.kt` — save path upserts both caches (last-saved-wins); estimate resolution auto-picks cached items by name and corrects the amount.
- `app/src/main/java/com/example/vocalorie/data/CachedMealMatch.kt` — adjust for cache-sourced matches and item results.
- Tests: `app/src/test/java/com/example/vocalorie/data/` — exact matching, no cross-match, upsert-on-save (one row per key), per-item normalization/scaling, and history-untouched.
- No LLM-prompt, build, or dependency changes in this change.
