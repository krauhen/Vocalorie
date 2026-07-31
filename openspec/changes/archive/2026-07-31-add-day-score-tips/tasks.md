## 1. Pure tip derivation (day-score-tips logic)

- [x] 1.1 Add `app/src/main/java/com/example/vocalorie/ui/entries/stats/DayScoreTips.kt` — same package as `MealStatsCalculator.kt` so the `internal` sub-score functions stay `internal`. Define a `DayScoreTip` value type (stable id/kind + `text`) and a `dayScoreTips(totals, goals, activityBurnedKcal, hasLoggedActivity, localTime)` entry point returning a ranked `List<DayScoreTip>`.
- [x] 1.2 Compute the shortfalls by calling the existing `calorieAdherence` / `proteinAdherence` / `carbsAdherence` / `fatAdherence` (`MealStatsCalculator.kt:154`, `:167`, `:174`, `:186`) and the quality overages behind `qualityMultiplier` (`MealStatsCalculator.kt:200`) against `calorieTarget = goals.calorieGoalKcal + 0.5 * activityBurnedKcal` — the same expression as `nutritionScore` (`MealStatsCalculator.kt:137`). Do not add new thresholds.
- [x] 1.3 Rank by `weight × (100 − subScore)` with the score's weights (`MealStatsCalculator.kt:143`); quality nutrients rank by `0.10 × overage × 100`; skip any component at 100.
- [x] 1.4 Add the tip catalogue verbatim from the twelve-row table in `design.md` (rows 1–11 here, row 12 in task 1.5) — exactly those triggers and strings, no protein-over entry, no category the score does not measure.
- [x] 1.5 Add the activity tip, row 12 of the table (`"Over budget — log some sport to offset it."`), emitted only when intake exceeds the activity-adjusted calorie target and `hasLoggedActivity` is false; rank at half the calorie tip's value.
- [x] 1.6 Add late-day suppression: at or after 21:00 local, drop the eat-more tips (calories/protein/carbs/fat under). Take the time as a parameter — no `LocalTime.now()` inside the module.
- [x] 1.7 Add reply validation: `validateRewordedTips(ruleTips, replyTips)` accepting only an equal count with every entry 5–10 words (whitespace-separated tokens containing at least one letter or digit, so a standalone dash does not count), returning the rule tips otherwise. Wholesale, never per-tip.
- [x] 1.8 Add `app/src/test/java/com/example/vocalorie/ui/entries/stats/DayScoreTipsTest.kt` covering: calorie 60 outranks fat 20; on-target component emits nothing; carbs gap outranks a 2× sugar overage; score-100 day yields an empty list; activity tip present/absent for the three cases; 23:00 drops protein-under but keeps over-budget; 18:00 keeps protein-under; 23:00 all-eat-more yields empty; all twelve catalogue strings match the design.md table and each is 5–10 words; no protein-over tip exists; validation rejects wrong count and a 12-word entry and accepts a valid trio.
- [x] 1.9 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 2. Rotation-interval setting (day-score-tips setting)

- [x] 2.1 Add `KEY_TIP_ROTATION_SECONDS = "tip_rotation_seconds"` and `DEFAULT_TIP_ROTATION_SECONDS = 5` plus `getTipRotationSeconds()` / `saveTipRotationSeconds(Int)` in `settings/NutritionSettingsStore.kt` (companion at `:75`, following `getBaseCaloriesBurned` at `:21`).
- [x] 2.2 Add `tipRotationSeconds: Int` to `ThemeSettingsSnapshot` (`data/repository/ThemeSettingsRepository.kt:13`), read it in `currentSnapshot()` (`:43`), and add `suspend fun saveTipRotationSeconds(value: Int)` beside `saveBaseCaloriesBurned` (`:76`).
- [x] 2.3 Add `tipRotationSeconds` to `ui/settings/SettingsUiState.kt` (beside `baseCaloriesBurned` at `:39`) and `SettingsEvent.SaveTipRotationSeconds(val input: String)` (beside `:79`).
- [x] 2.4 Handle it in `ui/capture/MealCaptureViewModel.kt`: dispatch in the settings `when` (`:520`) and add `saveTipRotationSeconds(input: String)` mirroring `saveBaseCaloriesBurned` (`:566`) — trim, `toIntOrNull`, accept `0` or `2..60`, otherwise set `settingsMessage` and return without writing; reload via `loadThemeSettings()`.
- [x] 2.5 Add the numeric field + Save button in `ui/settings/SettingsScreen.kt` following the base-calories-burned field (`:182`–`:196`), labelled so `0` is documented as "no rotation".
- [x] 2.6 Add `tipRotationSeconds` to `ui/capture/MealCaptureUiState.kt` beside `baseCaloriesBurned` / `kcalPerStep` and populate it where the theme-settings snapshot is applied.
- [x] 2.7 Unit tests: default is 5; saving 10 persists; saving 0 persists; 90, 1 and `"abc"` each leave the stored value unchanged and set a message.
- [x] 2.8 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 3. Tips in the state holder (day-score-tips wiring)

