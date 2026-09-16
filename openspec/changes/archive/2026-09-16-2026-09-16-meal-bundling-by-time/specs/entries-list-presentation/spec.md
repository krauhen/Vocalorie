## ADDED Requirements

### Requirement: Consecutive meal entries within 15 minutes are grouped into a meal bundle
The system SHALL group consecutive meal entries on the same day whose time difference between adjacent entries is 15 minutes or less into a single meal bundle. Bundles SHALL chain consecutively: any entry logged within 15 minutes of the immediately preceding entry belongs to the same bundle. An entry separated by more than 15 minutes from its adjacent entries SHALL NOT be grouped with them.

#### Scenario: Two meal entries logged 8 minutes apart are bundled
- **WHEN** the user logs two meals at 12:00 and 12:08 on the same day
- **THEN** both entries appear grouped together in a single meal bundle

#### Scenario: Two meal entries logged 20 minutes apart remain separate
- **WHEN** the user logs two meals at 12:00 and 12:20 on the same day
- **THEN** each entry appears as an independent standalone row

#### Scenario: Chained meal entries across a 30-minute window form one bundle
- **WHEN** the user logs three meals at 12:00, 12:12, and 12:24 on the same day
- **THEN** all three entries form a single meal bundle because consecutive gaps are each 12 minutes (<= 15 minutes)

#### Scenario: An entry logged 16 minutes after the previous one starts a new bundle
- **WHEN** the user logs meals at 12:00, 12:10, and 12:26 on the same day
- **THEN** the first two entries (12:00 and 12:10) form one bundle, and the third entry (12:26) forms a separate entry or bundle

### Requirement: Meal bundle visual presentation with accent bracket and summary badge
The system SHALL display multi-entry meal bundles with a connected vertical accent bracket along the leading edge and a bundle summary badge above the bundled items. The summary badge SHALL display the bundle's aggregated calories, aggregated macronutrients (Protein, Carbs, Fat, Amount) using the app's semantic macro color tokens, and the bundle's item count or time span. Single-entry bundles SHALL render as standard individual rows without the accent bracket or redundant bundle header badge.

#### Scenario: Multi-entry bundle displays aggregate summary badge and accent bracket
- **WHEN** a meal bundle containing two or more entries renders in the entries list
- **THEN** a bundle header badge displays the summed calories and macronutrients of all items in the bundle, and a vertical accent bracket visually connects the cards along the leading edge

#### Scenario: Single-entry bundle renders cleanly without bracket clutter
- **WHEN** a meal has no neighboring entries within 15 minutes
- **THEN** it renders as a standard standalone meal row without a leading accent bracket and without a duplicate bundle summary badge

### Requirement: Individual entry interactions and details are preserved within bundles
Within a multi-entry meal bundle, the system SHALL render each meal as an individual card displaying its full individual title, query text, food-type category icon, individual calories, individual macros, and individual timestamp. Tapping any individual card within the bundle SHALL open that specific meal in the meal editor.

#### Scenario: Tapping an entry in a bundle opens its individual editor
- **WHEN** the user taps the second entry card inside a three-entry meal bundle
- **THEN** the meal editor opens prefilled with that specific second meal's details

#### Scenario: Individual category icons remain visible for each item in a bundle
- **WHEN** a bundle contains an entry categorized as Drink and an entry categorized as Meal
- **THEN** each item's card displays its respective Drink or Meal icon in its top-right corner
