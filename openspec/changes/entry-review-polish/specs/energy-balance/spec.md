## MODIFIED Requirements

### Requirement: Daily balance (deficit/surplus) shown in stats header
The system SHALL show, in the daily stats header, a balance figure computed as `consumed − base burned − sum of activities`. A negative value SHALL read as a deficit and a positive value SHALL read as a surplus; the figure SHALL be signed and color-coded to distinguish deficit from surplus, with a deficit (negative balance) shown in a positive/favorable color and a surplus (positive balance) shown in the error/alert color, since a calorie deficit is the outcome most users are working toward.

#### Scenario: Deficit when eating under burn
- **WHEN** the user consumed 2000 kcal, base burn is 2400, and there are no activities
- **THEN** the balance shows −400 (a deficit), rendered in the positive/favorable color

#### Scenario: Surplus when eating over burn
- **WHEN** the user consumed 2800 kcal, base burn is 2400, and there are no activities
- **THEN** the balance shows +400 (a surplus), rendered in the error/alert color

#### Scenario: Activities deepen the deficit
- **WHEN** the user consumed 2400 kcal, base burn is 2400, and activities burned 300 kcal
- **THEN** the balance shows −300 (a deficit), rendered in the positive/favorable color
