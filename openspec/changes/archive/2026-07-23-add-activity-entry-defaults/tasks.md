## 1. Pre-fill activity title (F2)

- [x] 1.1 In `ActivityEntryOverlay.kt` (add path), initialize the title field with `type.displayName()` when opening the form to add a new activity
- [x] 1.2 Ensure the pre-fill applies only on add, not when editing an existing activity
- [x] 1.3 Ensure the pre-filled value is editable and is not auto-overwritten if the user changes the type afterward
- [x] 1.4 If `ActivityEditor.kt` owns the title initial state, apply the default there instead

## 2. Default step time to 23:59 (F3)

- [x] 2.1 When the selected type is `ActivityType.STEPS`, default the form time-of-day to 23:59 on the selected day
- [x] 2.2 Keep the time editable; leave non-step types on their existing default
- [x] 2.3 Ensure editing an existing activity preserves its saved timestamp (no 23:59 override)

## 3. Tests

- [x] 3.1 Add-form title pre-fill equals the type display name per type; edit-form shows saved title
- [x] 3.2 STEPS add defaults to 23:59; a non-step type does not; editing preserves the stored timestamp

## 4. Verification

- [x] 4.1 Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon` and confirm all tests pass
- [x] 4.2 Manually verify on emulator/device: adding a Steps activity opens with title "Steps" and time 23:59; adding another type pre-fills its name and keeps the normal time; editing an existing activity is unchanged
