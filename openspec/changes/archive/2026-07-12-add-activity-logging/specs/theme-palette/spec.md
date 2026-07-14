## ADDED Requirements

### Requirement: Second activity color scheme in Settings
The system SHALL provide a second, independent 7-color scheme for the Activities tab, configured in a new "Activity Appearance" section in Settings that mirrors the existing "Appearance" section (the same seven roles — Primary, Secondary, Accent, Background, Surface, Surface Variant, Outline — each with a label, a color picker, and a live preview swatch). This activity scheme SHALL be persisted independently of the meal scheme, following the existing settings-store pattern, and SHALL default to a black+blue analog of the meal scheme's structure.

#### Scenario: Activity Appearance section is visible
- **WHEN** the user opens Settings
- **THEN** an "Activity Appearance" section is present with the same seven labeled color rows as the meal "Appearance" section

#### Scenario: Activity scheme defaults to black+blue
- **WHEN** the user has never customized the activity scheme
- **THEN** the Activities tab uses a black+blue default scheme, distinct from the meal scheme's defaults

#### Scenario: Activity colors persist and are independent
- **WHEN** the user changes an activity-scheme color and restarts the app
- **THEN** the activity scheme reopens with the chosen color, and the meal scheme is unaffected

### Requirement: Active tab drives a whole-screen accent swap
The system SHALL apply exactly one color scheme to the entries screen at a time, selected by the active tab: the meal scheme while the Meals tab is active and the activity scheme while the Activities tab is active. Selecting a tab SHALL re-theme the entire entries screen — header, heatmap grid, tab bar, and list — with that tab's scheme, and switching back SHALL restore the other scheme.

#### Scenario: Selecting Activities re-themes the whole screen
- **WHEN** the user selects the Activities tab
- **THEN** the entire entries screen (header, grid, tab bar, and list) is re-themed with the activity color scheme

#### Scenario: Returning to Meals restores the meal scheme
- **WHEN** the user selects the Meals tab after viewing Activities
- **THEN** the entire entries screen returns to the meal color scheme

## MODIFIED Requirements

### Requirement: App-wide theme derives entirely from the 4 colors
The system SHALL derive its full Material3 `ColorScheme` (all roles: containers, on-colors, surface variants, outline, etc.) from the currently active color scheme's base colors in `app/src/main/java/com/example/vocalorie/ui/VocalorieTheme.kt`. The set of base colors is the seven roles the codebase already uses (Primary, Secondary, Accent, Background, Surface, Surface Variant, Outline). The active scheme is selected by the current tab (meal scheme on the Meals tab, activity scheme on the Activities tab); because the theme is applied once at the app root, the active-scheme selection SHALL be hoisted so `VocalorieTheme` can be told which scheme's colors to use rather than always self-sourcing a single scheme. No UI element anywhere in the app SHALL retain a hardcoded color literal independent of the active scheme, including the gradient blend endpoints in `DayNavigator`'s header (`app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt`), which SHALL derive from the active scheme's Background color instead of literal white/black.

#### Scenario: Changing Primary retints buttons and accents app-wide
- **WHEN** the user changes the Primary color of the active scheme in Settings
- **THEN** buttons, meal-row calorie-bucket coloring, the day-navigator header gradient, and other primary-accent UI elements throughout the app visibly update to reflect the new color

#### Scenario: Changing Background retints the day-navigator gradient
- **WHEN** the user changes the Background color of the active scheme in Settings
- **THEN** the day navigator's header gradient blend (previously blending toward literal white/black) visibly shifts to blend toward the new Background color instead

#### Scenario: Derived roles remain legible
- **WHEN** the user picks an arbitrary color combination for a scheme
- **THEN** derived "on-color" text/icon roles remain readable against their corresponding container colors (no illegible low-contrast text)

#### Scenario: The active scheme follows the tab
- **WHEN** the user switches between the Meals and Activities tabs
- **THEN** the derived Material3 `ColorScheme` is rebuilt from the newly active scheme's base colors
