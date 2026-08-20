## 1. The normalization rule (meal-caching logic)

- [x] 1.1 In `app/src/main/java/com/example/vocalorie/data/MealMappers.kt`, add `internal fun String.foldedForKey(): String` beside the existing patterns (`:418-420`): lowercase, map `ä`/`ö`/`ü`/`ß` to `ae`/`oe`/`ue`/`ss`, then `Normalizer.normalize(this, Form.NFD)` and drop combining marks (design D3, order matters). Pure Kotlin, no Android types.
- [x] 1.2 Extend the amount patterns beside `amountTokenPattern` (`MealMappers.kt:420`): a `countPrefixedAmountPattern` for `\b\d+(?:[.,]\d+)?\s*[x×]\s*\d+(?:[.,]\d+)?\s*(?:g|ml|kg|l)\b` and a `kcalTokenPattern` for `\b\d+(?:[.,]\d+)?\s*kcal\b`, both `IGNORE_CASE`. Strip the count-prefixed pattern before `amountTokenPattern` so the longer match wins.
- [x] 1.3 Add the closed token sets `internal val COUNTING_WORDS` (ein, eine, einen, einem, eins, zwei, drei, vier, fuenf, sechs, sieben, acht, neun, zehn, elf, zwoelf) and `internal val UNIT_WORDS` (g, ml, kg, l, el, tl, stueck, scheibe, scheiben, prise, portion, glas, becher, tasse), written in already-folded form so they compare against folded tokens (design D2).
- [x] 1.4 Rewrite `normalizeMealText` (`MealMappers.kt:335-338`) to fold via 1.1, then strip the count-prefixed, amount and kcal patterns, then collapse whitespace — leaving the existing `trim()` and `Regex("\\s+")` collapse in place.
- [x] 1.5 In `toStableNormalizedMealKey` (`MealMappers.kt:340-348`), add `.filterNot { it in COUNTING_WORDS }` and `.filterNot { it in UNIT_WORDS }` to the existing `filterNot { it.isNumericToken() }` chain, keeping the `distinct().sorted()` token-set semantics untouched.
- [x] 1.6 Extend `app/src/test/java/com/example/vocalorie/data/MealMappersTest.kt` with a table test over the rule: `"Karotten zwei"` and `"Karotten vier"` both → `karotten`; `"2x106g Sprotten"` and `"1x106g Sprotten"` both → `sprotten`; `"ein Glas Buttermilch"` → `buttermilch`; `"Rührei 169kcal"` → `ruehrei`; `"Müsli"` and `"Muesli"` both → `muesli`; `"zwei Scheiben Brot"` → `brot`; `"2 Stück"` → empty; `"Grillgwmüse"` ≠ `"Grillgemüse"`; and `"Apfel und Banane"` still → `apfel banane und` in sorted order.
- [x] 1.7 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 2. Match, write and search behaviour (meal-caching behaviour)

- [x] 2.1 Confirm no caller needs an edit: whole-meal match (`MealMappers.kt:120`), cache write (`:169`), item-cache write (`:192`), item-cache read (`:221`) and meal-history search (`:246, 351`) all reach the rule through `toStableNormalizedMealKey`. Read each and record that no change was needed.
- [x] 2.2 Confirm the empty-key guards hold: `findCachedMealMatch` returns null on a blank key (`MealMappers.kt:121`) and `toCachedItemEntity` returns null on a blank normalized name (`:193`). Add the meal-key *write* guard if the write path (`:169`) lacks one, so a query normalizing to nothing is never cached (design D6).
- [x] 2.3 Extend `app/src/test/java/com/example/vocalorie/data/MealCacheTest.kt`: "Karotten zwei" hits a row cached from "Karotten vier"; "ein Glas Buttermilch" hits a "Buttermilch" row; "2 Stück" matches nothing and writes nothing; "Grillgwmüse" still misses "Grillgemüse"; an item named "Scheibe" produces no item-cache row; and an item named "Muesli" resolves from a "Müsli" row.
- [x] 2.4 Add a search-path test for the widened blast radius (design "Risks"): searching the meal history for "Glas" or "zwei" no longer matches meals whose query contained only those size words, while searching for the food name still matches. Place it beside the existing search cases in `MealMappersTest.kt`.
- [x] 2.5 Confirm nutrition math is untouched: the per-100 basis (`MealMappers.kt:191-209`) and the reuse scaling (`:370-381`) read amounts only; no counting word becomes a portion factor (design D7).
- [x] 2.6 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 3. Migration and backup version (data layer)

