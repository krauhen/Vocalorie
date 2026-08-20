# meal-caching

## Purpose

Speed up and stabilize repeat meal logging by caching reviewed nutrition data separately from meal history. Two caches are maintained: a whole-meal cache keyed on the normalized query, and an item-name cache storing nutrition per 100 g/ml. Caches are written only on reviewed-save, never modify history, and start empty on migration.
## Requirements
### Requirement: Cache is separate from meal history
The system SHALL store cached nutrition data in dedicated cache storage that is separate from the `meals` history table. Adding or reusing cache entries SHALL NOT modify, merge, delete, or de-duplicate any meal history row. Every logged meal instance SHALL remain in history exactly as saved, while the cache holds at most one entry per normalized key.

#### Scenario: Logging the same food repeatedly keeps full history
- **WHEN** the user logs "Buttermilch" on five different occasions
- **THEN** all five instances remain as separate history rows in the entries list, and the cache holds a single "Buttermilch" entry

#### Scenario: Cache changes never rewrite history
- **WHEN** a reviewed meal is saved and upserts the cache
- **THEN** no existing history row is altered or removed as a result

### Requirement: Whole-meal cache matches only on exact normalized query
The system SHALL reuse a cached meal for an add-meal request only when the request's normalized query exactly matches a cached meal-key entry. Normalization SHALL lowercase, strip amount/quantity tokens, and reduce the query to an order-insensitive, de-duplicated token set; a match SHALL require the two token sets to be equal (not a subset or prefix match). The meal-key SHALL be derived from the query text only and SHALL NOT include item names.

When a reused cached meal is scaled to the amount requested by the query, each of its items' `quantity` labels SHALL be scaled by the same rule as the item amounts, as defined by the `meal-item-quantity` capability, so that a reused meal never presents a label disagreeing with the amount beside it.

Normalization SHALL remove every token that states how much of the food there is, because size is carried by the item amounts rather than by the key. It SHALL remove: bare numbers; `<number><unit>` amounts in g, ml, kg and l; `<count>x<number><unit>` amounts; `<number>kcal` hints; the German counting words for one through twelve including their common inflections; and the German portion-unit words the estimate prompt teaches the model to expect — EL, TL, Stück, Scheibe, Prise, Portion — together with Glas, Becher and Tasse.

Normalization SHALL fold diacritics so a transcriber's umlaut choice does not change the identity of a food: `ä`, `ö`, `ü` and `ß` SHALL fold to `ae`, `oe`, `ue` and `ss`, and any remaining combining marks SHALL be dropped.

A query whose normalized key is empty SHALL NOT match any cached meal and SHALL NOT be written to the cache.

#### Scenario: Identical query reuses the cached meal
- **WHEN** the user adds "Buttermilch 200g" and a cached meal-key entry `{buttermilch}` exists
- **THEN** the cached meal is reused (no LLM call), scaled to the requested amount

#### Scenario: Word order does not affect the match
- **WHEN** the user adds "Apfel und Banane" and a cached meal-key entry `{apfel, banane, und}` exists
- **THEN** the cached meal is reused, because the token sets are equal regardless of order

#### Scenario: Subset query does not match
- **WHEN** the user adds "Buttermilch" and the only cached meal-key entry is `{buttermilch, mit, honig}`
- **THEN** no cache match is returned and a fresh estimate is requested

#### Scenario: Item names do not trigger a meal match
- **WHEN** a cached meal "Frühstück" contains an item named "Froobie" and the user adds a meal query "Froobie"
- **THEN** the cached "Frühstück" meal is NOT matched on the basis of its item name

#### Scenario: A counting word does not fork the key
- **WHEN** the user adds "Karotten zwei" and a cached entry exists for "Karotten vier"
- **THEN** both queries normalize to `{karotten}` and the cached meal is reused

#### Scenario: A count-prefixed amount does not fork the key
- **WHEN** the user adds "2x106g Sprotten" and a cached entry exists for "1x106g Sprotten"
- **THEN** both normalize to `{sprotten}` and the cached meal is reused

#### Scenario: A portion-unit word does not fork the key
- **WHEN** the user adds "ein Glas Buttermilch" and a cached entry exists for "Buttermilch"
- **THEN** both normalize to `{buttermilch}` and the cached meal is reused

#### Scenario: A spoken calorie hint does not fork the key
- **WHEN** the user adds "Rührei 169kcal" and a cached entry exists for "Rührei"
- **THEN** both normalize to `{ruehrei}` and the cached meal is reused

#### Scenario: Umlaut spelling variants share one key
- **WHEN** the user adds "Muesli" and a cached entry exists for "Müsli"
- **THEN** both normalize to `{muesli}` and the cached meal is reused

#### Scenario: A different food still misses
- **WHEN** the user adds "zwei Scheiben Brot" and the only cached entry is for "ein Glas Buttermilch"
- **THEN** the normalized keys are `{brot}` and `{buttermilch}`, no match is returned, and a fresh estimate is requested

#### Scenario: A transcription typo is still a separate key
- **WHEN** the user adds "Grillgwmüse" and a cached entry exists for "Grillgemüse"
- **THEN** no match is returned, because normalization corrects size words and spelling variants but not typos

#### Scenario: A query of nothing but size words matches nothing
- **WHEN** the user adds "2 Stück"
- **THEN** the normalized key is empty, no cached meal is matched, and the resulting meal is not written to the meal-key cache

