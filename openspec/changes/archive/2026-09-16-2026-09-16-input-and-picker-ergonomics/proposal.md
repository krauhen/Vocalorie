## Why

Editing timestamps and entering meal data currently involve two ergonomic friction points on mobile:

1. **24-hour dial picker misclicks (B2)**: When adjusting an entry's time in `EntryTimestampField` (`ui/components/CommonUi.kt:390-406`), the Material 3 24-hour clock dial dialog places hours 00/12–23 on an inner concentric circle and hours 01–12 on an outer circle within the standard 256dp diameter bounds. The user explicitly prefers the 24-hour clock dial modality over typing or 12-hour AM/PM switching. However, the compact radial distance and cramped touch targets between the inner and outer concentric tracks cause frequent misclicks (e.g. attempting to tap 14:00 on the inner ring inadvertently selects 02:00 on the outer ring, or tapping 20:00 selects 08:00). Correcting this requires repetitive retaps.

2. **Software keyboard (IME) occluding input fields (F5)**: Across all forms and overlays in the app, activating the software keyboard hides the active input field or leaves insufficient room to see what is being typed. Specifically:
   - In `VoiceInputOverlay` (`ui/voice/VoiceInputOverlay.kt:180-200`, `280-300`), focusing the "Meal description" field or the "Search past meals" field causes the software keyboard to cover the bottom portion of the bottom sheet, preventing the user from viewing search results or draft summaries while typing.
   - In `MealEntryOverlay` (`ui/entries/MealEntryOverlay.kt:70-85`) and `ActivityEntryOverlay` (`ui/entries/ActivityEntryOverlay.kt:79-95`), editing fields near the bottom of the dialog (food items, macro breakdown, step counts, notes, or the timestamp field) is occluded by the IME. The user cannot see the active cursor or newly typed characters without dismissing the keyboard or blindly submitting.
   - In `SettingsScreen` (`ui/settings/SettingsScreen.kt:168-200`), fields toward the bottom (API keys, tool call iteration limits, system prompt override) get trapped beneath the IME.

The root causes are twofold: `AndroidManifest.xml` lacks `android:windowSoftInputMode="adjustResize"` on `MainActivity`, and the scrollable content containers across modal sheets, dialogs, and screens lack `WindowInsets.ime` / `Modifier.imePadding()` and focus auto-scrolling accommodation.

Resolving both issues makes data entry fluid, precise, and error-free without altering the underlying data models.

## What Changes

- **Enlarge 24-hour clock dial and radial spacing (B2)**: Retain the preferred 24-hour clock dial widget in `TimePickerDialog` (`ui/components/CommonUi.kt:390-406`), but enlarge the dial container diameter and increase the radial distance and touch target padding between the inner ring (00/12–23) and outer ring (01–12) so taps on inner and outer hour tracks are distinctly separated and misclicks are eliminated.
- **Set Activity soft input mode to `adjustResize` (F5)**: Add `android:windowSoftInputMode="adjustResize"` to `.MainActivity` in `app/src/main/AndroidManifest.xml:23` so the window viewport automatically contracts when the IME opens.
- **Apply IME padding to `VoiceInputOverlay` (F5)**: Add `Modifier.imePadding()` to `VoiceSheetContent`'s scrollable container (`ui/voice/VoiceInputOverlay.kt:180-186`) so the bottom sheet viewport compresses above the keyboard and focused fields auto-scroll into view.
- **Apply IME accommodation to `MealEntryOverlay` and `ActivityEntryOverlay` (F5)**: Configure `AlertDialog` instances in `MealEntryOverlay.kt:57-74` and `ActivityEntryOverlay.kt:64-83` with system windows insets handling and `Modifier.imePadding()` on their inner scrollable columns so editing forms resize and scroll active fields into view above the keyboard.
- **Apply IME padding to `SettingsScreen` (F5)**: Add `Modifier.imePadding()` to the scrollable container in `SettingsContent` (`ui/settings/SettingsScreen.kt:168-171`) to keep all settings fields visible and scrollable above the software keyboard.

## Capabilities

### Modified Capabilities

- `entry-timestamp-editing`: Add requirement and scenarios for enlarged 24-hour clock dial touch targets and increased radial separation between concentric hour rings to prevent misclicks.
- `ui-responsiveness`: Add requirement and scenarios ensuring all text input fields across screens, dialogs, and bottom sheets remain visible and interactive above the software keyboard.

## Impact

- **Manifest**: `app/src/main/AndroidManifest.xml` adds `android:windowSoftInputMode="adjustResize"`.
- **UI Components**: `ui/components/CommonUi.kt` updates `TimePicker` container and dial sizing in `EntryTimestampField`.
- **Overlays & Screens**: `ui/voice/VoiceInputOverlay.kt`, `ui/entries/MealEntryOverlay.kt`, `ui/entries/ActivityEntryOverlay.kt`, and `ui/settings/SettingsScreen.kt` gain `Modifier.imePadding()` and IME scroll accommodation.
- **Tests**: Pure-JVM unit tests for timestamp parsing and merging remain green; verification includes unit tests and on-device manual validation on Samsung Galaxy S23.
- **No data/schema impact**: Zero database migrations, zero entity changes, and no change to backup serialization.

## Non-goals

- **No replacement of the 24-hour clock dial with a 12-hour AM/PM dial**: The 24-hour dial is the user's explicit preference.
- **No replacement of the clock dial with text-only numeric input**: The visual dial interaction is preferred by the user over typing time digits.
- **No new external insets or window-management libraries**: Native Jetpack Compose foundation and Android platform insets provide all necessary IME support.
- **No alteration of date picking or calendar selection**: Calendar date selection in `DatePickerDialog` is unaffected; this change touches only the time dial and IME visibility.
- **No changes to hardware keyboard handling or physical orientation rules**: The scope is strictly software keyboard (IME) visibility and vertical viewport responsiveness.
- **No database or storage changes**: This change touches strictly UI ergonomics and presentation layout.