- [x] 3.1 Add `MIGRATION_10_11` to `app/src/main/java/com/example/vocalorie/data/VocalorieDatabase.java`, following the shape of `MIGRATION_9_10` (`:159-167`): `DELETE FROM cached_meals` and `DELETE FROM cached_items`, with a comment stating that the key rule changed so every stored key is unreachable (design D4). Touch no other table.
- [x] 3.2 Raise `@Database(version = 10)` to `11` (`VocalorieDatabase.java:13`) and add `MIGRATION_10_11` to the `addMigrations(...)` chain (`:183`). Do not introduce `fallbackToDestructiveMigration`.
- [x] 3.3 In `app/src/main/java/com/example/vocalorie/data/VocalorieBackup.kt`, set `BACKUP_SCHEMA_VERSION` to `11` (`:14`) and widen `SUPPORTED_BACKUP_SCHEMA_VERSIONS` to `8..11` (`:22`) in this same change, updating the doc comment to say that a v8–v10 envelope's cache rows arrive under the old key rule and are simply unreachable (design D5).
- [x] 3.4 Extend `app/src/test/java/com/example/vocalorie/data/VocalorieBackupTest.kt` so a v10 envelope still imports and a v11 envelope round-trips.
- [x] 3.5 Add the 10→11 case to `app/src/androidTest/java/com/example/vocalorie/data/VocalorieDatabaseMigrationTest.kt` beside the existing cases: a v10 database holding cache rows and history rows migrates to v11 with both cache tables empty and the history intact.
- [x] 3.6 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon` and, because this group changes the schema, `./gradlew :app:compileDebugAndroidTestKotlin :app:connectedDebugAndroidTest --no-daemon`

## 4. Specs and backlog (documentation)

- [x] 4.1 Apply this change's `meal-caching` delta to `openspec/specs/meal-caching/spec.md`, replacing the "Whole-meal cache matches only on exact normalized query" requirement (`:18`), the "Item-name cache stores nutrition per 100 g/ml and scales on use" requirement (`:37`) and the "Cache starts empty on migration" requirement (`:78`) with the modified text, scenarios included. If the `2026-08-20-fix-item-quantity-scaling` change has already been applied to the main spec, keep its quantity-label sentences and merge the normalization sentences alongside them rather than overwriting.
- [x] 4.2 Confirm the backlog bookkeeping is already done — the `b5-*` file is deleted and `docs/agent/backlog/bugs/README.md` lists B5 under `## Promoted`, pointing at this change. Its investigation, including the refuted date-leak suspicion, lives in this change's `proposal.md`. No edit expected.
- [x] 4.3 Verify: `openspec validate 2026-08-20-fix-cache-key-normalization --strict` passes, and the main specs still parse (`openspec list --specs`).

## 5. On-device confirmation

- [x] 5.1 Install: `./gradlew :app:installDebug --no-daemon` onto an install already holding the 171 cached meals, so the migration runs against real data rather than a fresh database.
- [ ] 5.2 Confirm the app starts and the meal history is intact after the migration, and that the first reviewed save repopulates a cache row.
- [ ] 5.3 Log "Karotten zwei", save it reviewed, then log "Karotten vier" and confirm the second request is served from the cache with no LLM call; repeat with "Buttermilch" followed by "ein Glas Buttermilch".
- [ ] 5.4 Confirm a genuinely different food still misses: log "Brot" after the Buttermilch rows and confirm a fresh estimate is requested.
- [ ] 5.5 Export a backup and re-import it, confirming the v11 envelope round-trips and an older exported file still imports.
- [x] 5.6 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`
