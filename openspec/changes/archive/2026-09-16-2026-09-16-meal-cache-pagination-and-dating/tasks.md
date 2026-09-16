## 1. Pure domain logic & pagination helpers (meal-caching logic)

- [ ] 1.1 In `app/src/main/java/com/example/vocalorie/data/MealMappers.kt`, extract pure helper `fun calculateNextSuggestionsLimit(currentCount: Int, step: Int = 5, maxLimit: Int, totalMatches: Int): Int` to compute the next visible batch size clamped to `min(maxLimit, totalMatches)`.
- [ ] 1.2 In `app/src/main/java/com/example/vocalorie/settings/NutritionSettingsStore.kt`, extract pure helper `fun coerceSuggestionsLimit(value: Int?, default: Int = 50, max: Int = 50, min: Int = 5): Int` clamping inputs to `min..max`.
- [ ] 1.3 Extend `app/src/test/java/com/example/vocalorie/data/MealMappersTest.kt` with table unit tests for `calculateNextSuggestionsLimit`: step increments from 5 to 10, capping at `maxLimit = 50`, stopping when `totalMatches < currentCount + step`, and handling empty results.
- [ ] 1.4 In `app/src/test/java/com/example/vocalorie/data/MealMappersTest.kt`, verify `searchSavedMeals` with dynamic limits (e.g. limit=10, limit=50) matches up to requested limit without exceeding it.
- [ ] 1.5 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 2. Settings persistence for configurable limit N (data layer)

- [ ] 2.1 In `app/src/main/java/com/example/vocalorie/settings/NutritionSettingsStore.kt`, define `KEY_MAX_SEARCH_SUGGESTIONS = "max_search_suggestions"`, `DEFAULT_MAX_SEARCH_SUGGESTIONS = 50`, `MAX_SEARCH_SUGGESTIONS = 50`, and `MIN_SEARCH_SUGGESTIONS = 5`. Add `fun getMaxSearchSuggestions(): Int` and `@Synchronized fun saveMaxSearchSuggestions(value: Int)` with clamping via 1.2.
- [ ] 2.2 In `app/src/main/java/com/example/vocalorie/data/repository/ThemeSettingsRepository.kt:13`, add `maxSearchSuggestions: Int` to `ThemeSettingsSnapshot`, read it in `currentSnapshot()` (`:45`), and add `suspend fun saveMaxSearchSuggestions(value: Int)` beside `saveTipRotationSeconds`.
- [ ] 2.3 In `app/src/main/java/com/example/vocalorie/ui/settings/SettingsUiState.kt:39`, add `maxSearchSuggestions: Int` to `SettingsUiState` and add event `data class SaveMaxSearchSuggestions(val input: String) : SettingsEvent()`.
- [ ] 2.4 In `app/src/main/java/com/example/vocalorie/ui/settings/SettingsScreen.kt`, add an editable settings card for "Search suggestions limit" accepting integer inputs, validating via `coerceSuggestionsLimit`, displaying current limit and range hint (5–50, default 50).
- [ ] 2.5 In `app/src/test/java/com/example/vocalorie/settings/NutritionSettingsStoreTest.kt`, add test cases verifying default 50, valid updates (e.g. 25), and clamping of values > 50 down to 50 and values < 5 up to 5.
- [ ] 2.6 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 3. Paginated search suggestions in UI (B1)

