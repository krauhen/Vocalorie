# meal-stats-overview

## Purpose

TBD — capture the intent of the meal-logging stats overview shown on the meal entries screen.

## Requirements

### Requirement: Stats overview placement
The system SHALL display a meal-logging stats overview on the meal entries screen, below the existing daily nutrition header and above the list of individual meal entries.

#### Scenario: Stats overview visible on main view
- **WHEN** the user opens the app and the meal entries screen renders
- **THEN** the stats overview section is shown below the daily nutrition header and above the meal entry list, even when the list is empty

### Requirement: Day-range selector
The system SHALL provide a compact segmented control to select the stats time window: All, 30 days, or 7 days, defaulting to 30 days.

#### Scenario: Default range on first render
- **WHEN** the meal entries screen renders for the first time in a session
- **THEN** the day-range selector shows "30d" as selected and range-scoped tiles reflect the last 30 days

#### Scenario: Switching range updates scoped stats
- **WHEN** the user selects "7d" or "All" on the segmented control
- **THEN** the meals-logged count, active-days count, and average-daily-calories tile recompute to reflect only meals within the newly selected range

### Requirement: Meals-logged and active-days tiles
The system SHALL display the total count of meals logged and the count of distinct calendar days with at least one meal, both scoped to the selected day range.

#### Scenario: Multiple meals same day counted once for active days
- **WHEN** the selected range contains 3 meals logged on the same calendar day and 1 meal on another day
- **THEN** the meals-logged tile shows 4 and the active-days tile shows 2

#### Scenario: No meals in range
- **WHEN** the selected range contains zero logged meals
- **THEN** both tiles show 0 rather than an error or blank state

### Requirement: Streak tiles independent of range selector
The system SHALL compute current streak and longest streak from the user's full meal history, regardless of the selected day-range filter.

#### Scenario: Streak longer than selected range
- **WHEN** the user has logged at least one meal on each of the last 45 consecutive calendar days and the day-range selector is set to "7d"
- **THEN** the current-streak tile shows 45, not a value capped at 7

#### Scenario: Streak broken by a missed day
- **WHEN** the user's meal history has a calendar day with zero meals interrupting an otherwise consecutive run
- **THEN** the current streak counts only the consecutive run ending at the most recent logged day at or before today, and does not include days before the gap

#### Scenario: No meals logged at all
- **WHEN** the user has no meal entries
- **THEN** current streak and longest streak both show 0

### Requirement: Average daily calories tile
The system SHALL display the average daily calories logged within the selected day range, computed as total calories in range divided by the number of days in the range.

#### Scenario: Average reflects selected range
- **WHEN** the selected range is "7d" and the user logged a combined 800 kcal across two of those seven days
- **THEN** the average-daily-calories tile shows approximately 114 (800 / 7)

#### Scenario: No meals in range
- **WHEN** the selected range contains zero logged meals
- **THEN** the average-daily-calories tile shows 0

### Requirement: Calendar heatmap with a fixed window
The system SHALL render a calendar-style heatmap of a fixed 100-calendar-day window ending today, showing for each day a color reflecting that day's nutrition quality score (see `day-nutrition-score` capability) rather than calories alone, with a distinct neutral color for days with no logged meals. The heatmap window SHALL NOT change when the day-range selector changes, and SHALL NOT extend into future dates even though day navigation elsewhere supports future dates (see `future-entries` capability). Each heatmap cell SHALL be tappable to select that day, and the cell for the currently selected day (when within the visible window) SHALL render a visual selected-day indicator distinct from the existing out-of-range diagonal-cross overlay.

#### Scenario: Heatmap unaffected by range selector
- **WHEN** the user switches the day-range selector between "All", "30d", and "7d"
- **THEN** the heatmap continues to display the same fixed 100-day window ending today

#### Scenario: Heatmap on a history shorter than the fixed window
- **WHEN** the user's earliest logged meal is more recent than 100 days ago
- **THEN** the heatmap still spans the full fixed 100-day window, with days before the earliest logged meal shown as having no meals

#### Scenario: Cell color reflects nutrition score, not calories alone
- **WHEN** the heatmap renders a day that has logged meals
- **THEN** the cell's color is derived from that day's weighted nutrition quality score (calories, protein, carbs, fat), not from calories alone

#### Scenario: No-data day renders a distinct neutral color
- **WHEN** the heatmap renders a day with zero logged meals
- **THEN** that cell renders in a neutral "no data" color, distinct from any score-based color on the gradient

#### Scenario: Tapping a visible cell selects that day
- **WHEN** the user taps a heatmap cell within the visible fixed window
- **THEN** the app's selected day updates to that cell's date, consistent with the day navigator elsewhere on the screen

#### Scenario: Selected day shows a visual indicator
- **WHEN** the currently selected day falls within the heatmap's visible fixed window
- **THEN** that cell renders a visual selected-day indicator distinct from the diagonal-cross overlay used for out-of-range days

#### Scenario: Selection outside the visible window shows no indicator
- **WHEN** the currently selected day falls outside the heatmap's fixed 100-day window
- **THEN** no cell in the heatmap shows the selected-day indicator, and the heatmap's window does not shift or scroll to include the selected day

#### Scenario: Heatmap never shows a future date
- **WHEN** the user navigates to a future date via the day navigator
- **THEN** the heatmap's visible window is unaffected and still ends at today
