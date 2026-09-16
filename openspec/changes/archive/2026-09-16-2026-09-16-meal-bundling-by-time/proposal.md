## Why

When logging meals, the user frequently enters meal components sequentially across a few minutes — for example, logging a coffee first, toast 5 minutes later, and an apple 4 minutes after that, or entering multiple courses of dinner as distinct queries. Currently, the entries list (`MealEntriesScreen.kt:219`) displays every logged meal as an isolated, standalone card with equal spacing and independent styling.

This creates visual fragmentation for what was in reality a single eating session. The user has to mentally search for adjacent rows and manually sum calories and macros in their head to answer simple questions like "what did breakfast total?". At the same time, the list appears cluttered with repetitive timestamp and category markers for items eaten together.

This change introduces time-based meal bundling: consecutive food entries logged within 15 minutes of each other are visually grouped into a cohesive meal bundle with a connected accent bracket and a summary badge showing the bundle's total calories and macros, while preserving each item's individual card and click-to-edit behavior.

## What Changes

- **Consecutive 15-minute gap bundling rule**: Consecutive food entries on the same calendar day where the time difference is 15 minutes or less (`abs(t_i - t_{i+1}) <= 15 min`) are grouped into a single meal bundle. Chained entries (e.g. 12:00, 12:10, 12:22) form a single bundle because each consecutive step is within 15 minutes.
- **Visual presentation with accent bracket and summary badge**: Bundles containing multiple entries are framed with a vertical accent bracket along the leading edge and display a header badge showing the bundle's aggregated calories and macros (Fat, Carbs, Protein, Amount). Single entries without neighboring items within 15 minutes remain standalone cards without bracket decoration.
- **Preserved entry inspection and editing**: Each entry within a bundle remains an individual card displaying its full title, query, individual category icon, macros, and timestamp. Tapping any entry opens that specific meal's editor, identical to current row behavior.
- **Pure calculation logic**: Grouping is performed by a pure, deterministic function (`bundleMealsByTime`) operating on the list of meals for the day. It has no Android dependencies and is verified by pure-JVM unit tests per `docs/agent/guidance/testing.md`.
- **Zero schema or persistence changes**: Bundling is strictly a presentation-layer aggregation over `SavedMeal`. Room entities, database tables, and JSON payloads remain untouched.

## Capabilities

### Modified Capabilities

- `entries-list-presentation`: amend the entries list presentation requirements to group consecutive food entries within 15 minutes into meal bundles, render visual accent brackets and aggregate summary badges for multi-entry bundles, and preserve individual entry card interactions.

## Impact

- **UI presentation**: `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesRows.kt` adds bundle presentation composables (bracket container and bundle summary badge). `MealEntriesScreen.kt` iterates over bundles instead of a raw flat list of meals.
- **Pure domain/UI logic**: Pure bundling function and immutable data structures (`MealBundle`) added under `app/src/main/java/com/example/vocalorie/ui/entries/MealBundling.kt` (or beside `MealTimeWindows.kt`).
- **Tests**: Pure-JVM unit tests in `app/src/test/java/com/example/vocalorie/ui/entries/MealBundlingTest.kt` verifying grouping boundaries, chaining behavior, empty lists, single items, and nutrition aggregation.
- **No Room or data changes**: No migration, no DAO changes, and no `BACKUP_SCHEMA_VERSION` movement.
- **No activity list impact**: Activity entries remain separate discrete items on their own tab.

## Non-goals

- **No database or schema changes**: Bundling is purely a view-level grouping and does not persist bundle identifiers or relational tables.
- **No bundling of activities**: Activities represent discrete physical exercises or workouts, not multi-component meals.
- **No manual bundle grouping, split, or merge controls**: Automatic 15-minute gap clustering eliminates manual state management and complex gesture interactions.
- **No cross-day bundling**: Bundling operates within the viewed calendar day window; entries spanning past midnight remain partitioned by the day navigator.
- **No changes to daily stats or nutrition score**: The day score and top header stats sum all meals for the day, which remains mathematically identical.
- **No changes to individual meal editing or deletion**: Tapping an item in a bundle opens the existing `MealEditor` for that individual meal without altering draft contracts.
