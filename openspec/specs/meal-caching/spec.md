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

### Requirement: Item-name cache stores nutrition per 100 g/ml and scales on use
The system SHALL maintain an item cache keyed on the exact normalized item name (amount tokens stripped, order-insensitive token-set equality, matching the whole-meal normalization rule). Each cached item's nutrition SHALL be stored normalized to a 100 g/ml basis. When a cached item is reused, its nutrition SHALL be scaled from the 100 g/ml basis to the requested amount for that item.

#### Scenario: Item nutrition is stored per 100 g/ml
- **WHEN** an item "Buttermilch 200g" with 100 kcal is cached
- **THEN** it is stored as 50 kcal per 100 g

#### Scenario: Reused item scales to the requested amount
- **WHEN** the cached "Buttermilch" item (50 kcal / 100 g) is reused for a request of 150 g
- **THEN** the resolved item nutrition is 75 kcal

#### Scenario: Item match ignores amount
- **WHEN** the item cache holds "Buttermilch" and a new estimate contains "Buttermilch" at 150 g
- **THEN** the cached item matches (name equal, amount ignored) and is scaled to 150 g

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
The system SHALL create the cache storage via an additive Room migration from schema v4 to v5 (no destructive migration), and the cache SHALL start empty — it SHALL NOT be backfilled from existing meal history. Existing meals SHALL remain intact through the migration.

#### Scenario: Existing meals survive the migration
- **WHEN** the app upgrades an existing database from v4 to v5
- **THEN** the cache table(s) are created empty and all existing meals remain intact

#### Scenario: Cache fills from new saves only
- **WHEN** the user saves a reviewed meal after upgrading to v5
- **THEN** that save is the first data to populate the cache