#### Scenario: A scaled reuse moves the quantity labels with the amounts
- **WHEN** the user adds "Buttermilch 100g" and the matching cached meal holds one item reading `"200 g"` at `200`
- **THEN** the prepared draft's item reads `"100 g"` at `100`

#### Scenario: A scaled reuse derives a label it cannot parse
- **WHEN** the user adds "Nüsse 50g" and the matching cached meal holds one item with `quantity` `"eine Handvoll"` at `100`
- **THEN** the prepared draft's item reads `"50 g"` at `50`

### Requirement: Item-name cache stores nutrition per 100 g/ml and scales on use
The system SHALL maintain an item cache keyed on the exact normalized item name (amount tokens stripped, order-insensitive token-set equality, matching the whole-meal normalization rule). Each cached item's nutrition SHALL be stored normalized to a 100 g/ml basis. When a cached item is reused, its nutrition SHALL be scaled from the 100 g/ml basis to the requested amount for that item.

The per-100 basis SHALL be derived from `amountGml` only; an item's `quantity` label SHALL NOT contribute to it, and the cache SHALL NOT rely on that label having any particular form. When a reused item is scaled to the requested amount, its `quantity` label SHALL be scaled by the rule defined in the `meal-item-quantity` capability.

The item key SHALL be produced by the same normalization as the meal key, including the counting-word, unit-word and diacritic rules, so the two caches can never disagree about what the same words mean. An item whose normalized name is empty SHALL NOT be written to the item cache.

#### Scenario: Item nutrition is stored per 100 g/ml
- **WHEN** an item "Buttermilch 200g" with 100 kcal is cached
- **THEN** it is stored as 50 kcal per 100 g

#### Scenario: Reused item scales to the requested amount
- **WHEN** the cached "Buttermilch" item (50 kcal / 100 g) is reused for a request of 150 g
- **THEN** the resolved item nutrition is 75 kcal

#### Scenario: Item match ignores amount
- **WHEN** the item cache holds "Buttermilch" and a new estimate contains "Buttermilch" at 150 g
- **THEN** the cached item matches (name equal, amount ignored) and is scaled to 150 g

#### Scenario: Item names fold the same way as meal keys
- **WHEN** the item cache holds "Müsli" and a new estimate contains an item named "Muesli"
- **THEN** the cached item matches, both names normalizing to `muesli`

#### Scenario: An item named only by a unit word is not cached
- **WHEN** a reviewed meal contains an item named "Scheibe"
- **THEN** its normalized name is empty and no item-cache row is written for it

#### Scenario: An unparseable quantity label does not disturb the per-100 basis
- **WHEN** an item with `quantity` `"eine Handvoll"` and `amountGml` `50` and 100 kcal is cached
- **THEN** it is stored as 200 kcal per 100 g, the label having played no part in the calculation

### Requirement: Recurring estimate items resolve from the item cache
The system SHALL, after an estimate produces items, auto-resolve any item whose normalized name matches an item-cache entry, reusing the cached per-100 nutrition (scaled to that item's amount) rather than requiring a fresh nutrition value for it. The per-request amount for each item SHALL still be honored.

#### Scenario: Repeat item is auto-picked from cache
- **WHEN** an estimate returns an item "Buttermilch 150g" and the item cache contains "Buttermilch"
- **THEN** the item is auto-resolved from cache, scaled to 150 g, without needing a re-requested nutrition value

#### Scenario: Unknown item is not resolved from cache
- **WHEN** an estimate returns an item with no matching cached item name
- **THEN** that item keeps its freshly estimated nutrition and is not altered by the cache

### Requirement: Caches are written only on reviewed-save, one row per key
The system SHALL upsert both the meal-key cache and the item-name cache only when the user saves a reviewed meal, with the most recently saved values winning on key conflict (one row per normalized key). Unreviewed estimate results SHALL NOT be written to either cache. The caches SHALL hold one entry per key with no size limit or eviction.

#### Scenario: Reviewed save populates both caches
- **WHEN** the user reviews and saves a meal "Buttermilch 200g"
- **THEN** the meal-key cache gains/updates `{buttermilch}` and the item-name cache gains/updates "Buttermilch" (per 100 g/ml)

#### Scenario: Re-saving the same key overwrites, not appends
- **WHEN** the user later reviews and saves another "Buttermilch" with corrected nutrition
- **THEN** the existing "Buttermilch" cache entries are updated in place (still one row per key), not duplicated

#### Scenario: Unsaved estimate does not populate the cache
- **WHEN** an estimate is produced but the user discards it without saving
- **THEN** neither cache is written

### Requirement: Cache starts empty on migration
The system SHALL create both cache tables empty and SHALL clear them again whenever the normalization rule that produces their keys changes, because a stored key that the current rule would not produce is unreachable. Neither table SHALL be populated by back-filling from the existing meal history; both SHALL refill from subsequent reviewed saves.

#### Scenario: Existing install migrates to an empty cache
- **WHEN** an install with saved meals is migrated to a schema version that introduces the caches
- **THEN** both cache tables exist and are empty, and no history row is back-filled into them

#### Scenario: A normalization change clears the stored rows
- **WHEN** an install holding 171 cached meals and 280 cached items is migrated to the schema version that changes the key rule
- **THEN** both tables are empty afterwards, and the next reviewed save writes a row under the new rule

#### Scenario: Saved meals survive the clearing
- **WHEN** the cache-clearing migration runs
- **THEN** the meals and activities history tables are untouched
