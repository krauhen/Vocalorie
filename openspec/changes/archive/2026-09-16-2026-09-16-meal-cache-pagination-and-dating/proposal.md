## Why

When logging meals using the capture overlay (`VoiceInputOverlay.kt`), two user friction points repeatedly impede fast and accurate logging:

1. **Search suggestions are truncated to an arbitrary 5 items (B1).**
   Currently, search suggestions in `VoiceInputOverlay.kt:211` and `MealMappers.kt:245` hardcode a `limit = 5` via `searchResults.take(5)`. When the user searches for frequent staples with multiple past preparations (e.g. "Kaffee", "Müsli", "Salat"), only the 5 most recent matches are reachable. Older or alternative variations logged weeks or months ago are completely invisible and cannot be selected for reuse. At the same time, suggestions must remain strictly tied to active queries so the overlay does not display visual clutter when no search is underway (`searchQuery.isNotBlank()`).

2. **New meal drafts cannot be backdated or time-stamped before saving (F2).**
   Currently, when a new meal draft is generated—whether from voice/text LLM estimation (`NutritionAgentResult.toEditableDraft()`) or cache/search reuse (`CachedMealMatch`)—its `createdAtEpochMillis` is either `null` or synthetic `0L`. Because `EditableMealEditor.kt:111` conditionally renders the timestamp picker with `if (draft.createdAtEpochMillis != null)`, `EntryTimestampField` is completely hidden on new meal drafts. Furthermore, `MealCaptureViewModel.kt:352` forcefully overrides the timestamp with `newEntryTimestampMillis()` on save. If the user logs lunch at dinner time or back-logs yesterday's meals, they cannot adjust the timestamp on the draft. Instead, they are forced to save first with the wrong timestamp, locate the saved entry in the entries list, open the editor overlay, and re-date it post-save.

Resolving these issues allows the user to browse past recurring meals beyond the 5-item ceiling in smooth 5-item increments up to a configurable cap of 50, and directly pick the exact date and time on new meal drafts before committing the save.

## What Changes

- **Suggestions restricted to active search queries (B1)**: Search suggestions are rendered exclusively when `searchQuery.isNotBlank()`. If the search field is blank or empty, suggestions remain hidden.
- **Paginated suggestion scrolling up to N (B1)**: Suggestions render in a scrollable list that initially loads 5 items. When the user scrolls to the bottom of the list, 5 additional items load incrementally until reaching the total available matches or the configurable maximum N.
- **Configurable suggestion ceiling N in Settings (B1)**: The maximum suggestion limit N is configurable in Settings (`SettingsScreen.kt`), stored in `NutritionSettingsStore` (default 50, strictly capped at 50, allowed range 5..50).
- **Pre-save date+time picker on all new drafts (F2)**: All new meal drafts (from estimation, cache matches, or search selection) are created with `createdAtEpochMillis` initialized to the current target timestamp (`newEntryTimestampMillis()`, respecting `selectedDayOffset`).
- **Surface `EntryTimestampField` in the new meal editor (F2)**: `EditableMealEditor` displays `EntryTimestampField` directly on new meal drafts. In `VoiceInputOverlay`, timestamp validation is wired to `canSave`, preventing saving if the timestamp is unparseable.
- **Honor edited draft timestamps on save (F2)**: `MealCaptureViewModel.saveNewMeal` uses `mealDraft.createdAtEpochMillis ?: newEntryTimestampMillis()`, saving the draft with the exact timestamp chosen by the user.
- **Pure JVM-testable logic**: Pagination math (`calculateNextSuggestionsLimit`), setting validation/clamping (`coerceSuggestionsLimit`), and draft timestamp seeding are extracted as pure functions verified by unit tests per `docs/agent/guidance/testing.md`.

## Capabilities

### Modified Capabilities

- `meal-caching`: Add requirement specifying that search suggestions appear only during active search (`searchQuery.isNotBlank()`); add requirement for incremental 5-item pagination up to configurable limit N; add requirement for setting-configurable suggestion limit N defaulting to and capped at 50; add requirement for pre-save date and time editing on all new meal drafts.

## Impact

- **UI presentation**: `VoiceInputOverlay.kt` implements a scroll-bounded suggestions list with bottom-detection pagination; `MealEditor.kt` ensures `EntryTimestampField` is shown and validated for all meal drafts; `SettingsScreen.kt` adds an input card to configure the search suggestion limit.
- **UI state & ViewModel**: `MealCaptureUiState.kt` exposes `maxSearchSuggestions` and current pagination state; `MealCaptureViewModel.kt` handles pagination increments, seeds new draft timestamps, and preserves draft timestamps in `saveNewMeal`.
- **Settings storage**: `NutritionSettingsStore.kt` adds `KEY_MAX_SEARCH_SUGGESTIONS` with default 50 and maximum 50; `ThemeSettingsRepository.kt` exposes getters and setters in its snapshot.
- **Mappers & helpers**: `MealMappers.kt` supports dynamic search limits in `searchSavedMeals` and preserves target timestamps during draft conversion.
- **Tests**: Pure-JVM unit tests in `MealMappersTest.kt`, `NutritionSettingsStoreTest.kt`, `ThemeSettingsRepositoryTest.kt`, and `MealCaptureViewModelTest.kt`.
- **No Room migrations**: No SQLite schema changes, no database migrations, and no bump to `BACKUP_SCHEMA_VERSION`.

## Non-goals

- **No full-text search engine or SQLite FTS integration**: The existing in-memory normalized token matching is sufficiently fast for local single-user histories and avoids schema changes.
- **No unbounded or infinite suggestion loading**: The hard ceiling of N=50 avoids Compose layout strain and excessive memory usage on the main thread.
- **No suggestions when search query is empty**: Browsing the full history without a query belongs to the entries screen, not the capture overlay.
- **No changes to activity draft timestamps**: Activity draft creation already seeds `createdAtEpochMillis` and exposes `EntryTimestampField`.
- **No retrofitting of past database entries**: Existing saved meals already have valid timestamps; this change affects only new meal draft creation and search presentation.
