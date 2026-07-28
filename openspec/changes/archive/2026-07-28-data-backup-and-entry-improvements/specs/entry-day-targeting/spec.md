## ADDED Requirements

### Requirement: New entries are dated to the viewed day
The system SHALL date a newly created meal or activity to the day currently selected in the entries screen's day navigator, using the current wall-clock time on that day, instead of always defaulting to the present moment. The currently-viewed day (`selectedDayOffset` in `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt`) SHALL be available to the save path in `app/src/main/java/com/example/vocalorie/ui/MealCaptureScreen.kt`, and the resolved timestamp SHALL be that day at the current local time-of-day.

#### Scenario: Adding while viewing a past day
- **WHEN** the user is viewing a past day and saves a new meal or activity
- **THEN** the entry's timestamp is that past day at the current wall-clock time, and it appears under that day rather than today

#### Scenario: Adding while viewing today is unchanged
- **WHEN** the user is viewing today (day offset 0) and saves a new meal or activity
- **THEN** the entry is timestamped at approximately the current moment, matching the prior behavior

#### Scenario: Adding while viewing a future day
- **WHEN** the user is viewing a future day and saves a new meal or activity
- **THEN** the entry is dated to that future day and is treated as a future (planned) entry

#### Scenario: Editing keeps the stored timestamp
- **WHEN** the user edits an already-saved entry
- **THEN** its existing stored timestamp is preserved and is not replaced by the selected-day timestamp
