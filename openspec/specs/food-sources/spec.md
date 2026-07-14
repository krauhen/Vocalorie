# food-sources

## Purpose

TBD — capture the intent of item-level food source attribution and nutrition-prompt output rules.

## Requirements

### Requirement: Every meal item always has a source
The system SHALL require the nutrition-estimation prompt (`DEFAULT_SYSTEM_PROMPT` in `app/src/main/java/com/example/vocalorie/ai/KoogNutritionAgent.kt`) to always provide a source for each generated food item, including for simple, generic single-food meals, falling back to a general database entry for that food category when no specific product match exists.

#### Scenario: Generic single-food meal still gets a source
- **WHEN** the user logs a plain, unbranded food (e.g. "an apple")
- **THEN** the generated item includes a source URL pointing to a general database entry for that food (e.g. a generic apple entry in a food-composition database), not a blank source

### Requirement: Preference for German and listed national food-composition databases
The system SHALL instruct the model to prefer citing sources from a defined list of national/international food-composition databases, preferring German sources (the Bundeslebensmittelschlüssel, https://www.blsdb.de/) when a German-appropriate source is plausible, and falling back to other listed databases (USDA FoodData Central, McCance & Widdowson's/CoFID, Ciqual, Frida, AFCD, Swiss Food Composition Database, NEVO, Livsmedelsverket, Canadian Nutrient File, Open Food Facts, FAO/INFOODS) otherwise.

#### Scenario: German food item cites a German source when plausible
- **WHEN** the user logs a common German food item
- **THEN** the generated item's source preferentially cites the Bundeslebensmittelschlüssel (BLS) database over a non-German database, when a matching BLS-style entry is plausible

#### Scenario: Non-German food item falls back to another listed database
- **WHEN** the user logs a food item with no plausible German-database match
- **THEN** the generated item's source cites one of the other listed national/international databases rather than being left blank

### Requirement: Multi-food queries split into maximal separate items
The system SHALL instruct the model to split a query describing multiple distinct foods into the maximum reasonable number of separate items, rather than merging them into one combined item.

#### Scenario: Combined food-and-drink query splits into two items
- **WHEN** the user's query is "coffee with milk"
- **THEN** the generated result contains two separate items — one for coffee and one for milk — rather than a single combined item

### Requirement: Generated text is unconditionally German
The system SHALL instruct the model to always generate item titles and descriptions in German, regardless of the language of the user's query, replacing the prior bilingual "reply in the query's language" behavior.

#### Scenario: English query still produces German output
- **WHEN** the user's query is written in English
- **THEN** the generated item titles and descriptions are in German

### Requirement: Source is an item-level-only concept
The system SHALL represent food-item source exclusively at the item level. The meal-level `source` field SHALL be removed from `NutritionAgentResult` (`app/src/main/java/com/example/vocalorie/model/NutritionEstimateDtos.kt`), from the persisted `MealEntity` (`app/src/main/java/com/example/vocalorie/data/MealEntity.kt`, requiring Room migration `MIGRATION_4_5` to drop the column, bumping schema to version 5), and from the meal editor UI (`app/src/main/java/com/example/vocalorie/ui/components/MealEditor.kt`, removing the standalone meal-level source text field). Existing meal-level source values are discarded on migration; item-level `source` on `FoodItemEstimate` remains the sole source field and continues to be shown per item in the editor.

#### Scenario: Meal editor no longer shows a meal-level source field
- **WHEN** the user opens the meal editor for any meal
- **THEN** no standalone meal-level source input is present; each item still shows its own source

#### Scenario: Existing meals migrate without a meal-level source
- **WHEN** the app upgrades a database from schema version 4 to version 5
- **THEN** the `meals` table no longer has a `source` column, all other meal data is preserved, and any previously stored meal-level source values are gone (not migrated into any item)

#### Scenario: Item-level source remains visible and editable
- **WHEN** the user views or edits a meal with items that have source URLs
- **THEN** each item still displays and allows editing its own source, unaffected by the meal-level field's removal
