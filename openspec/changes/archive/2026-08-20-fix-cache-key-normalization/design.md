## Context

B5, captured on the backlog and deleted on promotion (git holds the note), reported duplicate cache entries and guessed at a time value in the key. The investigation refuted that and measured the real shape: `cached_items` 280 rows / 0 duplicate names, `cached_meals` 171 rows / 11 duplicated titles. The cause is normalization divergence in `data/MealMappers.kt:335-348`.

Binding constraints: Room migrations are additive only, never `fallbackToDestructiveMigration`, and a schema bump moves `BACKUP_SCHEMA_VERSION` and widens `SUPPORTED_BACKUP_SCHEMA_VERSIONS` in the same commit (`openspec/config.yaml`, `docs/agent/guidance/coding.md`). Extracted pure functions ship JVM-tested (`docs/agent/guidance/testing.md`).

## Decisions

### D1: Strip size, keep identity

Counting words, unit words, `<count>x<amount><unit>` tokens and `<number>kcal` hints are all statements about *how much*, and how much already lives in `amountGml`, which the reuse path scales (`data/MealMappers.kt:370-381`). The key keeps only what names the food.

*Alternative — keep counts in the key and accept the duplicates as "different portions".* Lost because the cache already scales by amount, so the second row adds nothing but an LKM call the cache was meant to save.

### D2: A closed word list, not a part-of-speech rule

`COUNTING_WORDS` (one to twelve, with the `ein`/`eine`/`einen`/`einem` inflections) and `UNIT_WORDS` (el, tl, stück, scheibe, prise, portion, glas, becher, tasse) are explicit sets, folded through the same diacritic folding as the query so `stück` matches `stueck`.

*Alternative — a morphological analyser or an open numeral parser.* Lost as far too much machinery for a single-user app, and unverifiable: a closed list can be read and unit-tested in one table test.

### D3: Fold umlauts to digraphs, then drop remaining marks

`ä→ae`, `ö→oe`, `ü→ue`, `ß→ss` first, then NFD-decompose and drop combining marks. The order matters: NFD alone would turn `Müsli` into `Musli`, which does not match the `Muesli` the user also says.

*Alternative — NFD strip only.* Lost on that exact pair, which is a real observed case. *Alternative — no folding.* Lost because the transcriber's umlaut choice is not a property of the food.

### D4: Clear both cache tables in migration 10→11

The new rules change the primary-key value of every existing row, so all 171 meal rows and 280 item rows become unreachable whatever we do. Deleting them is explicit; leaving them is silent dead weight that a coincidental key collision could later resurrect with stale nutrition. The item cache is cleared too, even though it has no duplicates, because it shares the one normalizer.

*Alternative — re-key the rows in the migration.* Lost because the normalizer is Kotlin and a Room migration executes SQL; reimplementing the token rules in SQL would create a second, drifting definition of the key. *Alternative — leave the rows and let them rot.* Lost per above: a rebuildable cache is not worth carrying unreachable rows for.

### D5: Imported backups keep their old cache keys

Import restores cache rows verbatim (`data/VocalorieBackup.kt:102`). Rows from a v8–v10 envelope arrive under the old rules and are simply unreachable.

*Alternative — re-normalize cache rows on import.* Lost because the envelope's cache rows are derived data; making import responsible for key migration adds a code path that has to move with every future key change.

### D6: The blank-key guard becomes load-bearing and is specified

With counts and units stripped, a query like `2 Stück` normalizes to nothing. `findCachedMealMatch` (`data/MealMappers.kt:121`) and the item-cache write (`:193`) already return early on a blank key; this change makes that behaviour a specified requirement rather than a defensive accident, so an empty key can never match every meal.

### D7: No count-to-portion inference

A stripped count is discarded, not converted into a scaling factor. The cached row's own baseline count is unknown — `karotten zwei` may have been cached from a two-carrot meal or a four-carrot one — so multiplying by the new count would produce a confidently wrong amount. Scaling stays driven by the explicit g/ml amount in the query.

## Risks

- **Over-merging.** A unit word that is also a food name would collapse two foods. Checked against the list: none of `el, tl, stück, scheibe, prise, portion, glas, becher, tasse` names a food in this app's history. `Glas` is the closest call and was included deliberately, because "ein Glas Buttermilch" is an observed duplicate.
- **Search behaviour changes with the key.** `toStableNormalizedMealKey` also backs meal-history search (`data/MealMappers.kt:246, 351`), so those words stop being searchable. Accepted and tested rather than worked around with a second normalizer, which would drift.
- **A schema bump for a cache clear.** The version move is the price of doing the clear once and deterministically instead of at some arbitrary app start.
