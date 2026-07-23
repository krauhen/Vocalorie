# activity-logging

## Purpose

Let the user log physical activities alongside meals in the entries screen, persisting them locally and presenting them via a dedicated Activities tab with manual entry, editing, and day-scoped sorting.

## Requirements

### Requirement: Meals and Activities tabs below the grid
The system SHALL present two tabs — "Meals" and "Activities" — in a Material3 `TabRow` (each tab with a text label and an icon) positioned directly below the heatmap grid in the entries screen. The `DayNavigator`, the daily stats header, and the heatmap grid SHALL remain shared and visible above the tabs regardless of the active tab; only the list content below the tabs SHALL switch between meal entries and activity entries.

#### Scenario: Both tabs are visible below the grid
- **WHEN** the user opens the entries screen
- **THEN** a "Meals" tab and an "Activities" tab (each with an icon) are shown below the heatmap grid, with "Meals" selected by default

#### Scenario: Switching tabs swaps only the list
- **WHEN** the user taps the "Activities" tab
- **THEN** the list below the tabs shows activity entries for the selected day
- **AND** the DayNavigator, daily stats header, and heatmap grid above the tabs remain unchanged and visible

### Requirement: Activity data model
The system SHALL persist each activity as a row in a new Room `activities` table (new `ActivityEntity`, `ActivityDao`, and a `MIGRATION_5_6` that additively creates the table, bumping the database to version 6 with no destructive migration). Each activity SHALL have: a full timestamp (date and time-of-day, stored as epoch milliseconds), a required type, a free-text title, a description, calories burned (kcal), and a duration in whole minutes.

#### Scenario: Activity persists across app restart
- **WHEN** the user adds an activity and fully restarts the app
- **THEN** the activity reappears in the Activities list on its logged day with all fields intact

#### Scenario: Existing meal data survives the migration
- **WHEN** the app upgrades an existing database (schema v5) to v6
- **THEN** the `activities` table is created and all existing meals remain intact (no destructive migration)

### Requirement: Required activity type with icon
The system SHALL require the user to select an activity type from exactly this set: running, walking, bike, kettlebell, gym, hiking, swimming. Each type SHALL have a distinct icon, and that icon SHALL be shown on every activity row in the list. The title SHALL remain a separate free-text field, independent of the type.

#### Scenario: Type is required when adding
- **WHEN** the user opens the activity form
- **THEN** a type must be chosen from the seven allowed types before the activity can be saved

#### Scenario: Type icon shown on each row
- **WHEN** the Activities list renders an activity
- **THEN** the row displays the icon corresponding to that activity's type alongside its title, kcal, and duration

### Requirement: Manual activity entry via the Add button
The system SHALL make the bottom-right action button context-aware: on the Meals tab it opens meal capture as it does today; on the Activities tab it opens a simple manual activity form with fields for date and time-of-day, type, title, description, calories burned, and duration. The activity form SHALL NOT use voice or AI parsing. When the add form is opened, the title field SHALL be pre-filled with the currently selected activity type's display name and SHALL remain freely editable; the pre-fill applies only when adding a new activity, not when editing an existing one, and the title does not automatically change if the type is subsequently changed. When the selected type is `STEPS`, the form's time-of-day SHALL default to 23:59 on the selected day (steps are typically logged at the end of the day) and SHALL remain editable; all other types keep their existing default time.

#### Scenario: Add on the Activities tab opens the activity form
- **WHEN** the Activities tab is active and the user taps the "Add" button
- **THEN** a manual activity form opens with fields for date+time, type, title, description, kcal burned, and duration

#### Scenario: Add on the Meals tab opens meal capture
- **WHEN** the Meals tab is active and the user taps the "Add" button
- **THEN** the existing meal-capture entry point opens (unchanged behavior)

#### Scenario: Title is pre-filled with the type name
- **WHEN** the user opens the add-activity form with the type set to Running
- **THEN** the title field already contains "Running" and can be saved as-is or edited

#### Scenario: Pre-filled title is editable and not auto-tracked
- **WHEN** the user edits the pre-filled title or changes the type after the field is pre-filled
- **THEN** the user's title text is preserved and is not overwritten by a later type change

#### Scenario: Steps default to 23:59
- **WHEN** the user opens the add-activity form with the type set to Steps for the selected day
- **THEN** the time-of-day defaults to 23:59 on that day and remains editable

#### Scenario: Non-step types keep their default time
- **WHEN** the user opens the add-activity form with a non-step type (e.g. Running)
- **THEN** the time-of-day uses the existing default, not 23:59

#### Scenario: Editing an existing activity is unaffected
- **WHEN** the user opens an existing activity to edit it
- **THEN** its saved title and timestamp are shown unchanged, with no pre-fill or 23:59 default applied

### Requirement: Duration entered in minutes, displayed as hours and minutes
The system SHALL accept activity duration as a whole number of minutes and SHALL display it as hours and minutes.

#### Scenario: 61 minutes displays as 1h 1m
- **WHEN** the user enters a duration of 61 minutes
- **THEN** the activity displays its duration as "1h 1m"

### Requirement: Edit and delete activities like meals
The system SHALL let the user open an activity entry to view, edit, or delete it, mirroring the meal entry overlay pattern (tap a row to open an editor reusing the activity form fields, with the same delete affordance meals use).

#### Scenario: Edit an activity
- **WHEN** the user taps an activity row and edits a field, then saves
- **THEN** the activity updates in place and the list reflects the change

#### Scenario: Delete an activity
- **WHEN** the user deletes an activity from its overlay
- **THEN** the activity is removed from the list and no longer persisted

### Requirement: Activities sort the same as meals
The system SHALL filter activities to the currently selected day using the same day-window/selected-day mechanism the meal list uses, and SHALL order them within a day the same way meals are ordered (chronological by timestamp, matching the meal list's sort).

#### Scenario: Activities follow the selected day
- **WHEN** the user changes the selected day via the DayNavigator or heatmap
- **THEN** the Activities list shows only that day's activities, ordered the same way meals are ordered
