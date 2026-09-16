## Context

The app recently added Material 3 date and time pickers to `EntryTimestampField` (`ui/components/CommonUi.kt:290-406`) in change `2026-08-20-date-time-picker-in-entry-editors`. While this eliminated manual timestamp typing for routine corrections, two ergonomic issues degrade mobile interaction:

1. **24-hour time dial picker crowding**: In `CommonUi.kt:390-406`, the time dialog displays a Material 3 `TimePicker` with `is24Hour = true`. Material 3's default dial diameter is 256dp. In 24-hour mode, hours 01–12 sit on an outer track (~210dp diameter) and hours 00/12–23 sit on an inner track (~140dp diameter). The resulting radial separation is only ~35dp, which is significantly smaller than the standard 48dp touch target guideline. When using one-handed thumb interaction, users frequently hit the wrong concentric ring (e.g. tapping 14:00 registers as 02:00, or tapping 21:00 registers as 09:00). The user explicitly prefers the 24-hour dial modality over 12-hour AM/PM or text inputs, requiring dial geometry adjustments.

2. **Software keyboard (IME) occlusion**: In Android, when an `OutlinedTextField` gains focus, the on-screen software keyboard slides up. Currently:
   - `AndroidManifest.xml:22-29` defines `.MainActivity` without specifying `android:windowSoftInputMode`, falling back to default behavior.
   - `VoiceInputOverlay.kt:180-186` wraps the bottom sheet in a `Column` with `verticalScroll()` but without `imePadding()`.
   - `MealEntryOverlay.kt:57-74` and `ActivityEntryOverlay.kt:64-83` display editors inside `AlertDialog` with `Column.verticalScroll()` but without `imePadding()` or non-fitting window decor properties.
   - `SettingsScreen.kt:168-171` displays settings in a `Column.verticalScroll()` without `imePadding()`.

As a result, focusing any text field located in the lower half of these surfaces causes the keyboard to cover the active field, obscuring what is typed and preventing inspection.

## Goals / Non-Goals

**Goals:**
- Retain the 24-hour clock dial modality while enlarging the dial container diameter and increasing radial separation / touch target padding between inner (00/12–23) and outer (01–12) rings to prevent misclicks.
- Ensure text input fields remain fully visible and interactive above the software keyboard across all screens, dialogs, and bottom sheets.
- Ensure focused text fields automatically scroll into view above the keyboard.
- Maintain pure JVM testability and ensure zero regressions in timestamp merge/validation logic.

**Non-Goals:**
- No switching to 12-hour AM/PM dials or text-only time inputs.
- No external window-management or insets dependencies.
- No changes to database entities, Room schema, or backup serialization.
- No alteration of `DatePickerDialog` or calendar selection rules.
- Reasons are detailed in `proposal.md`.

## Decisions

### D1 — Retain 24-hour clock dial modality while enlarging dial touch targets and ring spacing

**Decision.** Keep `TimePicker` configured for 24-hour time (`is24Hour = true`), but enlarge the dial container and increase the radial distance and touch target padding between the inner and outer hour tracks.

**Alternative rejected.** Switch to a 12-hour AM/PM dial picker.

**Why it lost.** The user explicitly prefers the 24-hour clock dial representation. Switching to 12-hour mode introduces an extra AM/PM toggle tap and conflicts with the user's mental model and locale habits.

**Alternative rejected.** Replace the dial with numeric keypad text inputs (`TimeInput`).

**Why it lost.** The user prefers the direct, visual, single-tap interaction of the clock dial over typing numbers on a keyboard.

### D2 — Dial diameter enlargement and radial spacing mechanism

**Decision.** Enlarge the clock dial container diameter and increase radial distance and touch target padding between the inner ring (00/12–23) and outer ring (01–12) in `TimePickerDialog` in `ui/components/CommonUi.kt:390-406`.

**Alternative rejected.** Retain standard M3 default dial dimensions (256dp) and rely only on haptic feedback or touch slop adjustments.

**Why it lost.** The standard 256dp diameter leaves ~35dp radial spacing between concentric hour tracks. Thumb interaction physically demands >=48dp touch clearance to reliably distinguish adjacent tracks.

