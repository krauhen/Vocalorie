# visual-baseline Specification

## Purpose
TBD - created by archiving change improve-performance-and-code-quality. Update Purpose after archive.
## Requirements
### Requirement: Appearance is unchanged except by an enumerated allowlist
The system's visual appearance SHALL be preserved across this change. Performance, architecture, deduplication and data-layer work SHALL NOT alter layout, spacing, typography, colour, iconography, tinting, ordering, or wording. A visual difference from the pre-change baseline SHALL be treated as a defect unless it appears on the allowlist declared for this change.

The allowlist is exactly:
1. Numeric values no longer leaking raw floating-point precision (see "Numeric values render in human-readable form").
2. A progress indication shown while gallery attachments are being prepared.
3. A warning surfaced on an estimate whose grounding pass failed.
4. The two pre-existing layout squeezes named in "Squeezed labels render in full".

#### Scenario: A stage that changes appearance is rejected
- **WHEN** a stage is walked through on the device and a screen differs visually from the captured baseline in a way not on the allowlist
- **THEN** the difference is treated as a defect introduced by this change and is corrected, rather than accepted as an improvement

#### Scenario: Calorie-state tinting is preserved
- **WHEN** the meal entries list renders meals of widely differing calorie values
- **THEN** each row keeps its existing calorie-derived tint, so a high-calorie meal remains visually distinguishable from a low-calorie one at a glance

#### Scenario: Macro colours, food-type icons and the score heatmap are preserved
- **WHEN** the entries screen and stats overview render
- **THEN** protein, carbohydrate and fat keep their existing colours; each meal row keeps its food-type icon in the same position; and the heatmap keeps its existing score-derived colour gradient and neutral no-data colour

#### Scenario: The per-tab palette swap is preserved
- **WHEN** the user switches between the Meals and Activities tabs
- **THEN** the accent palette changes exactly as it does today

### Requirement: Numeric values render in human-readable form
The system SHALL render every user-facing numeric value — in text fields, labels, and summary rows — without exposing raw binary floating-point representation. A value SHALL NOT be displayed with spurious trailing precision, and SHALL NOT be displayed in scientific notation. A value the user entered SHALL render on redisplay as the value they entered.

#### Scenario: An entered setting reads back as entered
- **WHEN** the user enters `30` for calories burned per 1,000 steps and the setting is redisplayed
- **THEN** it reads `30`, not `29.999999329447746`

#### Scenario: Meal totals render without spurious precision
- **WHEN** the meal editor displays computed totals
- **THEN** values render as `816.65`, `5.7` and `3.45` rather than `816.6500000000000004`, `5.7000000000000004` and `3.450000000000004`

#### Scenario: Item values render without spurious precision
- **WHEN** an item card displays a computed nutrition value
- **THEN** it renders as `1.75` rather than `1.7500000000000002`

#### Scenario: Small values avoid scientific notation
- **WHEN** a value of `0.0001` is displayed in an editable field
- **THEN** it renders as `0.0001` rather than `1.0E-4`

### Requirement: Squeezed labels render in full
The system SHALL render control and field labels without breaking a single word across lines. The portion-scaling quick-select chips SHALL fit their labels on one line, and paired nutrition field labels SHALL not wrap mid-word.

#### Scenario: The portion "All" chip reads horizontally
- **WHEN** the portion-scaling quick-select chips render
- **THEN** the "All" chip shows its label on one line, not one character per line

#### Scenario: The carbohydrate field label does not split mid-word
- **WHEN** an editable item card renders its paired nutrition fields
- **THEN** the carbohydrate label is not broken mid-word across two lines

