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

### Requirement: Chart axis labels render inside their card
The system SHALL render every axis label of the calorie-over-time chart fully within its enclosing card, with no label clipped by the card's edge, and SHALL keep each y-axis label aligned with the gridline it names.

#### Scenario: The zero label is fully legible
- **WHEN** the "Calories over time" chart renders as the last element of the daily stats card
- **THEN** the bottom y-axis label `0` is shown in full, not vertically cut by the card edge

#### Scenario: Labels still name their gridlines
- **WHEN** the chart renders with a maximum, midpoint and zero tick
- **THEN** each label sits beside the gridline whose value it states

### Requirement: Heatmap range labels use one pattern and an explicit locale
The system SHALL render the heatmap's two range labels — the start of the window and today — with the same date pattern, so the two ends of one row cannot read differently. The pattern SHALL be numeric, avoiding month names, so no locale's abbreviation data can make one end carry a trailing period the other lacks, and it SHALL follow the numeric day-month-year convention already used by the day navigator.

Every user-facing date and time formatter on the entries screen SHALL name its locale explicitly rather than relying on the ambient default, and the label formatting SHALL be pure, JVM-testable logic so a locale regression is caught by a unit test rather than on a device.

#### Scenario: Both range labels read the same way
- **WHEN** the heatmap's window starts on 18 May and ends on 20 August
- **THEN** both labels use the same numeric pattern, and neither carries a trailing abbreviation period the other lacks

#### Scenario: No month abbreviation appears
- **WHEN** the range labels render in any device locale
- **THEN** neither label contains an abbreviated month name

#### Scenario: The label format is locale-explicit and testable
- **WHEN** the range label is produced for a given date and locale
- **THEN** the result is derived by pure logic taking that locale as an input, without reading an ambient default


### Requirement: Macro values in the day/window stats header use semantic color coding
The system SHALL apply the same semantic macro color coding — Protein blue, Carbs yellow, Fat red — to the macronutrient values shown in the selectable day/window stats header (the "Since 00:00"-style macro line in `SelectableStatsHeader`, `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt`), reusing the shared theme-aware macro color tokens so this header and the list rows stay visually consistent. The tile/heatmap stats overview shows no per-macro gram values and is out of scope. Textual labels for macros SHALL be retained so color is not the only signal.

#### Scenario: Header macros match list-row colors
- **WHEN** the day/window stats header shows the Protein/Carbs/Fat macro line
- **THEN** protein renders blue, carbs yellow, and fat red, matching the colors used in the list rows and keeping their text labels

### Requirement: Stats and heatmap values are computed once per input change
The system SHALL compute the statistics aggregate and the per-day heatmap scores at most once per change to their inputs — the meal history, the selected range, and the reference instant — rather than once per render. The reference instant SHALL be an explicit input rather than being read inside the computation, so the result is cacheable. Selecting a day, whether through the day navigator or by tapping a heatmap cell, SHALL NOT by itself recompute the aggregate or the per-day scores.

#### Scenario: Selecting a day does not recompute the aggregate
- **WHEN** the user selects a different day and no entry has been added, edited or deleted
- **THEN** the statistics aggregate and the per-day heatmap scores are reused rather than recomputed

#### Scenario: Changing the range recomputes once
- **WHEN** the user switches the day-range selector
- **THEN** the range-scoped tiles recompute exactly once for that change

#### Scenario: Adding an entry recomputes once
- **WHEN** the user saves a new meal
- **THEN** the aggregate and the affected heatmap day recompute once, and the displayed values reflect the new meal

### Requirement: Persisted meal totals are authoritative and consistent with their items
The system SHALL persist each meal's nutrition totals and MAY read those persisted totals when computing statistics and heatmap values instead of re-deriving them from the meal's stored item list. Persisted totals SHALL equal the totals derived from that meal's items at the time it was saved, and the system SHALL maintain that equality on every write.

#### Scenario: Persisted totals match item-derived totals
- **WHEN** a reviewed meal is saved
- **THEN** its persisted totals equal the sum of its items' values

#### Scenario: Statistics computed from persisted totals match item-derived statistics
- **WHEN** the statistics aggregate is computed for a set of meals
- **THEN** the values shown are the same as those obtained by summing each meal's items

#### Scenario: Editing a meal keeps its totals in step
- **WHEN** the user edits a meal's items and saves
- **THEN** the persisted totals are updated to match the edited items

