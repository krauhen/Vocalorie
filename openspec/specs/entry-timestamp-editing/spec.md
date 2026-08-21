# entry-timestamp-editing Specification

## Purpose
TBD - created by archiving change 2026-08-20-date-time-picker-in-entry-editors. Update Purpose after archive.
## Requirements
### Requirement: An entry's timestamp can be set from a calendar picker
When editing a meal or an activity, the system SHALL offer a calendar date picker for that entry's timestamp, reachable directly from the timestamp field, and seeded with the timestamp the entry currently holds. Reaching a date far in the past SHALL NOT require repeated stepping or retyping.

#### Scenario: Opening the picker on the current date
- **WHEN** the user opens the date picker from an entry whose timestamp is 14 March
- **THEN** the calendar opens showing March with the 14th selected

#### Scenario: A date weeks back is selected directly
- **WHEN** the user opens the picker on an entry dated today and selects a date three weeks earlier
- **THEN** the entry's date becomes that date, without any intermediate date being applied on the way

#### Scenario: Dismissing changes nothing
- **WHEN** the user opens the date picker and dismisses it without confirming
- **THEN** the entry's timestamp is unchanged

### Requirement: Choosing a date preserves the entry's time of day
When a date is chosen from the picker, the system SHALL keep the entry's existing local time-of-day and change only its calendar date. It SHALL NOT substitute midnight, the current wall-clock time, or any other default.

#### Scenario: Evening meal moved to an earlier day stays an evening meal
- **WHEN** an entry timestamped 14 March at 19:45 has its date changed to 2 March via the picker
- **THEN** its timestamp is 2 March at 19:45

#### Scenario: A boundary time is not invented
- **WHEN** any entry's date is changed via the picker
- **THEN** its time-of-day is not set to midnight or to the end of the day

### Requirement: The time of day can be set without typing
The system SHALL offer a time picker for the entry's time-of-day, and choosing a time SHALL change only the time part, leaving the entry's calendar date unchanged.

#### Scenario: Time is corrected without touching the date
- **WHEN** an entry timestamped 2 March at 19:45 has its time changed to 12:30 via the time picker
- **THEN** its timestamp is 2 March at 12:30

#### Scenario: The whole timestamp is reachable without the keyboard
- **WHEN** the user changes both the date and the time of an entry using the pickers
- **THEN** the entry's full timestamp is updated with no text typed into the timestamp field

### Requirement: Typing a timestamp remains available and unchanged
The system SHALL continue to accept a typed timestamp in the entry editors, with the same accepted format, the same validation, and the same error presentation as before the pickers existed. A picked value and a typed value SHALL be treated identically once applied.

#### Scenario: Typing still works
- **WHEN** the user types a valid timestamp into the field instead of using a picker
- **THEN** the entry's timestamp is updated exactly as it was previously

#### Scenario: An invalid typed value is still rejected
- **WHEN** the user types an unparseable timestamp
- **THEN** the field reports the error and the entry cannot be saved until it is valid

#### Scenario: The picker resolves a half-typed value
- **WHEN** the field holds an unparseable half-typed timestamp and the user picks a date and a time
- **THEN** the field holds the picked timestamp, is no longer in error, and the entry can be saved

### Requirement: Both the meal editor and the activity editor offer the same control
The system SHALL present the same timestamp-editing behaviour — the same field, the same pickers, the same accepted format, and the same error presentation — in the meal editor and in the activity editor.

#### Scenario: An activity's timestamp is edited the same way
- **WHEN** the user edits an activity's timestamp
- **THEN** the control behaves the same as it does for a meal, with the same picker and the same validation

### Requirement: The pickers permit exactly the dates the app already permits
The pickers SHALL NOT narrow or widen the set of dates an entry may carry. Any date the app permits an entry to hold — including future dates, which are permitted by the future-entries capability — SHALL be selectable in the picker.

#### Scenario: A future date is selectable
- **WHEN** the user opens the date picker and moves to a date after today
- **THEN** that date is selectable, and choosing it dates the entry to that future day

#### Scenario: The picked entry is treated as a future entry
- **WHEN** an entry is given a future date via the picker
- **THEN** it is treated as a future entry exactly as one dated by any other means, with no special-casing for having been picked

### Requirement: A picked timestamp lands on the day the user selected
The system SHALL apply the picked calendar date as that date in the device's local time zone, so that the entry appears under the day the user tapped, including across a daylight-saving transition.

#### Scenario: The entry files under the tapped day
- **WHEN** the user picks 2 March for an entry
- **THEN** the entry appears under 2 March in the entries list, not under 1 or 3 March

#### Scenario: A daylight-saving transition does not shift the day
- **WHEN** the user picks a date on which the local time zone changes its offset
- **THEN** the entry still falls on that calendar date locally

