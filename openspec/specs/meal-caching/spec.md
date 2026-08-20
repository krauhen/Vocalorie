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

#### Scenario: A scaled reuse moves the quantity labels with the amounts
- **WHEN** the user adds "Buttermilch 100g" and the matching cached meal holds one item reading `"200 g"` at `200`
- **THEN** the prepared draft's item reads `"100 g"` at `100`

#### Scenario: A scaled reuse derives a label it cannot parse
- **WHEN** the user adds "Nüsse 50g" and the matching cached meal holds one item with `quantity` `"eine Handvoll"` at `100`
- **THEN** the prepared draft's item reads `"50 g"` at `50`

### Requirement: Item-name cache stores nutrition per 100 g/ml and scales on use
The system SHALL maintain an item cache keyed on the exact normalized item name (amount tokens stripped, order-insensitive token-set equality, matching the whole-meal normalization rule). Each cached item's nutrition SHALL be stored normalized to a 100 g/ml basis. When a cached item is reused, its nutrition SHALL be scaled from the 100 g/ml basis to the requested amount for that item.

The per-100 basis SHALL be derived from `amountGml` only; an item's `quantity` label SHALL NOT contribute to it, and the cache SHALL NOT rely on that label having any particular form. When a reused item is scaled to the requested amount, its `quantity` label SHALL be scaled by the rule defined in the `meal-item-quantity` capability.

#### Scenario: Item nutrition is stored per 100 g/ml
- **WHEN** an item "Buttermilch 200g" with 100 kcal is cached
- **THEN** it is stored as 50 kcal per 100 g

#### Scenario: Reused item scales to the requested amount
- **WHEN** the cached "Buttermilch" item (50 kcal / 100 g) is reused for a request of 150 g
- **THEN** the resolved item nutrition is 75 kcal

#### Scenario: Item match ignores amount
- **WHEN** the item cache holds "Buttermilch" and a new estimate contains "Buttermilch" at 150 g
- **THEN** the cached item matches (name equal, amount ignored) and is scaled to 150 g

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
The system SHALL create the cache storage via an additive Room migration from schema v4 to v5 (no destructive migration), and the cache SHALL start empty — it SHALL NOT be backfilled from existing meal history. Existing meals SHALL remain intact through the migration.

#### Scenario: Existing meals survive the migration
- **WHEN** the app upgrades an existing database from v4 to v5
- **THEN** the cache table(s) are created empty and all existing meals remain intact

#### Scenario: Cache fills from new saves only
- **WHEN** the user saves a reviewed meal after upgrading to v5
- **THEN** that save is the first data to populate the cache

### Requirement: A reused cached meal preserves its food-type category
The system SHALL persist a cached meal's food-type category alongside its cached nutrition data, and SHALL restore that category when the cached meal is reused. A cache hit SHALL NOT downgrade a meal's category to the neutral fallback. Cache entries written before the category was persisted SHALL resolve to the neutral fallback category, exactly as an unclassified meal does.

#### Scenario: A cache hit keeps the original category
- **WHEN** a meal classified as a drink is saved, cached, and later reused from the whole-meal cache
- **THEN** the reused meal is still classified as a drink and renders the drink icon

#### Scenario: A pre-existing cache entry falls back neutrally
- **WHEN** a cached meal written before category persistence is reused
- **THEN** it resolves to the neutral fallback category rather than failing or being rejected

#### Scenario: Category survives the round trip through the cache
- **WHEN** a reviewed meal of any category is saved and its cache entry is read back
- **THEN** the category read back equals the category that was saved

### Requirement: Cache lookup cost does not grow with cache size
The system SHALL resolve a whole-meal cache lookup and an item-name cache lookup by querying for the specific keys being looked up. The system SHALL NOT load the full contents of either cache table in order to find a matching entry, and SHALL NOT retain the full cache tables in user-interface state.

#### Scenario: A whole-meal lookup queries only its key
- **WHEN** an add-meal request is checked against the whole-meal cache
- **THEN** the lookup retrieves at most the entry matching that request's normalized key, rather than every cached meal

#### Scenario: Item resolution queries only the names it needs
- **WHEN** an estimate's items are resolved against the item-name cache
- **THEN** the lookup retrieves only entries matching those item names, rather than every cached item

#### Scenario: Lookup behaviour is unchanged as the cache grows
- **WHEN** the cache contains a large number of entries
- **THEN** matching still follows the existing exact normalized-key rules and returns the same results as before, at a cost that does not scale with the number of stored entries

### Requirement: Cache writes for one saved meal are atomic
The system SHALL write a reviewed meal's whole-meal cache entry and its item-name cache entries as one atomic unit, so that the two caches cannot be left inconsistent with each other by an interrupted save.

#### Scenario: An interrupted cache write leaves neither cache updated
- **WHEN** a reviewed save is interrupted between writing the whole-meal cache entry and the item-name cache entries
- **THEN** neither cache retains a partial update from that save

