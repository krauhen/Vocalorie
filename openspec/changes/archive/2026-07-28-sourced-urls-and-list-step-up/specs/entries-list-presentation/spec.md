## ADDED Requirements

### Requirement: Food-type icon on each meal row
The system SHALL display the meal's food-type icon in the top-right corner of each meal row in the entries list (`app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt`). The icon SHALL be tinted from the active Material theme color scheme rather than a hard-coded color, so it adapts to the user's palette and to light/dark theme.

#### Scenario: Meal row shows its category icon
- **WHEN** a meal row renders for a meal categorized as `Drink`
- **THEN** the drink icon appears in the row's top-right corner, tinted from the theme color scheme

#### Scenario: Legacy meal shows the default icon
- **WHEN** a meal row renders for a meal with category `Other`
- **THEN** the neutral default icon appears in the top-right corner

### Requirement: Semantic macro color coding in list rows
The system SHALL color the macronutrient values shown in meal list rows using fixed semantic colors — Protein blue, Carbs yellow, Fat red — while retaining the existing textual macro labels so color is never the only signal. The exact shades SHALL be defined as theme-aware tokens (`app/src/main/java/com/example/vocalorie/ui/VocalorieTheme.kt`) tuned for legibility in both light and dark themes, and the Fat color SHALL be visually distinct from the over-budget calorie-state red so the two reds are not confused.

#### Scenario: Macros are color coded in a row
- **WHEN** a meal row shows fat, carbs, and protein values
- **THEN** protein renders blue, carbs yellow, and fat red, each still accompanied by its text label

#### Scenario: Fat red distinct from over-budget red
- **WHEN** a meal row is shown in an over-budget (red-tinted) calorie state
- **THEN** the fat value's red remains visually distinguishable from the calorie-state container red

### Requirement: Row visual polish
The system SHALL refine meal and activity row presentation for scannability — clearer typographic hierarchy between title, calories, and secondary detail, consistent spacing, and calorie emphasis — without removing any information currently shown on the rows.

#### Scenario: No information is lost in the polish
- **WHEN** a meal row renders after the polish
- **THEN** it still shows title, query subtitle, calories, the macro line, and date, with a clearer visual hierarchy
