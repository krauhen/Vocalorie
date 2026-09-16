## 1. 24-hour clock dial enlargement & misclick prevention (entry-timestamp-editing UI)

- [ ] 1.1 Spike: Inspect `TimePicker` rendering and sizing in `app/src/main/java/com/example/vocalorie/ui/components/CommonUi.kt:390-406` on target display densities (e.g. 1080x2340 / 420dpi on SM-S911B). Validate container dimensions, layout scale modifiers, or custom clock dial wrapper layout that increases the diameter and radial spacing between inner (00/12–23) and outer (01–12) rings.
- [ ] 1.2 In `app/src/main/java/com/example/vocalorie/ui/components/CommonUi.kt:390-406`, implement the enlarged dial container in `TimePickerDialog`. Ensure the dialog container width and height accommodate the expanded dial (e.g. expanding dial width/height to >= 288-300dp or scaling dial content) while keeping confirm ("OK") and dismiss ("Cancel") buttons clearly visible and untruncated.
- [ ] 1.3 Verify existing timestamp tests remain green: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 2. Window configuration & Activity soft input mode (ui-responsiveness windowing)

- [ ] 2.1 In `app/src/main/AndroidManifest.xml:22-29`, add `android:windowSoftInputMode="adjustResize"` to `.MainActivity`.
- [ ] 2.2 In `app/src/main/java/com/example/vocalorie/MainActivity.kt:20-25`, confirm the activity root decor and content setup cleanly propagate window resize insets without clipping top or bottom system bars.
- [ ] 2.3 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 3. Modal bottom sheet & overlays IME padding (ui-responsiveness UI)

- [ ] 3.1 In `app/src/main/java/com/example/vocalorie/ui/voice/VoiceInputOverlay.kt:180-186`, add `Modifier.imePadding()` to `VoiceSheetContent`'s scrollable `Column`. Ensure that when focusing the meal description (`VoiceInputOverlay.kt:193-200`) or search input (`VoiceInputOverlay.kt:280-300`), the scroll container shrinks and focused fields remain above the keyboard.
- [ ] 3.2 In `app/src/main/java/com/example/vocalorie/ui/entries/MealEntryOverlay.kt:57-74`, configure `AlertDialog` with `properties = DialogProperties(decorFitsSystemWindows = false)` and apply `Modifier.imePadding()` to the inner scrollable `Column` (`MealEntryOverlay.kt:70-74`), ensuring all fields in `EditableMealEditor` remain accessible.
- [ ] 3.3 In `app/src/main/java/com/example/vocalorie/ui/entries/ActivityEntryOverlay.kt:64-83`, configure `AlertDialog` with `properties = DialogProperties(decorFitsSystemWindows = false)` and apply `Modifier.imePadding()` to the inner scrollable `Column` (`ActivityEntryOverlay.kt:79-83`), ensuring all fields in `EditableActivityEditor` remain accessible.
- [ ] 3.4 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 4. Settings screen IME padding & auto-scroll (ui-responsiveness UI)

- [ ] 4.1 In `app/src/main/java/com/example/vocalorie/ui/settings/SettingsScreen.kt:168-171`, add `Modifier.imePadding()` to the scrollable `Column` in `SettingsContent`.
- [ ] 4.2 Verify that lower input fields in `SettingsScreen.kt` (such as API keys around lines 350–420 and prompt overrides around lines 500–550) smoothly scroll into view above the keyboard when focused.
- [ ] 4.3 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 5. Specification validation and on-device confirmation

- [ ] 5.1 Validate change files with OpenSpec CLI: `openspec validate 2026-09-16-input-and-picker-ergonomics --strict`.
- [ ] 5.2 Verify full test suite: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`.
- [ ] 5.3 On-device: Pull database before installation per `docs/agent/guidance/setup.md`.
- [ ] 5.4 Install debug build to connected device (`SM-S911B`): `./gradlew :app:installDebug --no-daemon`.
- [ ] 5.5 On-device: Open an existing meal entry, tap the time picker clock icon, and verify:
  - Inner ring hours (e.g. 14:00, 18:00, 22:00) can be tapped cleanly without triggering the adjacent outer ring hours (02:00, 06:00, 10:00).
  - Outer ring hours (e.g. 02:00, 08:00) can be tapped cleanly without triggering the inner ring hours.
- [ ] 5.6 On-device: Test software keyboard visibility across all screens:
  - Open `VoiceInputOverlay`: tap "Meal description" and "Search past meals"; verify keyboard does not occlude text and user can scroll while keyboard is shown.
  - Open `MealEntryOverlay`: edit bottom items, food amount, and timestamp; verify active fields scroll above the keyboard.
  - Open `ActivityEntryOverlay`: edit notes, duration, and timestamp; verify active fields scroll above the keyboard.
  - Open `SettingsScreen`: tap "System prompt override" and API key fields at the bottom; verify fields scroll into view above the keyboard.
  - Dismiss keyboard on each screen and confirm smooth transition with no stuck insets or blank gaps.
- [ ] 5.7 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`
