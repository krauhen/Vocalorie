# meal-item-quantity

## Purpose

Define what a meal item's `quantity` label means and how it behaves under scaling: it is human-readable display text, never a calculation input — `amountGml` is the sole numeric basis — and when an item is scaled the label moves with the amount instead of drifting from it.

## Requirements

### Requirement: Item quantity is a display label, never a calculation input
A meal item's `quantity` SHALL be a human-readable label describing that item's portion, held as free text (`"2 eggs"`, `"1 Scheibe"`, `"500 ml"`, `"eine Handvoll"`). It SHALL NOT participate in any nutrition, amount, or total calculation, and SHALL NOT be used as a basis for cached per-100 nutrition. `amountGml` SHALL be the sole numeric basis for every such calculation.

Consequently the label MAY be a rounded or approximate presentation of the portion without affecting any computed value.

#### Scenario: Totals ignore the quantity label
- **WHEN** an item's `quantity` reads `"1 Scheibe"` and its `amountGml` is `40`
- **THEN** the meal's amount and nutrition totals are computed from `40` alone, and editing the label to `"3 Scheiben"` changes no total

#### Scenario: A label with no number is not a missing amount
- **WHEN** an item's `quantity` reads `"eine Handvoll"` and its `amountGml` is `100`
- **THEN** the item contributes `100` to the meal's amount total, and the unparseable label has no effect on any total

### Requirement: Scaling an item scales the leading number in its quantity label
When an item is scaled by a factor, the system SHALL multiply the leading number of its `quantity` by that factor and SHALL preserve the remainder of the string verbatim. The item's `name` SHALL NOT be altered. A comma decimal separator SHALL be accepted in the leading number.

#### Scenario: A counted portion scales
- **WHEN** an item with `quantity` `"2 eggs"` is scaled by a factor of 2
- **THEN** its `quantity` reads `"4 eggs"`

#### Scenario: A measured portion scales and keeps its unit
- **WHEN** an item with `quantity` `"500 ml"` is scaled by a factor of 1.5
- **THEN** its `quantity` reads `"750 ml"`

#### Scenario: A comma decimal is accepted
- **WHEN** an item with `quantity` `"1,5 l"` is scaled by a factor of 2
- **THEN** its `quantity` reads `"3 l"`

#### Scenario: Scaling does not rename the item
- **WHEN** an item named "Toastbrot" with `quantity` `"1 Scheibe"` is scaled by any factor
- **THEN** its name is still "Toastbrot"

### Requirement: A scaled quantity number renders with at most one decimal
The system SHALL render a scaled leading number rounded half-up to at most one decimal place, with a trailing `.0` removed, so that a fractional scaling is visible without exposing the scaling factor's full precision.

#### Scenario: A fractional factor yields one decimal
- **WHEN** an item with `quantity` `"1 Scheibe"` is scaled by a factor of 1.5
- **THEN** its `quantity` reads `"1.5 Scheibe"`

#### Scenario: A whole result carries no decimal point
- **WHEN** an item with `quantity` `"2 eggs"` is scaled by a factor of 2
- **THEN** its `quantity` reads `"4 eggs"` and not `"4.0 eggs"`

#### Scenario: The factor's precision is not exposed
- **WHEN** an item with `quantity` `"1 Scheibe"` is scaled by a factor carrying many decimal places, such as a portion factor of 0.748
- **THEN** its `quantity` reads `"0.7 Scheibe"` rather than reproducing the factor's full precision

### Requirement: An unparseable quantity label is replaced by one derived from the scaled amount
When an item's `quantity` has no leading number and its scaled `amountGml` is present and greater than zero, the system SHALL replace `quantity` with a label built from that scaled amount and a unit. The unit SHALL be inferred from the original `quantity` text: `ml` when that text mentions `ml` or `l`, and `g` otherwise. The derived label SHALL be consistent with the amount written by the same scaling operation.

This unit inference is an approximation: `amountGml` is a single number that does not distinguish grams from millilitres, so the unit is taken from the text being replaced rather than known.

#### Scenario: A descriptive label becomes a derived weight
- **WHEN** an item with `quantity` `"eine Handvoll"` and `amountGml` `100` is scaled by a factor of 2
- **THEN** its `quantity` reads `"200 g"` and its `amountGml` reads `200`

#### Scenario: A liquid label derives a millilitre unit
- **WHEN** an item with `quantity` `"einige ml"` and `amountGml` `200` is scaled by a factor of 1.5
- **THEN** its `quantity` reads `"300 ml"`, the unit taken from the `ml` mentioned in the original text

#### Scenario: A descriptive liquid label without a unit token derives grams
- **WHEN** an item with `quantity` `"ein Schluck Milch"` and `amountGml` `200` is scaled by a factor of 1.5
- **THEN** its `quantity` reads `"300 g"`, because the inference matches only the tokens `ml` and `l` and the word "Milch" does not qualify

#### Scenario: An empty label is derived rather than left blank
- **WHEN** an item with an empty `quantity` and `amountGml` `50` is scaled by a factor of 2
- **THEN** its `quantity` reads `"100 g"`

### Requirement: A quantity label with nothing to derive from is left unchanged
When an item's `quantity` has no leading number **and** its scaled `amountGml` is absent, empty, or not greater than zero, the system SHALL leave `quantity` exactly as it was. Scaling SHALL NOT blank a label it cannot improve.

#### Scenario: No amount to derive from
- **WHEN** an item with `quantity` `"eine Handvoll"` and no `amountGml` is scaled by a factor of 2
- **THEN** its `quantity` still reads `"eine Handvoll"`

#### Scenario: A zero amount derives nothing
- **WHEN** an item with `quantity` `"eine Handvoll"` and `amountGml` `0` is scaled by a factor of 2
- **THEN** its `quantity` still reads `"eine Handvoll"`

#### Scenario: An empty label with no amount stays empty
- **WHEN** an item with an empty `quantity` and no `amountGml` is scaled by any factor
- **THEN** its `quantity` is still empty and no label is invented

### Requirement: Every portion-scaling path scales quantity labels
The system SHALL apply the quantity-label rules above on every path that scales a meal's items by a factor — recipe/ate portion entry, the portion chips in the meal editor, and reuse of a cached meal scaled to a requested amount — so that no path can leave a label disagreeing with the amount beside it.

#### Scenario: The portion chips move the label
- **WHEN** the user taps a portion chip in the meal editor that halves a meal whose item reads `"2 eggs"` at `120` g
- **THEN** that item reads `"1 eggs"` at `60` g, both changing together

#### Scenario: Recipe-and-ate entry moves the label
- **WHEN** the user enters that the recipe makes 4 portions and they ate 1, for an item reading `"400 g"`
- **THEN** that item reads `"100 g"`

#### Scenario: Reused cached meal moves the label
- **WHEN** a cached meal whose item reads `"500 ml"` at `500` is reused for a request of `250 ml`
- **THEN** the prepared draft's item reads `"250 ml"` at `250`
