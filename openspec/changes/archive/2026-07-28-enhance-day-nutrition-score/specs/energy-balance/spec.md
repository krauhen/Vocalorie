## REMOVED Requirements

### Requirement: Score and heatmap unaffected by activities
**Reason**: The daily nutrition score is moving to a goal-adherence model in which logged activity legitimately expands the day's calorie allowance; the blanket "activities never touch the score" rule directly conflicts with that intent.
**Migration**: Replaced by the "Activities raise the day's calorie target" requirement below and the "Activity-adjusted calorie target" requirement in the `day-nutrition-score` capability. The balance/burned header figures (`consumed − base burn − activities`) are unaffected by this change.

## ADDED Requirements

### Requirement: Activities raise the day's calorie target
The system SHALL let a day's logged activities raise the calorie target used by the day nutrition score, by adding **50% of the day's total activity calories burned** to the user's calorie goal, as specified by the `day-nutrition-score` capability. The heatmap and daily-header score SHALL reflect this activity-adjusted target. This affects only the score's calorie target; the burned-calories and balance figures shown in the stats header (which use the base burn plus the full activity total) SHALL remain unchanged.

#### Scenario: Active day expands the scoring allowance
- **WHEN** a day has activities burning 600 kcal and the calorie goal is 2400
- **THEN** the day nutrition score treats 2700 kcal (2400 + 0.5 × 600) as the on-target intake, so eating more on an active day is not penalized

#### Scenario: Header figures still use the full activity total
- **WHEN** a day has activities burning 600 kcal, base burn 2400, consumed 2700
- **THEN** the header burned figure (3000) and balance figure (−300) are computed from the full activity total and base burn, unchanged by the score's 50% add-back
