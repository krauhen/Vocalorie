## ADDED Requirements

### Requirement: Scrolling content clears the floating action buttons
The system SHALL reserve enough space below the entries list's content that no row's text or values are occluded by the floating action buttons when the list is scrolled to its end. The reserved space SHALL account for the buttons' own height, the spacing between them and the screen edge, and the navigation-bar inset, and SHALL be derived from one shared definition also used to position the buttons, so the two layers cannot disagree.

The requirement covers both action buttons — the settings control at the start edge and the tab-dependent add/voice control at the end edge — and both entries tabs, whose end-edge buttons differ in size.

#### Scenario: The last meal row is fully readable
- **WHEN** the user scrolls the meal entries list to its end
- **THEN** the last row's title, calorie value and macro line are fully visible, none of them covered by either button

#### Scenario: The calorie read-out is never truncated by a button
- **WHEN** a meal row's energy line renders behind the position of the settings control
- **THEN** the full value is visible, rather than the leading digits being covered

#### Scenario: Both tabs reserve enough space
- **WHEN** the user scrolls to the end of the list on the meals tab and then on the activities tab
- **THEN** the last row is fully visible on both, the reserved space accounting for whichever button is taller

#### Scenario: Content still scrolls under the buttons in motion
- **WHEN** the user scrolls the list
- **THEN** rows may pass behind the buttons during the scroll; the requirement is that no row comes to rest occluded
