## ADDED Requirements

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
