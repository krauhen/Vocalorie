## MODIFIED Requirements

### Requirement: An in-flight estimate names its current step
While a meal estimate is running, the system SHALL show a single line of text naming what the estimate is currently doing, in place of a fixed loading message. The line SHALL update as the estimate moves from one step to the next, so that a progressing estimate is distinguishable from a stalled one without any further interaction. The primary step line SHALL display only the active step in progress, while earlier steps and executed tool calls MAY be reviewed in an adjacent collapsible details section.

#### Scenario: The line replaces the fixed loading message
- **WHEN** the user starts an estimate
- **THEN** the capture overlay shows a step line describing the current step, and not a message that stays identical for the whole run

#### Scenario: The line advances as the estimate proceeds
- **WHEN** an estimate moves from researching sources to computing the nutrition values
- **THEN** the step line changes to describe the later step, without the user interacting with the screen

#### Scenario: Only the current step is shown on the primary step line
- **WHEN** an estimate has passed through several steps
- **THEN** the primary step line shows only the step currently in progress, while earlier steps and executed commands accumulate chronologically in a separate collapsible details section

## ADDED Requirements

### Requirement: Cancellation of in-flight estimation
While a meal estimate is running (`isLoading = true`), the system SHALL display an actionable Cancel control on the capture overlay. Triggering Cancel SHALL immediately abort the running estimation coroutine, clear the loading state and progress indicators, and preserve the entered meal description and attached images intact in the capture form so typos and attachments can be edited without re-entering data.

#### Scenario: Cancel button is visible during active estimation
- **WHEN** an estimate is in progress
- **THEN** an active Cancel action is visible on the capture overlay

#### Scenario: Tapping Cancel aborts the in-flight estimate
- **WHEN** the user triggers Cancel while an estimate is running
- **THEN** the running estimation coroutine is cancelled immediately, loading spinners and progress text disappear, and no draft or error is posted

#### Scenario: Inputs survive cancellation intact
- **WHEN** the user cancels an estimate that was started with a description and attached images
- **THEN** the description text and attached images remain present in the capture input fields, ready for immediate correction or re-submission

### Requirement: Stacked turn count and executed tool commands in collapsible section
While a meal estimate is running, the system SHALL record chronological execution events, including agent turn count and executed tool commands. The system SHALL provide a collapsible section in the loading area displaying this stacked history. The section SHALL be toggleable (expandable and collapsible) by the user during estimation, and SHALL be automatically cleared and hidden once estimation terminates on success or error.

#### Scenario: Executed steps stack chronologically
- **WHEN** an agent performs research across multiple turns with tool invocations
- **THEN** each executed command and turn count is appended to the stacked history in chronological order

#### Scenario: Collapsible section can be expanded and collapsed
- **WHEN** the user toggles the collapsible details header during active loading
- **THEN** the stacked log of turns and executed commands expands or collapses without interrupting or cancelling the estimation

#### Scenario: Stacked history clears when estimation ends
- **WHEN** the estimate finishes successfully or fails with an error
- **THEN** the collapsible progress log is dismissed and not retained on screen
