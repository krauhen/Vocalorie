## ADDED Requirements

### Requirement: Chart axis labels render inside their card
The system SHALL render every axis label of the calorie-over-time chart fully within its enclosing card, with no label clipped by the card's edge, and SHALL keep each y-axis label aligned with the gridline it names.

#### Scenario: The zero label is fully legible
- **WHEN** the "Calories over time" chart renders as the last element of the daily stats card
- **THEN** the bottom y-axis label `0` is shown in full, not vertically cut by the card edge

#### Scenario: Labels still name their gridlines
- **WHEN** the chart renders with a maximum, midpoint and zero tick
- **THEN** each label sits beside the gridline whose value it states

### Requirement: Heatmap range labels use one pattern and an explicit locale
The system SHALL render the heatmap's two range labels — the start of the window and today — with the same date pattern, so the two ends of one row cannot read differently. The pattern SHALL be numeric, avoiding month names, so no locale's abbreviation data can make one end carry a trailing period the other lacks, and it SHALL follow the numeric day-month-year convention already used by the day navigator.

Every user-facing date and time formatter on the entries screen SHALL name its locale explicitly rather than relying on the ambient default, and the label formatting SHALL be pure, JVM-testable logic so a locale regression is caught by a unit test rather than on a device.

#### Scenario: Both range labels read the same way
- **WHEN** the heatmap's window starts on 18 May and ends on 20 August
- **THEN** both labels use the same numeric pattern, and neither carries a trailing abbreviation period the other lacks

#### Scenario: No month abbreviation appears
- **WHEN** the range labels render in any device locale
- **THEN** neither label contains an abbreviated month name

#### Scenario: The label format is locale-explicit and testable
- **WHEN** the range label is produced for a given date and locale
- **THEN** the result is derived by pure logic taking that locale as an input, without reading an ambient default
