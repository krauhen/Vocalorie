## Context

The meal capture flow (`VoiceInputOverlay.kt`) currently limits saved meal search suggestions to 5 items (`take(5)` in `VoiceInputOverlay.kt:211`, `searchSavedMeals` in `MealMappers.kt:245`), preventing users from finding older matching entries. Concurrently, new meal drafts generated from LLM estimation (`NutritionAgentResult.toEditableDraft()`) or cache reuse leave `createdAtEpochMillis` null or synthetic 0L, hiding `EntryTimestampField` in `EditableMealEditor.kt:111` and forcing users to save before backdating.

Binding constraints from `docs/agent/guidance/coding.md` and `docs/agent/guidance/testing.md`:
- Pure logic must be JVM-testable without Android context or Room instrumentation.
- UI layering is strict: UI -> state holder (ViewModel) -> repository -> store/DAO.
- Avoid nested unbounded scrolling crashes in Jetpack Compose.
- Single Gradle module, AGP 9.2.1 with built-in Kotlin, no new libraries or dependencies.

## Decisions

### D1: Restrict search suggestions strictly to non-blank queries
Suggestions are rendered in `VoiceInputOverlay.kt` only when `searchQuery.isNotBlank()`. When the search input is empty or consists solely of whitespace, no suggestions list is rendered.

*Alternative considered — show the most recent meals when search input is empty.* Lost because rendering suggestions without an active query creates excessive visual noise in the capture overlay, distracting from the primary task of speaking or typing a new meal description. Browsing recent meals is already served by the main entries screen.

### D2: Incremental 5-item batch pagination in a scroll-bounded container
Suggestions start by displaying 5 items. As the user reaches the end of the scrollable list container, the loaded count increments by 5 (`currentCount + 5`) until it reaches either the total number of matching items or the configured maximum limit N (default 50, capped at 50). The suggestions container uses `Modifier.heightIn(max = 240.dp)` with internal scrolling to prevent scroll conflicts with `VoiceInputOverlay`'s outer `verticalScroll`.

*Alternative considered — load all N matches simultaneously.* Lost because rendering up to 50 complex cards at once within an overlay causes initial composition stutters on mobile devices. Incremental 5-item chunks maintain fluid 60 fps interactions.

*Alternative considered — manual "Load more" button.* Lost because automatic end-of-list pagination provides a seamless continuous scrolling experience with zero extra taps required from the user.

### D3: Configurable limit N in Settings backed by NutritionSettingsStore
The maximum suggestion limit N is stored in `NutritionSettingsStore` under `KEY_MAX_SEARCH_SUGGESTIONS`, exposed via `ThemeSettingsRepository` (and `ThemeSettingsSnapshot`), and editable in `SettingsScreen.kt`. The default value is 50, with a hard upper bound of 50 and a lower bound of 5 (`5..50`). Any entered value exceeding 50 is clamped to 50.

*Alternative considered — hardcode N=50 without a user setting.* Lost because users with smaller or specialized meal histories prefer tighter caps to prevent deep scrolling, while the requirement explicitly demands a configurable limit up to 50.

*Alternative considered — separate dedicated SharedPreferences file.* Lost because `NutritionSettingsStore` already manages numerical preferences and goals within the shared `PREFS_NAME`, keeping settings centralized without file sprawl.

### D4: Seed new draft timestamp with target day instant
Whenever a new meal draft is generated—whether produced by `runEstimate` from the LLM or prepared from a cache match / search suggestion (`CachedMealMatch`)—`createdAtEpochMillis` is initialized to `newEntryTimestampMillis()`. This correctly anchors the draft to the currently viewed day offset (`state.selectedDayOffset`) at the current wall-clock time.

*Alternative considered — leave createdAtEpochMillis null until save time.* Lost because leaving it null prevents `EntryTimestampField` from rendering in `EditableMealEditor`, making it impossible for the user to view or edit the timestamp prior to saving.

*Alternative considered — initialize to Instant.now() regardless of day offset.* Lost because if the user has navigated to yesterday (`selectedDayOffset = 1`) to log missed meals, defaulting to `Instant.now()` would misplace the entry onto today unless manually fixed every time.

### D5: Surface EntryTimestampField in EditableMealEditor and gate save on validity
`EditableMealEditor` displays `EntryTimestampField` whenever `draft.createdAtEpochMillis != null`. Because D4 guarantees non-null timestamps on all new drafts, the field appears immediately beneath the meal title and description. `VoiceInputOverlay` passes `onCreatedAtValidationChange` to track timestamp parsing validity (`isTimestampValid`), disabling the "Save entry" button (`enabled = enabled && canSave && isTimestampValid`) when the timestamp text is malformed.

*Alternative considered — add a separate one-off date picker button in VoiceInputOverlay outside EditableMealEditor.* Lost because `EntryTimestampField` in `CommonUi.kt` already encapsulates format hint presentation, error styling, manual text parsing, and Material 3 date and time dialog pickers; duplicating picker controls outside the editor would create UI inconsistency and code duplication.

### D6: Honor draft timestamp in saveNewMeal
In `MealCaptureViewModel.saveNewMeal(mealDraft)`, the save operation passes `mealDraft.createdAtEpochMillis ?: newEntryTimestampMillis()` to `mealRepository.saveReviewedMeal()`, persisting the exact timestamp confirmed or edited by the user.

*Alternative considered — retain newEntryTimestampMillis() in saveNewMeal.* Lost because overwriting the draft timestamp at save time discards any adjustments made by the user in the pre-save timestamp picker, defeating the purpose of F2.

### D7: Pure JVM-testable logic helpers
Pagination limit calculation (`calculateNextSuggestionsLimit(currentCount: Int, step: Int = 5, maxLimit: Int, totalMatches: Int): Int`) and settings clamping logic (`coerceSuggestionsLimit(value: Int?, default: Int = 50, max: Int = 50): Int`) are isolated as pure functions without Android or Compose dependencies, fully tested via pure-JVM unit tests.

*Alternative considered — embed pagination and limit clamping directly within Composable state callbacks.* Lost because untestable UI logic violates Rule 3 in `docs/agent/guidance/testing.md` ("extracted pure functions ship tested").

## Risks

- **Nested scroll interaction**: `VoiceInputOverlay` uses an outer scrollable column (`Modifier.verticalScroll(rememberScrollState())`). Placing an inner scrollable list inside an unconstrained column causes measurement exceptions. *Mitigation*: The suggestions list is constrained with an explicit maximum height (`Modifier.heightIn(max = 240.dp)`) and internal scrolling (`rememberScrollState()` or `LazyColumn`), allowing it to scroll independently without expanding the parent overlay beyond screen bounds.
- **Search filtering performance**: Searching through thousands of historical meals on every keystroke could introduce typing latency. *Mitigation*: `searchSavedMeals` filters lazily using `asSequence()` and terminates immediately once the active pagination limit (capped at N <= 50) is reached.
