## ADDED Requirements

### Requirement: Configurable daily calorie goal
The system SHALL provide a single global "daily calorie goal" setting (kcal), editable in Settings via a numeric field following the existing numeric-setting pattern, defaulting to **2400 kcal** and persisted across app restarts in `settings/ThemeSettingsStore.kt`. This goal expresses the user's intended daily energy intake and is the basis for the day nutrition score's targets. It is distinct from the existing "base calories burned" setting.

#### Scenario: Default calorie goal
- **WHEN** the user has never set a calorie goal
- **THEN** the system uses 2400 kcal as the daily calorie goal

#### Scenario: Editing the calorie goal persists
- **WHEN** the user sets the calorie goal to a new value and restarts the app
- **THEN** the new value is used for all days' score targets

### Requirement: Configurable macronutrient split
The system SHALL provide a global macronutrient split expressed as three percentages of energy — protein, carbohydrate, fat — that SHALL sum to 100, editable in Settings, defaulting to **30% protein / 40% carbohydrate / 30% fat**, and persisted across app restarts. The system SHALL prevent saving a split whose three percentages do not sum to 100 (e.g. by deriving the third value from the other two).

#### Scenario: Default macro split
- **WHEN** the user has never set a macro split
- **THEN** the system uses 30% protein, 40% carbohydrate, 30% fat

#### Scenario: Split must sum to 100
- **WHEN** the user edits the macro split
- **THEN** the persisted percentages always sum to 100 (the UI does not allow an inconsistent split to be saved)

### Requirement: Derived per-macro gram targets
The system SHALL derive daily gram targets from the calorie goal and macro split using Atwater factors (protein and carbohydrate 4 kcal/g, fat 9 kcal/g):
- `proteinTarget = calorieGoal × pProtein/100 / 4`
- `carbsTarget = calorieGoal × pCarbs/100 / 4`
- `fatTarget = calorieGoal × pFat/100 / 9`

These derived targets are the reference points the day nutrition score's macro sub-scores are measured against.

#### Scenario: Default targets at the default goal
- **WHEN** the calorie goal is 2400 kcal with a 30/40/30 split
- **THEN** the derived targets are 180g protein, 240g carbohydrate, and 80g fat

#### Scenario: Targets track the calorie goal
- **WHEN** the calorie goal changes to 3000 kcal with the same 30/40/30 split
- **THEN** the derived targets scale proportionally (225g protein, 300g carbohydrate, 100g fat)
