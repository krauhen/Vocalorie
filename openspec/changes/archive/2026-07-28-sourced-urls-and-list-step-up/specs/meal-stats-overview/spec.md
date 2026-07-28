## ADDED Requirements

### Requirement: Macro values in the day/window stats header use semantic color coding
The system SHALL apply the same semantic macro color coding — Protein blue, Carbs yellow, Fat red — to the macronutrient values shown in the selectable day/window stats header (the "Since 00:00"-style macro line in `SelectableStatsHeader`, `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt`), reusing the shared theme-aware macro color tokens so this header and the list rows stay visually consistent. The tile/heatmap stats overview shows no per-macro gram values and is out of scope. Textual labels for macros SHALL be retained so color is not the only signal.

#### Scenario: Header macros match list-row colors
- **WHEN** the day/window stats header shows the Protein/Carbs/Fat macro line
- **THEN** protein renders blue, carbs yellow, and fat red, matching the colors used in the list rows and keeping their text labels
