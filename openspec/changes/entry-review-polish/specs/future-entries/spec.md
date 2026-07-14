## MODIFIED Requirements

### Requirement: Future-dated entries are visually highlighted
The system SHALL visually distinguish any meal or activity entry row whose timestamp is in the future (relative to the current instant) with a dashed-border treatment plus a diagonal hatch-stripe fill, both rendered in that row's own calorie-magnitude bucket color, layered on top of the entry's existing calorie-bucket styling rather than replacing it. This applies to both `MealEntryRow` and `ActivityEntryRow` in `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt`.

#### Scenario: Future meal entry shows a dashed border and hatch fill in its bucket color
- **WHEN** a meal entry's timestamp is later than the current instant
- **THEN** its row renders with a dashed border and a diagonal hatch-stripe fill, both in its calorie-bucket color, in addition to its normal calorie-bucket background/border color

#### Scenario: Future activity entry shows the same treatment
- **WHEN** an activity entry's timestamp is later than the current instant
- **THEN** its row renders with the same dashed-border-plus-hatch-fill treatment, in its own calorie-bucket color, matching the treatment already applied to future meal entries

#### Scenario: Past or present entries are unaffected
- **WHEN** a meal or activity entry's timestamp is at or before the current instant
- **THEN** its row renders with its normal calorie-bucket styling only, no dashed border or hatch fill
