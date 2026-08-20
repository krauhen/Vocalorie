## ADDED Requirements

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
