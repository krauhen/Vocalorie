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

### Requirement: Future/past classification re-evaluates on refresh
The system SHALL classify each entry as future or past against a current instant that advances when the entries screen is refreshed, not only when a write reloads the list. The entries screen (`app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt`) SHALL provide a pull-to-refresh (swipe-down) gesture that reloads entries from the database AND recomputes the current instant used for future/past classification, replacing the previous behavior where the instant was memoized against the loaded meal list (`remember(meals) { Instant.now() }`) and therefore only changed on a write. The refresh indicator SHALL remain visible for a brief minimum duration so the near-instant local reload reads as a deliberate action rather than a flicker.

#### Scenario: Passed-time entry un-crosses after pull-to-refresh
- **WHEN** an entry is currently rendered with the future (dashed-border + hatch) treatment because its timestamp is later than the last-computed instant, its timestamp has since become earlier than the real current time, and the user performs a pull-to-refresh
- **THEN** the entry is reloaded and reclassified as past, rendering with its normal calorie-bucket styling only and no dashed/hatch treatment

#### Scenario: Still-future entry stays crossed after pull-to-refresh
- **WHEN** an entry's timestamp is still later than the current time and the user performs a pull-to-refresh
- **THEN** the entry continues to render with the dashed-border + hatch future treatment

#### Scenario: Refresh does not require a write
- **WHEN** the user performs a pull-to-refresh without adding or editing any entry
- **THEN** the current instant is recomputed and future/past classification updates accordingly, independent of any save action