**Build/tooling assumption & Spike.** Compose Material 3 `TimePicker` does not expose separate ring radius parameters in its public `TimePickerColors` or `TimePickerDefaults`. Therefore, enlarging the dial container via layout scale modifier / expanded container bounds or a dedicated 24-hour dial layout wrapper must be validated on-device. Gated behind Task 1.1 spike.

### D3 — Setting `android:windowSoftInputMode="adjustResize"` on `MainActivity` in `AndroidManifest.xml`

**Decision.** Declare `android:windowSoftInputMode="adjustResize"` on `.MainActivity` in `app/src/main/AndroidManifest.xml:23`.

**Alternative rejected.** Programmatically manipulate window flags in `MainActivity.onCreate()` using `WindowInsetsControllerCompat`.

**Why it lost.** Manifest declaration is the canonical Android mechanism for activity window soft input behavior; setting it in the manifest guarantees the window manager applies resize behavior immediately during window initialization before any frame composition.

**Alternative rejected.** Use `adjustPan`.

**Why it lost.** `adjustPan` shifts the entire root window upward, causing top bars and dialog framing to pan off-screen and breaking Compose's granular insets calculations.

### D4 — WindowInsets IME padding on scrollable containers

**Decision.** Apply `Modifier.imePadding()` directly to the vertically scrollable content columns in `VoiceInputOverlay` (`VoiceSheetContent`), `MealEntryOverlay`, `ActivityEntryOverlay`, and `SettingsScreen`.

**Alternative rejected.** Applying `imePadding()` at the root screen or `Surface` level in `MainActivity.kt`.

**Why it lost.** Root-level IME padding shrinks the entire application viewport, compressing background surfaces, floating action buttons, and top bars unnecessarily. Applying IME padding directly to scrollable containers compresses only the scrollable viewport, preserving toolbar/header anchor positions while giving the scroll container room to scroll content above the keyboard.

### D5 — Dialog window soft input behavior for `AlertDialog` (`MealEntryOverlay` and `ActivityEntryOverlay`)

**Decision.** Configure `AlertDialog` instances in `MealEntryOverlay.kt:57-74` and `ActivityEntryOverlay.kt:64-83` with `DialogProperties(decorFitsSystemWindows = false)` so the dialog window participates in window insets dispatch, coupled with `Modifier.imePadding()` on the inner scrollable `Column`.

**Alternative rejected.** Leave `AlertDialog` with default `DialogProperties(decorFitsSystemWindows = true)`.

**Why it lost.** Default dialog properties cause the dialog window to consume system insets internally, preventing child composables from receiving IME insets and resulting in the keyboard floating on top of the dialog content.

### D6 — Automatic scroll-into-view on focus

**Decision.** Rely on Compose foundation's native `bringIntoView` mechanism within `Modifier.verticalScroll(...)` combined with `Modifier.imePadding()`, ensuring that focused text fields automatically scroll above the software keyboard when the keyboard expands.

**Alternative rejected.** Manually calculating pixel offsets and triggering coroutine scrolls via `LaunchedEffect(isFocused)`.

**Why it lost.** Compose's built-in `Modifier.verticalScroll` already includes focus-driven `bringIntoView` coordination. When `imePadding()` compresses the scroll viewport, the scroll state automatically brings the focused cursor into the visible area without manual coordinate calculations.

### D7 — Target capabilities classification

**Decision.** Map the 24h dial ergonomics to `entry-timestamp-editing`, and map IME visibility and scroll ergonomics to `ui-responsiveness`.

**Alternative rejected.** Create a new standalone `keyboard-ergonomics` capability.

**Why it lost.** `ui-responsiveness` already governs UI fluidity, interaction responsiveness, and layout presentation without blocking; keeping IME visibility in `ui-responsiveness` maintains cohesive UI behavior specs. `entry-timestamp-editing` already specifies all timestamp pickers and editors.

## Risks / Trade-offs

- **Dial scaling within `TimePickerDialog`**: An enlarged dial must fit within standard mobile portrait screens without clipping action buttons (OK/Cancel). Bounded by keeping dialog width within screen bounds and vertical arrangement balanced.
- **Keyboard animation jank**: Rapidly opening/closing the keyboard could trigger layout recalculations. Bounded by Compose's animated insets handling and standard hardware-accelerated rendering.

## Open Questions

None. Decisions D1 through D7 settle the design.
