## MODIFIED Requirements

### Requirement: Tips shown only for today, only with logged meals
The system SHALL show the tips section only when the selected day is the current day, that day has at least one logged meal, and that day's nutrition score is **below 50**. On a day with no logged meals — for which no score exists either — the section SHALL be absent rather than empty, and on any past or future selected day the section SHALL be absent. On a day scoring 50 or above the tip list SHALL be empty, so the section is absent there too.

The score the gate reads SHALL be the same day nutrition score shown beside the tips, derived from the same totals, goals and activity burn by the `day-nutrition-score` capability, so the gate can never disagree with the number the user sees. The threshold SHALL be evaluated against the day's score as it currently stands, not against a projected end-of-day total; tips MAY therefore appear and disappear as the day is logged.

#### Scenario: Day with no logged meals hides the section
- **WHEN** today has no logged meals
- **THEN** the tips section is not shown at all, matching the score number's absence

#### Scenario: A past day shows no tips
- **WHEN** the user selects a previous day that has logged meals and a computed score
- **THEN** the score number is shown but the tips section is not

#### Scenario: A day scoring above the threshold shows no tips
- **WHEN** today scores 72 with its carbs outside the on-target band
- **THEN** the tip list is empty and the tips section is not shown, even though a carbs shortfall exists

#### Scenario: A day scoring below the threshold shows tips
- **WHEN** today scores 11 with its carbs well under target
- **THEN** the carbs tip is shown

#### Scenario: The threshold boundary excludes 50
- **WHEN** today's score is exactly 50
- **THEN** no tips are shown, and at 49 the ranked tips are shown

#### Scenario: Logging a meal past the threshold retires the tips
- **WHEN** today scores 42 with tips shown and the user logs a meal that raises the score to 63
- **THEN** the tips section disappears without any other user action

### Requirement: Rotating presentation with tap to expand
The system SHALL show at most one tip at a time in the daily nutrition section, rotating through the ranked tips with a crossfade at the configured interval. The ranked list itself SHALL contain at most the **3** highest-ranked tips, the cap being a property of the derived list rather than of the renderer, so every consumer of the list sees the same tips. Tapping the tip SHALL stop rotation and expand the full ranked list; collapsing SHALL resume rotation. Rotation SHALL not run when fewer than two tips exist. The section SHALL remain compact enough to sit beneath the score without displacing the existing header content (kcal total, burned, balance, macro line, histogram).

#### Scenario: Only the three costliest tips are derived
- **WHEN** six shortfalls on the day would each produce a tip
- **THEN** the derived list holds exactly the three with the highest `weight × (100 − subScore)` rank, and the other three appear nowhere

#### Scenario: Expanding shows the same three, not more
- **WHEN** the user taps the displayed tip on a day with three derived tips
- **THEN** all three are listed and no fourth tip appears on expansion

#### Scenario: Rotation cycles the derived list
- **WHEN** three tips are ranked and the rotation interval is 5 seconds
- **THEN** the section displays one tip at a time, crossfading between the three every 5 seconds

#### Scenario: Tap expands the full list and freezes rotation
- **WHEN** the user taps the displayed tip
- **THEN** all ranked tips are listed and rotation stops until the user collapses the list again

#### Scenario: A single tip does not rotate
- **WHEN** exactly one tip is ranked
- **THEN** that tip is shown statically with no crossfade
