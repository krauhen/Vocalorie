## MODIFIED Requirements

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
