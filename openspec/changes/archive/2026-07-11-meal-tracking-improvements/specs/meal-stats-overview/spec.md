## MODIFIED Requirements

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
