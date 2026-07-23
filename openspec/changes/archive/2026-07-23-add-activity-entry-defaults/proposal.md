## Why

Two small friction points make manual activity logging slower than it should be. (F2) The activity title field opens blank and only falls back to the type's display name silently on save, so the user can't see or tweak the default and often has to type a title. (F3) Steps are almost always logged at the end of the day, but a new step entry defaults its time to "now," forcing the user to correct it every time. Both are quick data-entry defaults on the existing activity form.

## What Changes

- **F2 — pre-fill the title:** When the add-activity form opens, the title field is pre-populated with the current activity type's display name (e.g. "Steps", "Running"), fully editable. This replaces reliance on the silent blank→fallback behavior so the default is visible and can be saved directly or tweaked. (Scope: pre-fill only; the title does not auto-track later type changes.)
- **F3 — default step time to 23:59:** When adding a `STEPS` activity, the time defaults to 23:59 on the selected day, still editable. Other activity types keep their current default. Existing entries and editing an existing activity are unaffected.

Out of scope: making the title auto-update when the type changes; forcing (non-editable) timestamps; changing the default for non-step activity types.

## Capabilities

### Modified Capabilities
- `activity-logging`: the add-activity form pre-fills the title with the type's display name (editable), and step-type activities default their time to 23:59 on the selected day (editable).

## Impact

- `app/src/main/java/com/example/vocalorie/ui/entries/ActivityEntryOverlay.kt` — initialize the title field with `type.displayName()` when opening the add form (not when editing an existing activity); default the time-of-day to 23:59 when the type is `STEPS`.
- `app/src/main/java/com/example/vocalorie/ui/components/ActivityEditor.kt` — if the editable form owns the title/time initial state, apply the defaults there.
- `app/src/main/java/com/example/vocalorie/model/ActivityModels.kt` — reference `ActivityType.STEPS` and `displayName()`; no model shape change.
- Tests: coverage for add-form title pre-fill per type and 23:59 default for `STEPS`; no schema, dependency, or LLM changes.
