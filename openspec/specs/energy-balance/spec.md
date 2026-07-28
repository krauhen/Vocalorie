# energy-balance

## Purpose

Surface a daily energy picture — calories burned and the consumed-vs-burned balance — in the stats header, driven by a configurable base burn plus logged activities. The header figures use the full activity total; logged activities additionally raise the day nutrition score's calorie target (at a 50% discount).

## Requirements

### Requirement: Configurable base calories burned per day
The system SHALL provide a single global "base calories burned per day" setting, editable in Settings via a numeric field following the existing numeric-setting pattern, defaulting to 2400 kcal and persisted across app restarts. This value SHALL apply to every day; there SHALL be no per-day override.

#### Scenario: Default base burn
- **WHEN** the user has never set a base-burn value
- **THEN** the system uses 2400 kcal as the base calories burned per day

#### Scenario: Editing base burn persists
- **WHEN** the user sets the base calories burned to a new value and restarts the app
- **THEN** the new value is used for all days' calculations

### Requirement: Daily burned calories shown in stats header
The system SHALL show, in the daily stats header below the consumed-calories figure, a burned-calories figure equal to the base calories burned per day plus the sum of calories burned by that day's logged activities. This figure SHALL always be shown; on a day with no logged activities it SHALL equal the base burn alone.

#### Scenario: Burned shown with no activities
- **WHEN** the selected day has no logged activities and the base burn is 2400
- **THEN** the header shows burned calories of 2400

#### Scenario: Burned includes activities
- **WHEN** the selected day has activities burning a total of 500 kcal and the base burn is 2400
- **THEN** the header shows burned calories of 2900

### Requirement: Daily balance (deficit/surplus) shown in stats header
The system SHALL show, in the daily stats header, a balance figure computed as `consumed − base burned − sum of activities`. A negative value SHALL read as a deficit and a positive value SHALL read as a surplus; the figure SHALL be signed and color-coded to distinguish deficit from surplus, with a deficit (negative balance) shown in a positive/favorable color and a surplus (positive balance) shown in the error/alert color, since a calorie deficit is the outcome most users are working toward.

#### Scenario: Deficit when eating under burn
- **WHEN** the user consumed 2000 kcal, base burn is 2400, and there are no activities
- **THEN** the balance shows −400 (a deficit), rendered in the positive/favorable color

#### Scenario: Surplus when eating over burn
- **WHEN** the user consumed 2800 kcal, base burn is 2400, and there are no activities
- **THEN** the balance shows +400 (a surplus), rendered in the error/alert color

#### Scenario: Activities deepen the deficit
- **WHEN** the user consumed 2400 kcal, base burn is 2400, and activities burned 300 kcal
- **THEN** the balance shows −300 (a deficit), rendered in the positive/favorable color

### Requirement: Activities raise the day's calorie target
The system SHALL let a day's logged activities raise the calorie target used by the day nutrition score, by adding **50% of the day's total activity calories burned** to the user's calorie goal, as specified by the `day-nutrition-score` capability. The heatmap and daily-header score SHALL reflect this activity-adjusted target. This affects only the score's calorie target; the burned-calories and balance figures shown in the stats header (which use the base burn plus the full activity total) SHALL remain unchanged.

#### Scenario: Active day expands the scoring allowance
- **WHEN** a day has activities burning 600 kcal and the calorie goal is 2400
- **THEN** the day nutrition score treats 2700 kcal (2400 + 0.5 × 600) as the on-target intake, so eating more on an active day is not penalized

#### Scenario: Header figures still use the full activity total
- **WHEN** a day has activities burning 600 kcal, base burn 2400, consumed 2700
- **THEN** the header burned figure (3000) and balance figure (−300) are computed from the full activity total and base burn, unchanged by the score's 50% add-back
