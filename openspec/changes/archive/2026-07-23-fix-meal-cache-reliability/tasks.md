## 1. Cache storage separate from history (B4)

- [x] 1.1 Add Room entity + DAO for the meal-key cache (normalized key + serialized meal payload / prepared draft data)
- [x] 1.2 Add Room entity + DAO for the item-name cache (normalized item name + nutrition per 100 g/ml)
- [x] 1.3 Add `MIGRATION_4_5` in `VocalorieDatabase.java` that additively creates the cache table(s); bump version to 5; no `fallbackToDestructiveMigration`
- [x] 1.4 Confirm the migration leaves all existing `meals` rows intact and the cache starts empty (no backfill)

## 2. Exact whole-meal matching from cache (B3)

- [x] 2.1 Keep `toStableNormalizedMealKey()` normalization (lowercase, strip amounts, order-insensitive, de-duplicated token set); ensure the key excludes title and item names
- [x] 2.2 Change `matchesNormalizedMealKey()` to exact token-set equality (no subset/prefix)
- [x] 2.3 Repoint `findCachedMealMatch()` to read the meal-key cache instead of scanning the `meals` history list
- [x] 2.4 Ensure a reused meal is scaled to the requested amount as before

## 3. Per-item cache normalized to 100 g/ml (F1)

- [x] 3.1 On reviewed-save, normalize each item's nutrition to a 100 g/ml basis and upsert it into the item-name cache
- [x] 3.2 Match items by normalized name (amount ignored) and scale per-100 nutrition to the requested amount on reuse
- [x] 3.3 After an estimate resolves items, auto-resolve items with a cached-name match, honoring each item's requested amount

## 4. Write policy & lifecycle

- [x] 4.1 Upsert both caches only when a reviewed meal is saved (in `MealCaptureScreen.kt` save path); last-saved-wins per key (one row per key)
- [x] 4.2 Ensure unreviewed/discarded estimates never write to either cache
- [x] 4.3 No eviction / size cap; confirm no history row is ever modified, merged, or deleted

## 5. Tests

- [x] 5.1 Exact-match tests: identical query hits; word-order-equal hits; subset query misses; item-name query does not match a meal
- [x] 5.2 Separation tests: logging the same food 5× leaves 5 history rows and 1 cache entry; cache upsert never mutates history
- [x] 5.3 Write-policy tests: reviewed-save upserts both caches (one row per key, overwrite on re-save); discarded estimate writes nothing
- [x] 5.4 Per-item tests: item stored per 100 g/ml; reuse scales to requested amount; amount-agnostic name match; unknown item unaffected
- [x] 5.5 Migration test: v4→v5 creates empty cache table(s), existing meals intact

## 6. Verification

- [x] 6.1 Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon` and confirm all tests pass
- [ ] 6.2 Manually verify on emulator/device: repeated "Buttermilch"/"Froobie" keep full history but no longer cross-match; a recurring item reuses cached nutrition at the correct amount
