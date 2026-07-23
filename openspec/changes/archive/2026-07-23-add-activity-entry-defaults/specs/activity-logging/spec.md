## MODIFIED Requirements

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
