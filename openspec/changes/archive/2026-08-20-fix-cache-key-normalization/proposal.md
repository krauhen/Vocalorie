## Why

The meal cache accumulates near-duplicate rows for the same food, so a meal the user has already reviewed and saved is re-estimated by the LLM anyway — the cache stops paying for itself, and the reuse path silently does nothing on exactly the repeat meals it exists for.

**The suspected cause is refuted.** No timestamp participates in any cache key or row. `CachedMealEntity` is keyed on `normalizedKey` alone, explicitly "no id/createdAt — the key is the identity" (`data/CachedMealEntity.kt:10, 14-15`); `CachedItemEntity` is keyed on `normalizedName` (`data/CachedItemEntity.kt:15`); the cache migration adds no time column (`data/VocalorieDatabase.java:120-146`); and writes are `OnConflictStrategy.REPLACE` on the primary key (`data/CacheDao.java:40-44`). One key can only ever hold one row.

**Measured on the live device database:** `cached_items` holds 280 rows with **0 duplicate display names** — the item cache is clean. `cached_meals` holds 171 rows with **11 duplicated titles**. The duplication is in cached meals, and it is key divergence, not key collision.

The divergence is in `normalizeMealText` / `toStableNormalizedMealKey` (`data/MealMappers.kt:335-348`). The key is a sorted, de-duplicated bag of query words, and the only amount stripping is `\b\d+([.,]\d+)?\s*(g|ml|kg|l)\b` (`data/MealMappers.kt:420`). Everything else survives into the identity of the meal:

- **Counting words** — `karotten zwei` vs `karotten vier`; `aprikose eine` vs `aprikosen vier`; `pflaumen rote zwei` vs `drei pflaumen rote`.
- **Count-prefixed amounts the regex misses** — `1x106g … sprotten`, `2x106g … sprotten`, `3x106g … sprotten`: three rows, one food.
- **German unit words the prompt itself teaches the model to expect** — EL, TL, Stück, Scheibe, Prise, Portion (`ai/KoogNutritionAgent.kt:293`), plus Glas — so "Buttermilch" and "ein Glas Buttermilch" are different meals.
- **Spoken kcal hints** landing in the key verbatim (`102kcal 169kcal a einem esslöffel …`).
- **No diacritic folding**, so `Müsli` and `Muesli` are distinct keys.
- **Voice-transcription typos** — `grillgemüse`/`grillgwmüse`, `brie`/`briw` — which no rule can normalize away (see Non-goals).

How much of a meal there is already lives in `amountGml`, which is what the reuse path scales (`data/MealMappers.kt:370-381`). Counts and unit words are size, not identity, and this change stops treating them as identity.

## What Changes

- **Counting words are stripped from the key.** The German number words one through twelve in their common inflections (`ein`, `eine`, `einen`, `einem`, `zwei`, `drei` … `zwölf`) SHALL NOT contribute to a cache key, so `karotten zwei` and `karotten vier` are one cached meal.
- **Count-prefixed amounts are stripped.** A `<count>x<amount><unit>` token such as `2x106g` SHALL be stripped whole, closing the case the existing amount pattern misses.
- **German unit words are stripped.** The units the system prompt names — EL, TL, Stück, Scheibe, Prise, Portion — plus Glas, Becher and Tasse SHALL NOT contribute to the key, so "ein Glas Buttermilch" matches "Buttermilch".
- **Bare `kcal` hints are stripped.** A number followed by `kcal` SHALL be removed like any other amount token, so a spoken calorie guess does not fork the key.
- **Diacritics are folded.** `ä`/`ö`/`ü`/`ß` SHALL fold to `ae`/`oe`/`ue`/`ss` and remaining combining marks SHALL be dropped, so `Müsli` and `Muesli` are the same key.
- **A query that normalizes to nothing SHALL NOT be cached or matched.** Stripping is now aggressive enough that `2 Stück` could reduce to an empty key; the existing blank-key guards (`data/MealMappers.kt:121, 193`) become load-bearing and are specified as such.
- **Both cache tables are cleared on migration.** The new rules change the primary-key value of existing rows, so every current row is unreachable. Migration 10→11 empties `cached_meals` and `cached_items`; both refill on the next reviewed saves.
- **Matching, the per-100 basis and the nutrition math are unchanged.** Only what counts as the same query changes.

## Capabilities

### Modified Capabilities

- `meal-caching`: amend "Whole-meal cache matches only on exact normalized query" to enumerate the token classes normalization strips and the diacritic folding, and to require that an empty normalized key never matches; amend "Item-name cache stores nutrition per 100 g/ml and scales on use" so the item key follows the same rule (it shares the one normalizer); and amend "Cache starts empty on migration" to cover this second clearing.