- [x] 3.1 Add `dayScoreTips: List<DayScoreTip>` and `tipsRewordingInFlight: Boolean` to `ui/capture/MealCaptureUiState.kt`.
- [x] 3.2 Derive the tips in `MealCaptureViewModel` from `savedMeals` + `savedActivities` + `nutritionGoals` + `now`, reusing the day-window helper the header already uses (`filterActivitiesForDay`, `MealTimeWindows.kt`) and the same daily-totals aggregation as `MealEntriesScreen.kt`; recompute on any of those changing. Keep the raw lists as-is — only the tip list is added as an aggregate (design D1).
- [x] 3.3 Gate to today with at least one logged meal: empty list otherwise.
- [x] 3.4 Unit tests on the state holder: a today-with-meals state exposes a non-empty ranked list; a no-meals state exposes an empty list; a past selected day exposes an empty list.
- [x] 3.5 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 4. Tips strip in the daily nutrition section (day-score-tips presentation)

- [x] 4.1 Add a `DayScoreTipsSection` composable in `ui/entries/MealEntriesStatsHeader.kt` taking the ranked list, `rotationSeconds`, `canReword` and `onReword`; render nothing for an empty list.
- [x] 4.2 Show one tip at a time with a crossfade, advancing every `rotationSeconds` through the top 3; no rotation when `rotationSeconds == 0` or fewer than two tips. Keep the rotating index and expanded flag in `remember` — view state only (design D7).
- [x] 4.3 Tap toggles expansion: expanded lists up to 5 tips and stops rotation; collapsing resumes it.
- [x] 4.4 Call it from `SelectableStatsHeader` beneath the score row, above `EnergySummaryRow("Burned")`, and thread the new parameters from `MealEntriesScreen.kt` (header call site ~`:99`–`:155`) — the screen passes the state-holder tips through instead of deriving anything.
- [x] 4.5 Confirm on a device/emulator that the header still fits without displacing the kcal total, burned/balance rows, macro line or histogram, in both the compact and expanded tip states: `./gradlew :app:installDebug --no-daemon`, log a meal for today, open the entries screen.
- [x] 4.6 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 5. Optional LLM rewording (day-score-tips rewording)

- [x] 5.1 Add a narrow rewording entry point beside `ai/KoogNutritionAgent.kt` taking the rule tips plus the day's totals/targets and returning the model's list. Do not touch the nutrition-estimate prompt or DTOs — their exact wording is pinned by `ai/NutritionPromptContractTest.kt`.
- [x] 5.2 Instruct the model explicitly: same count, same order, same meaning, 5–10 words each, no additions or removals.
- [x] 5.3 Handle the refresh event in `MealCaptureViewModel`: set `tipsRewordingInFlight`, call the agent inside `runCatching`, pass the reply through `validateRewordedTips`, and on any failure or rejection restore the rule tips with no `settingsMessage`, no warning and no error state.
- [x] 5.4 Show the refresh affordance only when an OpenAI key is stored; hide it otherwise.
- [x] 5.5 Unit tests using a fake rewording collaborator (no network, no key, no billable call): valid trio replaces the text; two-tip reply is discarded; a 12-word entry discards the whole reply; a thrown error leaves the rule tips and no warning.
- [x] 5.6 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 6. Documentation and specs

- [x] 6.1 Sync the delta specs into `openspec/specs/day-score-tips/spec.md` (new) and `openspec/specs/day-nutrition-score/spec.md` (amended header requirement).
- [x] 6.2 Note the `MealCaptureUiState` aggregate-value precedent from design D1 against ADR-6/ADR-8 in `docs/arc42.md`, and add the crossfade-vs-visual-baseline trade-off to the §11.1 accepted-debt table if it survives review.
- [x] 6.3 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon` and confirm no Room schema, `app/schemas/`, `BACKUP_SCHEMA_VERSION` or dependency file was touched by this change.
