## ADDED Requirements

### Requirement: Input fields remain visible and interactive above the software keyboard across all screens and overlays
When a text input field in any screen, dialog, or bottom sheet gains focus and the software keyboard (IME) opens, the system SHALL contract the scrollable viewport to accommodate the keyboard insets and SHALL automatically scroll the focused input field into view above the keyboard. No active input cursor or typed text SHALL be obscured beneath the keyboard, and the user SHALL be able to scroll through the full content of the container while the keyboard remains visible.

#### Scenario: Voice overlay fields remain visible above the software keyboard
- **WHEN** the user focuses the meal description or past meal search field in the voice input overlay
- **THEN** the bottom sheet viewport adjusts for the keyboard insets, the active text field is fully visible above the keyboard, and the user can type and scroll without keyboard occlusion

#### Scenario: Meal editor fields in dialog overlay scroll into view above the software keyboard
- **WHEN** the user edits a meal entry in the overlay dialog and taps a text field located near the bottom of the form
- **THEN** the dialog content adjusts for the keyboard insets and auto-scrolls the active field into view above the software keyboard

#### Scenario: Activity editor fields in dialog overlay scroll into view above the software keyboard
- **WHEN** the user adds or edits an activity entry in the overlay dialog and focuses an input field
- **THEN** the dialog content accommodates the keyboard insets and keeps the focused field clearly visible above the keyboard

#### Scenario: Settings screen inputs remain accessible above the software keyboard
- **WHEN** the user focuses an input field located near the bottom of the settings screen (such as API keys or system prompt override)
- **THEN** the settings scroll container adjusts for the keyboard insets and scrolls the active field above the keyboard

#### Scenario: Dismissing the keyboard restores the viewport cleanly
- **WHEN** the software keyboard is closed after typing in an input field
- **THEN** the containing container smoothly expands to occupy its standard bounds without layout jitter, clipped content, or persistent extra padding
