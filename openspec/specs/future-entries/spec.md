# future-entries

## Purpose

TBD — capture the intent of allowing navigation to and logging of future-dated meal entries.

## Requirements

### Requirement: Navigation to future dates
The system SHALL allow the day navigator to move to dates after today, removing the current restriction that blocks negative day offsets. This applies to `DayNavigator`'s "Next day" control and the underlying window functions in `app/src/main/java/com/example/vocalorie/ui/entries/MealTimeWindows.kt` (`selectedDayWindow`, `selectedDayHistogramWindow`), both of which currently reject negative offsets.

#### Scenario: Next-day control is enabled past today
- **WHEN** the user is viewing today in the day navigator (`app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt`)
- **THEN** the "Next day" control is enabled and pressing it moves the view to tomorrow

#### Scenario: Repeated forward navigation reaches any future date
- **WHEN** the user repeatedly presses "Next day" from today
- **THEN** the view continues advancing into future calendar dates without being blocked or throwing an error

### Requirement: Today's window includes the full calendar day
The system SHALL compute "today"'s time window as the full local calendar day (midnight to midnight), not truncated at the current instant, so that meals logged with a future timestamp within today are included.

#### Scenario: Same-day future-timestamped meal is visible
- **WHEN** a meal is logged with a timestamp later today than the current instant, and the user is viewing today
- **THEN** that meal appears in today's meal list and is included in today's per-day stats and histogram

### Requirement: Future-dated entries are visually highlighted
The system SHALL visually distinguish any meal or activity entry row whose timestamp is in the future (relative to the current instant) with a dashed-border treatment plus a diagonal hatch-stripe fill, both rendered in that row's own calorie-magnitude bucket color, layered on top of the entry's existing calorie-bucket styling rather than replacing it. This applies to both `MealEntryRow` and `ActivityEntryRow` in `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt`.

#### Scenario: Future meal entry shows a dashed border and hatch fill in its bucket color
- **WHEN** a meal entry's timestamp is later than the current instant
- **THEN** its row renders with a dashed border and a diagonal hatch-stripe fill, both in its calorie-bucket color, in addition to its normal calorie-bucket background/border color

#### Scenario: Future activity entry shows the same treatment
- **WHEN** an activity entry's timestamp is later than the current instant
- **THEN** its row renders with the same dashed-border-plus-hatch-fill treatment, in its own calorie-bucket color, matching the treatment already applied to future meal entries

#### Scenario: Past or present entries are unaffected
- **WHEN** a meal or activity entry's timestamp is at or before the current instant
- **THEN** its row renders with its normal calorie-bucket styling only, no dashed border or hatch fill

### Requirement: Heatmap grid stays locked to today and past
The system SHALL NOT extend the heatmap grid's visible date range into the future, even though day navigation elsewhere now supports future dates. The heatmap in `app/src/main/java/com/example/vocalorie/ui/entries/stats/MealStatsOverview.kt` and its aggregation in `MealStatsCalculator.kt` continue to anchor their fixed window's ceiling at today.

#### Scenario: Selecting a future date does not add a future column to the heatmap
- **WHEN** the user navigates to a future date via the day navigator
- **THEN** the heatmap grid's visible window is unchanged and still ends at today, showing no column for the future date