- [ ] 3.1 In `app/src/main/java/com/example/vocalorie/ui/capture/MealCaptureUiState.kt:38`, add `maxSearchSuggestions: Int` and map it from `ThemeSettingsSnapshot` in `MealCaptureViewModel.kt:98, 139`.
- [ ] 3.2 In `app/src/main/java/com/example/vocalorie/ui/capture/MealCaptureViewModel.kt`, handle `SettingsEvent.SaveMaxSearchSuggestions` by validating input, saving to repository, and refreshing settings snapshot.
- [ ] 3.3 In `app/src/main/java/com/example/vocalorie/ui/MealCaptureScreen.kt:125`, update the search suggestions invocation to pass `limit = state.maxSearchSuggestions` to `searchSavedMeals`.
- [ ] 3.4 In `app/src/main/java/com/example/vocalorie/ui/voice/VoiceInputOverlay.kt:209-232`, guard suggestions with `if (searchQuery.isNotBlank())`. Replace the hardcoded `searchResults.take(5)` with a scroll-bounded container (`Modifier.heightIn(max = 240.dp)`) tracking visible count (starting at 5) and incrementing by 5 via `calculateNextSuggestionsLimit` when scrolled to the end, up to `maxSearchSuggestions`. Reset visible count to 5 when `searchQuery` changes.
- [ ] 3.5 In `app/src/test/java/com/example/vocalorie/ui/settings/SettingsUiStateTest.kt`, assert `maxSearchSuggestions` is correctly populated in `SettingsUiState`.
- [ ] 3.6 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 4. Pre-save timestamp picker on new meal drafts (F2)

- [ ] 4.1 In `app/src/main/java/com/example/vocalorie/ui/capture/MealCaptureViewModel.kt:307`, update `runEstimate` to seed the draft timestamp: `outcome.result.toEditableDraft().copy(createdAtEpochMillis = newEntryTimestampMillis())`.
- [ ] 4.2 In `app/src/main/java/com/example/vocalorie/ui/capture/MealCaptureViewModel.kt:257, 379`, ensure cached meal approval drafts and search-picked meal drafts seed `createdAtEpochMillis = newEntryTimestampMillis()`.
- [ ] 4.3 In `app/src/main/java/com/example/vocalorie/ui/capture/MealCaptureViewModel.kt:352`, update `saveNewMeal(mealDraft)` to pass `mealDraft.createdAtEpochMillis ?: newEntryTimestampMillis()` to `mealRepository.saveReviewedMeal()`, preserving the user-edited timestamp.
- [ ] 4.4 In `app/src/main/java/com/example/vocalorie/ui/voice/VoiceInputOverlay.kt:274`, pass `onCreatedAtValidationChange` to `EditableMealEditor`, tracking `isTimestampValid` state and updating `canSave = draft != null && isTimestampValid` at line `:177`.
- [ ] 4.5 In `app/src/test/java/com/example/vocalorie/data/MealMappersTest.kt`, verify that an `EditableMealDraft` with a customized `createdAtEpochMillis` converts to `MealEntity` preserving that exact timestamp.
- [ ] 4.6 Extend `app/src/test/java/com/example/vocalorie/ui/capture/EstimatePlanTest.kt` (or state holder test) to verify that `saveNewMeal` stores the customized timestamp from the draft rather than overwriting with wall-clock now.
- [ ] 4.7 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 5. Capability spec delta & documentation

- [ ] 5.1 Add the new requirements for paginated search suggestions and pre-save meal draft timestamp editing to `openspec/changes/2026-09-16-meal-cache-pagination-and-dating/specs/meal-caching/spec.md`.
- [ ] 5.2 Verify OpenSpec compliance: `openspec validate 2026-09-16-meal-cache-pagination-and-dating --strict` and `openspec validate --specs`.

## 6. On-device confirmation

- [ ] 6.1 Install debug build to connected device: `./gradlew :app:installDebug --no-daemon`.
- [ ] 6.2 Verify B1 on device: Enter a search query with many matches in `VoiceInputOverlay`. Confirm suggestions appear only while `searchQuery.isNotBlank()`, start with 5 items, and scrolling down loads 5 additional items up to configured N. Confirm changing N in Settings (e.g. to 15) caps suggestions at 15.
- [ ] 6.3 Verify F2 on device: Start a new meal estimate or pick from search suggestions while viewing a past day offset. Confirm the date/time picker is immediately visible in the draft editor prefilled with the target day. Change the date/time, tap "Save entry", and confirm the meal appears under the selected date and time in the entries list.
- [ ] 6.4 Final test suite run: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`.
