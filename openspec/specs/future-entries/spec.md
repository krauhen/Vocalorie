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

### Requirement: Future-dated meal entries are visually highlighted
The system SHALL visually distinguish any meal entry row whose timestamp is in the future (relative to the current instant) with a dotted-border treatment, layered on top of the entry's existing calorie-bucket styling rather than replacing it. This applies to `MealEntryRow` in `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt`.

#### Scenario: Future entry shows a dotted border alongside its calorie coloring
- **WHEN** a meal entry's timestamp is later than the current instant
- **THEN** its row renders with a dotted border in addition to its normal calorie-bucket background/border color

#### Scenario: Past or present entries are unaffected
- **WHEN** a meal entry's timestamp is at or before the current instant
- **THEN** its row renders with its normal calorie-bucket styling only, no dotted border

### Requirement: Heatmap grid stays locked to today and past
The system SHALL NOT extend the heatmap grid's visible date range into the future, even though day navigation elsewhere now supports future dates. The heatmap in `app/src/main/java/com/example/vocalorie/ui/entries/stats/MealStatsOverview.kt` and its aggregation in `MealStatsCalculator.kt` continue to anchor their fixed window's ceiling at today.

#### Scenario: Selecting a future date does not add a future column to the heatmap
- **WHEN** the user navigates to a future date via the day navigator
- **THEN** the heatmap grid's visible window is unchanged and still ends at today, showing no column for the future date
