# entry-day-targeting

## Purpose

New meals and activities should be filed under the day the user is currently viewing in the history screen, rather than always defaulting to today, so adding entries while reviewing a past or future day lands them on that day.

## Requirements

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

### Requirement: The viewed day tracks the wall clock
The system SHALL resolve the entries screen's viewed day against a clock reading that follows the real calendar day, so the day label, the resolved date, the visible entries, the day's totals and the heatmap selection never describe a day that has passed. The clock reading SHALL be refreshed when the entries screen resumes and again as each local calendar day turns, without requiring the user to save an entry or pull to refresh.

The entries screen SHALL read one clock — the state holder's — rather than keeping an independent reading of its own, so the value that stamps a saved entry and the value that resolves the displayed day are always the same.

When the calendar day changes while the screen is open, a "today" selection SHALL follow the clock and continue to mean the new current day, while a selection the user made explicitly SHALL keep the absolute date it referred to, being re-anchored by the number of days that passed. The re-anchoring arithmetic SHALL be pure, JVM-testable logic (`app/src/main/java/com/example/vocalorie/ui/entries/MealTimeWindows.kt`).

#### Scenario: Midnight passes with the app open on today
- **WHEN** the app is left open on the entries screen viewing today and the local calendar day turns, with no entry saved and no pull-to-refresh
- **THEN** the day label and the date shown beside it advance to the new day, and the visible entries and day totals are those of the new day

#### Scenario: Resuming after midnight
- **WHEN** the app is backgrounded before midnight and resumed after it
- **THEN** the viewed day resolves to the new current day on resume, before the user interacts with anything

#### Scenario: A stale day never presents itself as today
- **WHEN** the resolved date of the viewed day differs from the current local date
- **THEN** no label reading "Today" is shown for it

#### Scenario: An explicitly selected past day keeps its date across midnight
- **WHEN** the user has selected the day before today and the calendar day turns while that day is displayed
- **THEN** the same absolute date remains selected, now shown as two days ago rather than silently becoming the day before it

#### Scenario: An explicitly selected future day keeps its date across midnight
- **WHEN** the user has selected tomorrow and the calendar day turns
- **THEN** the same absolute date remains selected, now being the current day

#### Scenario: A new entry still lands on the day being viewed
- **WHEN** the calendar day has turned while the app was open and the user then saves a meal while viewing today
- **THEN** the entry is timestamped on the new current day, consistent with the day the screen displays
