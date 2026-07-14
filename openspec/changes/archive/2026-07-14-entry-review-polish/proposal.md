## Why

Live use of the app surfaced four rough edges in the meal-entry review experience: meal titles look mechanically derived rather than AI-generated, the deficit/surplus balance coloring reads backwards (a calorie deficit — the outcome most users want — is shown in alarm red), meal items almost never show a Source despite the prompt requiring one, and future-dated entries don't visually stand out the way they're supposed to. These are independent, low-risk fixes to existing capabilities, bundled into one increment since they all touch the meal-entry review surface.

## What Changes

- Add a `title` field to the nutrition-estimation prompt/DTO so the AI generates a short, natural meal title itself, replacing the current app-side heuristic (first food item's name). The AI-generated title still pre-fills the meal editor and remains user-editable, preserving the existing "don't overwrite manual edits" behavior.
- Flip the balance-row color semantics in the daily stats header: a calorie deficit (consumed < burned) now uses a positive/green color; a calorie surplus now uses the error/red color. Labels are unchanged. The unrelated per-card calorie-magnitude bucket coloring is untouched.
- Fix the meal-item Source bug: the mapper layer already only accepts real `http(s)` URLs (unchanged), but the nutrition prompt currently offers a generic-database-name fallback that gets silently discarded to blank. Change the prompt so the AI is pushed to always attempt a real source URL; if it genuinely cannot produce one confidently, a blank source is an accepted, expected outcome rather than a fallback name that gets thrown away.
- Extend future-entry visual highlighting: keep the existing calorie-bucket color (no neutral/fixed accent color), but add a diagonal hatch-stripe fill (in that same bucket color) alongside the existing dashed/dotted border, and extend the same future-entry treatment to activity entries, which currently have none.

Out of scope: a bigger multi-round conversational meal-entry feature (raised separately) is not part of this change and will be proposed independently.

## Capabilities

### New Capabilities
- `meal-titling`: AI-generated short meal titles as part of the nutrition-estimation result, replacing the app-side first-item-name heuristic, while remaining user-editable.

### Modified Capabilities
- `energy-balance`: the daily balance-row color mapping (deficit vs. surplus) is reversed.
- `food-sources`: the source-fallback rule changes from "URL or generic database name" to "always attempt a real URL; blank is an accepted outcome when no confident URL exists" — the generic-database-name fallback is removed since it was never actually surfaced to the user.
- `future-entries`: the future-entry highlighting requirement gains a hatch-stripe fill (in the existing calorie-bucket color) and is extended to activity entries, not just meal entries.

## Impact

- `app/src/main/java/com/example/vocalorie/ai/KoogNutritionAgent.kt` — prompt changes for title generation and source-URL guidance.
- `app/src/main/java/com/example/vocalorie/model/NutritionEstimateDtos.kt` — new `title` field on the nutrition result DTO.
- `app/src/main/java/com/example/vocalorie/data/MealMappers.kt` — consume AI-provided title instead of deriving it; `resolveMealTitle`/`toShortMealTitle` behavior adjusted accordingly.
- `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt` — balance-row color swap; future-entry hatch-fill styling for both `MealEntryRow` and `ActivityEntryRow`.
- `app/src/test/java/com/example/vocalorie/ai/NutritionPromptContractTest.kt` — updated deliberately alongside prompt wording changes.
- No dependency, build, or schema changes anticipated; no migration required (title is derived at parse time, not a new persisted column beyond what mappers already produce).
