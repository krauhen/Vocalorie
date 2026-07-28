## ADDED Requirements

### Requirement: Future/past classification re-evaluates on refresh
The system SHALL classify each entry as future or past against a current instant that advances when the entries screen is refreshed, not only when a write reloads the list. The entries screen (`app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt`) SHALL provide a pull-to-refresh (swipe-down) gesture that reloads entries from the database AND recomputes the current instant used for future/past classification, replacing the previous behavior where the instant was memoized against the loaded meal list (`remember(meals) { Instant.now() }`) and therefore only changed on a write.

#### Scenario: Passed-time entry un-crosses after pull-to-refresh
- **WHEN** an entry is currently rendered with the future (dashed-border + hatch) treatment because its timestamp is later than the last-computed instant, its timestamp has since become earlier than the real current time, and the user performs a pull-to-refresh
- **THEN** the entry is reloaded and reclassified as past, rendering with its normal calorie-bucket styling only and no dashed/hatch treatment

#### Scenario: Still-future entry stays crossed after pull-to-refresh
- **WHEN** an entry's timestamp is still later than the current time and the user performs a pull-to-refresh
- **THEN** the entry continues to render with the dashed-border + hatch future treatment

#### Scenario: Refresh does not require a write
- **WHEN** the user performs a pull-to-refresh without adding or editing any entry
- **THEN** the current instant is recomputed and future/past classification updates accordingly, independent of any save action
