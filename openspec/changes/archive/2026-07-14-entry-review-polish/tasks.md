## 1. AI-generated meal title (meal-titling)

- [x] 1.1 Add `title: String` to `NutritionAgentResult` in `NutritionEstimateDtos.kt`
- [x] 1.2 Update `DEFAULT_SYSTEM_PROMPT` in `KoogNutritionAgent.kt` to instruct the model to generate a short, natural meal title
- [x] 1.3 Update `NutritionPromptContractTest.kt` to assert the new title instruction/field
- [x] 1.4 Update `MealMappers.kt`'s `toEditableDraft()` to use `result.title` instead of `query.toShortMealTitle(items)`, keeping `resolveMealTitle`'s manual-edit guard unchanged in `toEntity()`
- [x] 1.5 Update/add unit tests in `MealMappersTest.kt` covering: AI title pre-fills draft, manual title edit is preserved on re-save

## 2. Deficit/surplus balance coloring (energy-balance)

- [x] 2.1 In `MealEntriesScreen.kt`'s `SelectableStatsHeader`, swap the color mapping: negative balance (deficit) → positive/favorable color, positive balance (surplus) → `colorScheme.error`
- [x] 2.2 Confirm/add a suitable positive/favorable color token in `VocalorieTheme.kt` if one doesn't already exist, following existing theme conventions
- [x] 2.3 Verify no change to `dailyEnergyBalance` calculation in `MealTimeWindows.kt` or to balance labels

## 3. Meal item source URL fix (food-sources)

- [x] 3.1 Update `DEFAULT_SYSTEM_PROMPT` in `KoogNutritionAgent.kt` to remove the generic-database-name fallback and instruct the model to only emit a source when it can produce a real, concrete URL, leaving it blank otherwise
- [x] 3.2 Update `NutritionPromptContractTest.kt` to match the revised prompt wording
- [x] 3.3 Confirm `toConcreteSourceUrlOrBlank` in `MealMappers.kt` is left unchanged (no relaxation of the URL-only filter)

## 4. Future-entry hatch-fill highlighting (future-entries)

- [x] 4.1 Implement a shared diagonal hatch-stripe fill drawing (Compose `Canvas`/`drawWithContent`) parameterized by a bucket color, matching the reference dashed-border + hatch pattern
- [x] 4.2 Apply the hatch fill (in the row's existing calorie-bucket color) alongside the existing dashed border on `MealEntryRow` in `MealEntriesScreen.kt`
- [x] 4.3 Extend the same future-timestamp check and dashed-border-plus-hatch-fill treatment to `ActivityEntryRow` in `MealEntriesScreen.kt`, using its own calorie-bucket color
- [x] 4.4 Manually verify on-device/emulator: future meal and future activity rows both show the dashed border + hatch fill in their respective bucket colors; past/present rows are unaffected

## 5. Verification

- [x] 5.1 Run `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon` and confirm all tests pass
- [x] 5.2 Manually exercise the meal-entry flow end-to-end (new meal parse, title review/edit, balance header coloring, source display, future-entry highlighting for a meal and an activity) on an emulator/device
