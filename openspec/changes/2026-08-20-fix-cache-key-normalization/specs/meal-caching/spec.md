## MODIFIED Requirements

### Requirement: Whole-meal cache matches only on exact normalized query
The system SHALL reuse a cached meal for an add-meal request only when the request's normalized query exactly matches a cached meal-key entry. Normalization SHALL lowercase, strip amount/quantity tokens, and reduce the query to an order-insensitive, de-duplicated token set; a match SHALL require the two token sets to be equal (not a subset or prefix match). The meal-key SHALL be derived from the query text only and SHALL NOT include item names.

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

### Requirement: Item-name cache stores nutrition per 100 g/ml and scales on use
The system SHALL maintain an item cache keyed on the exact normalized item name (amount tokens stripped, order-insensitive token-set equality, matching the whole-meal normalization rule). Each cached item's nutrition SHALL be stored normalized to a 100 g/ml basis. When a cached item is reused, its nutrition SHALL be scaled from the 100 g/ml basis to the requested amount for that item.

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
