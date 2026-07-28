## ADDED Requirements

### Requirement: LLM classifies each meal into a fixed food-type category
The system SHALL have the nutrition-estimation model (`app/src/main/java/com/example/vocalorie/ai/KoogNutritionAgent.kt`) classify the overall meal into exactly one of a fixed, closed set of food-type categories — **Meal**, **Snack**, **Drink**, **Dessert**, or **Other** — and return it as a structured field on the nutrition result (`NutritionAgentResult` in `app/src/main/java/com/example/vocalorie/model/NutritionEstimateDtos.kt`). The model SHALL choose exactly one category; when none of Meal/Snack/Drink/Dessert clearly applies it SHALL choose **Other**.

#### Scenario: Drink query classified as Drink
- **WHEN** the user logs "a cappuccino"
- **THEN** the result's category field is `Drink`

#### Scenario: Ambiguous item falls back to Other
- **WHEN** the model cannot confidently map the meal to Meal, Snack, Drink, or Dessert
- **THEN** the result's category field is `Other`

### Requirement: Category persists across saves and migration
The system SHALL persist the chosen category with the saved meal (`app/src/main/java/com/example/vocalorie/data/MealEntity.kt`, mapped in `app/src/main/java/com/example/vocalorie/data/MealMappers.kt`) via an additive Room migration in `app/src/main/java/com/example/vocalorie/data/VocalorieDatabase.java`, with no destructive fallback. Existing meals saved before this change SHALL be treated as category **Other**.

#### Scenario: New meal persists its category
- **WHEN** a meal classified as `Snack` is saved and reloaded
- **THEN** the reloaded meal reports category `Snack`

#### Scenario: Legacy meal defaults to Other
- **WHEN** a meal saved before this migration is loaded
- **THEN** it reports category `Other` rather than failing to load

### Requirement: Category maps to a single icon
The system SHALL map each category to exactly one icon used for display, with **Other** mapped to a neutral default icon. The mapping SHALL be total over the closed category set so every meal always resolves to an icon.

#### Scenario: Every category resolves to an icon
- **WHEN** a meal has any of the five categories
- **THEN** a corresponding icon is resolved, and `Other` resolves to the neutral default icon
