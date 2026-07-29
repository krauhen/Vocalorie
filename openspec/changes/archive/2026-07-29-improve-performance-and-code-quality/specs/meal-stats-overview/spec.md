## ADDED Requirements

### Requirement: Stats and heatmap values are computed once per input change
The system SHALL compute the statistics aggregate and the per-day heatmap scores at most once per change to their inputs — the meal history, the selected range, and the reference instant — rather than once per render. The reference instant SHALL be an explicit input rather than being read inside the computation, so the result is cacheable. Selecting a day, whether through the day navigator or by tapping a heatmap cell, SHALL NOT by itself recompute the aggregate or the per-day scores.

#### Scenario: Selecting a day does not recompute the aggregate
- **WHEN** the user selects a different day and no entry has been added, edited or deleted
- **THEN** the statistics aggregate and the per-day heatmap scores are reused rather than recomputed

#### Scenario: Changing the range recomputes once
- **WHEN** the user switches the day-range selector
- **THEN** the range-scoped tiles recompute exactly once for that change

#### Scenario: Adding an entry recomputes once
- **WHEN** the user saves a new meal
- **THEN** the aggregate and the affected heatmap day recompute once, and the displayed values reflect the new meal

### Requirement: Persisted meal totals are authoritative and consistent with their items
The system SHALL persist each meal's nutrition totals and MAY read those persisted totals when computing statistics and heatmap values instead of re-deriving them from the meal's stored item list. Persisted totals SHALL equal the totals derived from that meal's items at the time it was saved, and the system SHALL maintain that equality on every write.

#### Scenario: Persisted totals match item-derived totals
- **WHEN** a reviewed meal is saved
- **THEN** its persisted totals equal the sum of its items' values

#### Scenario: Statistics computed from persisted totals match item-derived statistics
- **WHEN** the statistics aggregate is computed for a set of meals
- **THEN** the values shown are the same as those obtained by summing each meal's items

#### Scenario: Editing a meal keeps its totals in step
- **WHEN** the user edits a meal's items and saves
- **THEN** the persisted totals are updated to match the edited items
