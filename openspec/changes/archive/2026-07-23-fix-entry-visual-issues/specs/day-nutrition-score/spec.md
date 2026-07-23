## MODIFIED Requirements

### Requirement: Day score shown in the heatmap and daily stats header
The system SHALL use the day nutrition score, not calories alone, to color each heatmap cell in `app/src/main/java/com/example/vocalorie/ui/entries/stats/MealStatsOverview.kt`, and SHALL additionally display the selected day's score as a number in the daily stats header block in `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt` (the block containing kJ/kcal and the "Since 00:00" dropdown), regardless of the separate All/30d/7d range selector used elsewhere on the stats screen. The score→color scale SHALL be monotonic with green as the maximum/best color: low scores render red, mid scores render yellow, and the highest/best scores render green. The scale SHALL NOT extend past green into blue or magenta.

#### Scenario: Best score renders green
- **WHEN** the heatmap renders a day with a computed score of 100
- **THEN** that cell is colored green (the top/best end of the scale), not blue or magenta

#### Scenario: A high score is green, not blue
- **WHEN** the heatmap renders a day with a computed score of 76
- **THEN** that cell is colored in the green (high) range, not blue

#### Scenario: A low score renders red
- **WHEN** the heatmap renders a day with a computed score near 0
- **THEN** that cell is colored red (the low end of the scale)

#### Scenario: Daily header shows the selected day's score as a number
- **WHEN** the user has a day selected showing kJ/kcal in the daily stats header
- **THEN** the same header also shows that day's nutrition score as a number, always for the single selected day regardless of the All/30d/7d range selector state

#### Scenario: No-data day keeps a neutral color, not a heatmap number
- **WHEN** the heatmap renders a day with zero logged meals
- **THEN** that cell renders in the existing neutral "no data" color, not the low end of the score gradient, and no score number is available for that day in the daily header