## Impact

- **One fix point.** `normalizeMealText` (`data/MealMappers.kt:335-338`) and the token filter in `toStableNormalizedMealKey` (`:340-348`) are the only places the key is built. Every caller reaches it through `toStableNormalizedMealKey`: whole-meal match (`:120`), cache write (`:169`), item cache write (`:192`), item cache read (`:221`) and meal-history search (`:246, 351`).
- **A widened blast radius to state plainly.** `toStableNormalizedMealKey` also backs meal-history *search* (`data/MealMappers.kt:246, 351`), so searching "Glas" or "zwei" stops matching on those words. That is consistent — they were never identifying words — but it is a user-visible behaviour change outside the cache, and the tests must cover it.
- **New pure functions.** `internal fun String.strippedOfAmountTokens(): String` and `internal fun String.foldedForKey(): String` in `MealMappers.kt` beside the existing patterns (`:418-420`), plus `internal val COUNTING_WORDS` and `internal val UNIT_WORDS` token sets. No Android types, no Room, so all of it is JVM-testable as `docs/agent/guidance/testing.md` requires.
- **Reuses existing helpers.** The `amountTokenPattern` / `numericTokenPattern` / `isNumericToken` machinery (`MealMappers.kt:418-420`) is extended rather than replaced, and the blank-key guards at `:121` and `:193` already exist and need no new call site.
- **A Room change is required, so `BACKUP_SCHEMA_VERSION` moves.** Migration 10→11 in `data/VocalorieDatabase.java` executes `DELETE FROM cached_meals` and `DELETE FROM cached_items` and the `@Database(version = 10)` (`:13`) becomes 11; `BACKUP_SCHEMA_VERSION` (`data/VocalorieBackup.kt:14`) becomes 11 and `SUPPORTED_BACKUP_SCHEMA_VERSIONS` (`:22`) widens to `8..11` in the same commit, or already-exported backups stop importing. A migration therefore also requires `./gradlew :app:compileDebugAndroidTestKotlin :app:connectedDebugAndroidTest --no-daemon` per `docs/agent/guidance/testing.md`.
- **Imported backups carry old keys.** A v8–v10 envelope restores cache rows keyed under the old rules. They are harmless — unreachable rows that a future save replaces only if the key happens to coincide — and the import path is left alone (see Non-goals).
- **Tests**: extend `app/src/test/java/com/example/vocalorie/data/MealMappersTest.kt` for the normalizer's token table and `MealCacheTest.kt` for the match/write behaviour, including the empty-key guard and the search-path change; add the migration case to the existing instrumented migration test alongside the 9→10 case.
- **Specs**: amend `openspec/specs/meal-caching/spec.md`.
- **Backlog**: B5 closes as promoted to this change.

## Non-goals

- **No fuzzy or edit-distance matching.** `grillgwmüse` and `briw` are transcription errors, and a similarity threshold loose enough to catch them would merge genuinely different foods — `Brie` and `Brot` are two edits apart. Typos stay a separate cache row; the user can fix the query.
- **No plural or stem folding.** `Ei`/`Eier`, `Aprikose`/`Aprikosen`: rule-based German pluralization is wrong often enough that a stemmer would create false matches, which cost the user a wrong estimate rather than a wasted LLM call.
- **No scaling of a hit by a stripped count.** A count word removed from the key is not turned into a portion factor. The reuse path already scales by the explicit g/ml amount in the query (`data/MealMappers.kt:370-381`), and inferring "zwei" as ×2 of a cached row whose own baseline count is unknown would produce a wrong amount silently — the class of defect B1/B2 exists to close, not to widen.
- **No LLM prompt change.** The German unit words are correct instructions to the model (`ai/KoogNutritionAgent.kt:293`); the fix is that the *key* should ignore them, not that the model should stop understanding them.
- **No cache-eviction policy.** The caches still hold one row per key with no size limit, as `meal-caching` specifies.
- **No re-keying of the 171 existing rows.** Re-keying would mean running the Kotlin normalizer inside a SQL migration, which is not possible; the cache is derived data that rebuilds on the next reviewed save, so clearing it is the honest option.
- **No upcasting of cache rows in imported backups.** Same reason: the import path cannot re-normalize, and a stale unreachable row costs nothing.
- **Nothing about B3, B4 or B6.** Tip gating, the midnight rollover and the entries-screen visual defects live in their own changes.
