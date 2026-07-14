## ADDED Requirements

### Requirement: Stats overview placement
The system SHALL display a meal-logging stats overview at the top of the meal entries screen, above the list of individual meal entries.

#### Scenario: Stats overview visible on main view
- **WHEN** the user opens the app and the meal entries screen renders
- **THEN** the stats overview section is shown above the meal entry list, even when the list is empty

### Requirement: Day-range selector
The system SHALL provide a segmented control to select the stats time window: All, 30 days, or 7 days, defaulting to 30 days.

#### Scenario: Default range on first render
- **WHEN** the meal entries screen renders for the first time in a session
- **THEN** the day-range selector shows "30d" as selected and range-scoped tiles reflect the last 30 days

#### Scenario: Switching range updates scoped stats
- **WHEN** the user selects "7d" or "All" on the segmented control
- **THEN** the meals-logged count, active-days count, and calendar heatmap recompute to reflect only meals within the newly selected range

### Requirement: Meals-logged and active-days tiles
The system SHALL display the total count of meals logged and the count of distinct calendar days with at least one meal, both scoped to the selected day range.

#### Scenario: Multiple meals same day counted once for active days
- **WHEN** the selected range contains 3 meals logged on the same calendar day and 1 meal on another day
- **THEN** the meals-logged tile shows 4 and the active-days tile shows 2

#### Scenario: No meals in range
- **WHEN** the selected range contains zero logged meals
- **THEN** both tiles show 0 rather than an error or blank state

### Requirement: Streak tiles independent of range selector
The system SHALL compute current streak and longest streak from the user's full meal history, regardless of the selected day-range filter, and SHALL indicate that streaks are not limited by the range selector.

#### Scenario: Streak longer than selected range
- **WHEN** the user has logged at least one meal on each of the last 45 consecutive calendar days and the day-range selector is set to "7d"
- **THEN** the current-streak tile shows 45, not a value capped at 7

#### Scenario: Streak broken by a missed day
- **WHEN** the user's meal history has a calendar day with zero meals interrupting an otherwise consecutive run
- **THEN** the current streak counts only the consecutive run ending at the most recent logged day at or before today, and does not include days before the gap

#### Scenario: No meals logged at all
- **WHEN** the user has no meal entries
- **THEN** current streak and longest streak both show 0

### Requirement: Most-common meal tile
The system SHALL display the most frequently logged meal title within the selected day range, using existing meal-title normalization for matching equivalent titles.

#### Scenario: Clear most-common meal
- **WHEN** the selected range contains "Chicken Salad" logged 3 times and two other distinct titles logged once each
- **THEN** the most-common meal tile shows "Chicken Salad"

#### Scenario: Tie between titles
- **WHEN** two or more distinct meal titles are tied for the highest count within the selected range
- **THEN** the system deterministically selects one (most recently logged among the tied titles) rather than showing an inconsistent or randomized result across renders

### Requirement: Calendar heatmap
The system SHALL render a calendar-style heatmap showing, for each calendar day within the selected range, whether at least one meal was logged, with visually distinct intensity for days with meals versus days without.

#### Scenario: Heatmap reflects selected range only
- **WHEN** the day-range selector is set to "7d"
- **THEN** the heatmap displays exactly the last 7 calendar days, each marked as having meals or not

#### Scenario: "All" range heatmap bounded by history
- **WHEN** the day-range selector is set to "All" and the user's earliest logged meal is 90 days ago
- **THEN** the heatmap spans from that earliest logged day through today, not an arbitrary fixed window
