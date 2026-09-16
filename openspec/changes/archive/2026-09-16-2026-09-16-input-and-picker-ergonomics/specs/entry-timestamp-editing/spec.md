## ADDED Requirements

### Requirement: The 24-hour time dial picker provides enlarged touch targets and clear radial separation between inner and outer hour rings
When editing an entry's time via the time picker dialog, the system SHALL present a 24-hour clock dial with enlarged container dimensions and distinct radial separation between the inner ring (hours 00/12–23) and the outer ring (hours 01–12). Touch target padding and radial distance SHALL ensure that tapping a digit on either ring reliably registers that digit without triggering the adjacent concentric digit.

#### Scenario: Tapping an inner ring hour selects the 24-hour afternoon/evening value
- **WHEN** the user opens the time picker dialog and taps the hour 14 on the inner track
- **THEN** the hour 14 is selected, and the adjacent hour 02 on the outer track is not selected

#### Scenario: Tapping an outer ring hour selects the morning value
- **WHEN** the user opens the time picker dialog and taps the hour 08 on the outer track
- **THEN** the hour 08 is selected, and the adjacent hour 20 on the inner track is not selected

#### Scenario: 24-hour dial presentation is preserved
- **WHEN** the user opens the time picker dialog
- **THEN** the picker opens directly in 24-hour dial mode without requiring an AM/PM toggle or mode switch
