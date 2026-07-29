## ADDED Requirements

### Requirement: No blocking work on the main thread
The system SHALL NOT perform image decoding, bitmap transformation, cryptographic operations, `SharedPreferences` access, file or database I/O, or JSON serialization on the main thread. Any such work SHALL be dispatched to a background dispatcher, and the UI SHALL remain interactive while it runs.

#### Scenario: Attaching multiple photos keeps the UI interactive
- **WHEN** the user selects the maximum number of gallery images for a meal attachment
- **THEN** the UI remains interactive throughout decoding, rotation, downsampling and compression, and no frame is blocked waiting for that work

#### Scenario: Reading a stored secret does not block the UI
- **WHEN** the app reads or writes an encrypted API key, requiring AndroidKeyStore access and an AES/GCM cipher operation
- **THEN** that work runs off the main thread and the UI does not stall

#### Scenario: Settings screen does not read preferences during composition
- **WHEN** the settings screen recomposes, including on every keystroke in a text field
- **THEN** no `SharedPreferences` read or cipher operation is performed as part of that recomposition

### Requirement: Whole-history derivations are computed once per input change
The system SHALL compute derivations that scan the full meal or activity history — statistics aggregates, streaks, heatmap cell values, and history search — at most once per change to their inputs, rather than once per recomposition. Any clock value such derivations depend on SHALL be supplied as an explicit input so that the result is cacheable.

#### Scenario: Navigating between days does not recompute full-history statistics
- **WHEN** the user taps through several days on the day navigator without any entry being added, edited or deleted
- **THEN** the full-history statistics aggregate is not recomputed for those taps

#### Scenario: Tapping a heatmap cell does not recompute every cell
- **WHEN** the user taps a heatmap cell to select that day
- **THEN** the per-day nutrition scores backing the heatmap cells are not recomputed as a result of that tap

#### Scenario: Typing in an unrelated field does not re-run history search
- **WHEN** the user types into a field other than the meal-history search field
- **THEN** the meal-history search is not re-executed

### Requirement: Editing a meal only recomposes what changed
The system SHALL structure meal editor state and callbacks so that editing one food item or one meal-level field does not invalidate the editor rows for unrelated food items.

#### Scenario: Typing in one item does not recompose the others
- **WHEN** the user types a character into one food item's field in a meal containing several items
- **THEN** the editor rows for the other food items are not recomposed

#### Scenario: Editing a meal-level field does not recompose every item
- **WHEN** the user types a character into the meal title
- **THEN** the individual food item editor rows are not recomposed

### Requirement: Grouped nutrition values are passed as a single typed value
The system SHALL pass the group of per-item and per-meal nutrition values (calories, amount, protein, carbohydrates, fat, saturated fat, sugar, salt) as one named value type rather than as a positional list of same-typed parameters, so that a mis-ordered value is a compile error rather than a silent data corruption.

#### Scenario: Nutrition values cannot be silently transposed
- **WHEN** a caller supplies nutrition values to the meal editor's change callback
- **THEN** the values are carried by a named type, so transposing two values of the same underlying type does not compile
