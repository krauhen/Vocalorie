# day-nutrition-score

## Purpose

TBD — capture the intent of the per-day nutrition quality score used to color the meal-stats heatmap and daily header.

## Requirements

### Requirement: Weighted day nutrition score
The system SHALL compute a single 0–100 nutrition quality score for any calendar day that has at least one logged meal, as a weighted average of four per-metric sub-scores derived from that day's total calories, protein, carbs, and fat (summed across all meals logged that day). Weights SHALL be calories ×10, protein ×3, carbs ×2, fat ×1 (sum of weights 16). This aggregation is implemented in `app/src/main/java/com/example/vocalorie/ui/entries/stats/MealStatsCalculator.kt`, which today only sums calories and needs new per-day protein/carbs/fat summation added alongside it.

#### Scenario: Ideal day scores near 100
- **WHEN** a day's totals are 2600 kcal, 180g protein, 0g carbs, 0g fat
- **THEN** the computed day score is 100 (every sub-metric at its peak)

#### Scenario: Day with no logged meals is not scored
- **WHEN** a calendar day has zero logged meals
- **THEN** the system does not compute a numeric score for that day and instead treats it as a distinct "no data" state

### Requirement: Per-metric normalization curves
The system SHALL normalize each metric to a 0–100 sub-score using the following shapes before applying weights:
- Calories: triangular peak of 100 at 2600 kcal, linearly decreasing to 0 at 2200 kcal and at 3000 kcal, clamped to 0 beyond either bound.
- Protein: 0 at 90g or below, linearly increasing to 100 at 180g, remaining 100 above 180g.
- Carbs: peak of 100 at 0g, linearly decreasing through 90g and 180g reference points to 0 at 270g, clamped to 0 beyond 270g.
- Fat: peak of 100 at 0g, linearly decreasing to 0 at 90g, clamped to 0 beyond 90g.

#### Scenario: Calories below the low bound clamps to zero
- **WHEN** a day's total calories is 2000 kcal (below the 2200 kcal low bound)
- **THEN** the calories sub-score is 0, not a negative or extrapolated value

#### Scenario: Protein above the plateau stays at maximum
- **WHEN** a day's total protein is 220g (above the 180g plateau point)
- **THEN** the protein sub-score is 100, not reduced for exceeding 180g

#### Scenario: Carbs beyond the too-much bound clamps to zero
- **WHEN** a day's total carbs is 320g (above the 270g bound)
- **THEN** the carbs sub-score is 0

### Requirement: Day score shown in the heatmap and daily stats header
The system SHALL use the day nutrition score, not calories alone, to color each heatmap cell in `app/src/main/java/com/example/vocalorie/ui/entries/stats/MealStatsOverview.kt` (replacing the existing `caloriesToColor` function), and SHALL additionally display the selected day's score as a number in the daily stats header block in `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt` (the block containing kJ/kcal and the "Since 00:00" dropdown), regardless of the separate All/30d/7d range selector used elsewhere on the stats screen.

#### Scenario: Heatmap cell color reflects the day score
- **WHEN** the heatmap renders a day with a computed score of 90
- **THEN** that cell is colored according to the high end of the score-based color scale, not a calorie-only gradient

#### Scenario: Daily header shows the selected day's score as a number
- **WHEN** the user has a day selected showing kJ/kcal in the daily stats header
- **THEN** the same header also shows that day's nutrition score as a number, always for the single selected day regardless of the All/30d/7d range selector state

#### Scenario: No-data day keeps a neutral color, not a heatmap number
- **WHEN** the heatmap renders a day with zero logged meals
- **THEN** that cell renders in the existing neutral "no data" color, not the low end of the score gradient, and no score number is available for that day in the daily header
