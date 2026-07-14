## ADDED Requirements

### Requirement: Four user-defined theme colors in Settings
The system SHALL provide a new "Appearance" section in `app/src/main/java/com/example/vocalorie/ui/settings/SettingsScreen.kt` where the user can define exactly 4 colors — internally named Primary, Secondary, Background, and Accent — each with a user-facing label, a color picker control, and a live preview swatch, following the existing section pattern (e.g. the "Agent tools" section's label/control/action layout).

#### Scenario: Appearance section is visible in Settings
- **WHEN** the user opens Settings
- **THEN** an "Appearance" section is present with 4 labeled color rows: Primary, Secondary, Background, Accent

#### Scenario: Picking a color updates its preview
- **WHEN** the user selects a new color for any of the 4 rows via its color picker
- **THEN** that row's preview swatch immediately reflects the newly picked color

### Requirement: Chosen colors persist across app restarts
The system SHALL persist the 4 chosen colors so they survive an app restart, using a dedicated settings store following the existing pattern in `app/src/main/java/com/example/vocalorie/settings/`.

#### Scenario: Colors survive app restart
- **WHEN** the user sets custom Primary/Secondary/Background/Accent colors and fully restarts the app
- **THEN** the app reopens using the previously chosen colors, not the defaults

### Requirement: App-wide theme derives entirely from the 4 colors
The system SHALL derive its full Material3 `ColorScheme` (all roles: containers, on-colors, surface variants, outline, etc.) from the 4 user-chosen colors in `app/src/main/java/com/example/vocalorie/ui/VocalorieTheme.kt`, replacing the current hardcoded light/dark color schemes. No UI element anywhere in the app SHALL retain a hardcoded color literal independent of these 4 colors, including the gradient blend endpoints in `DayNavigator`'s header (`app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt`), which SHALL derive from the Background color instead of literal white/black.

#### Scenario: Changing Primary retints buttons and accents app-wide
- **WHEN** the user changes the Primary color in Settings
- **THEN** buttons, meal-row calorie-bucket coloring, the day-navigator header gradient, and other primary-accent UI elements throughout the app visibly update to reflect the new color

#### Scenario: Changing Background retints the day-navigator gradient
- **WHEN** the user changes the Background color in Settings
- **THEN** the day navigator's header gradient blend (previously blending toward literal white/black) visibly shifts to blend toward the new Background color instead

#### Scenario: Derived roles remain legible
- **WHEN** the user picks an arbitrary Primary/Secondary/Background/Accent combination
- **THEN** derived "on-color" text/icon roles remain readable against their corresponding container colors (no illegible low-contrast text)
