## MODIFIED Requirements

### Requirement: Every meal item always attempts a real source URL
The system SHALL require the nutrition-estimation prompt (`DEFAULT_SYSTEM_PROMPT` in `app/src/main/java/com/example/vocalorie/ai/KoogNutritionAgent.kt`) to always attempt to provide a concrete, real source URL for each generated food item, including for simple, generic single-food meals. The prompt SHALL NOT instruct a generic-database-name fallback (e.g. bare "USDA" or "German BLS" with no URL); if the model cannot confidently identify a real source URL for an item, the source SHALL be left blank rather than populated with a non-URL placeholder, since the persistence layer (`toConcreteSourceUrlOrBlank` in `app/src/main/java/com/example/vocalorie/data/MealMappers.kt`) discards any non-URL value regardless.

#### Scenario: Generic single-food meal gets a real source URL when plausible
- **WHEN** the user logs a plain, unbranded food (e.g. "an apple")
- **THEN** the generated item includes a real source URL pointing to a general database entry for that food, when the model can confidently construct one

#### Scenario: No confident URL results in a blank source, not a placeholder
- **WHEN** the model cannot confidently identify a real source URL for a generated item
- **THEN** the item's source is left blank rather than filled with a bare database name or fabricated URL

### Requirement: Preference for German and listed national food-composition databases
The system SHALL instruct the model to prefer citing sources from a defined list of national/international food-composition databases as real URLs, preferring German sources (the Bundeslebensmittelschlüssel, https://www.blsdb.de/) when a German-appropriate source URL is plausible, and falling back to other listed databases (USDA FoodData Central, McCance & Widdowson's/CoFID, Ciqual, Frida, AFCD, Swiss Food Composition Database, NEVO, Livsmedelsverket, Canadian Nutrient File, Open Food Facts, FAO/INFOODS) otherwise. If none of these can be cited as a real URL with confidence, the source SHALL be left blank.

#### Scenario: German food item cites a German source when plausible
- **WHEN** the user logs a common German food item
- **THEN** the generated item's source preferentially cites a real Bundeslebensmittelschlüssel (BLS) URL over a non-German database, when a matching BLS-style entry is plausible

#### Scenario: Non-German food item falls back to another listed database URL, or blank
- **WHEN** the user logs a food item with no plausible German-database match
- **THEN** the generated item's source cites a real URL from one of the other listed national/international databases when confidently identifiable, or is left blank if no confident real URL exists
