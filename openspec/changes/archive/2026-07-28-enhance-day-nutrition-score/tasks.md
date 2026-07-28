## 1. Nutrition goals settings (nutrition-goals)

- [x] 1.1 Add persisted keys + defaults in `settings/ThemeSettingsStore.kt`: `calorie_goal` (default 2400), `macro_split_protein`/`macro_split_carbs`/`macro_split_fat` (default 30/40/30), with getters/setters following the existing numeric-setting pattern
- [x] 1.2 Add a pure helper deriving gram targets from goal + split (protein/carbs ÷4, fat ÷9), e.g. `MacroTargets(proteinG, carbsG, fatG)`
- [x] 1.3 Add Settings UI in `settings/SettingsScreen.kt`: calorie-goal numeric field + macro split editor where two percentages are editable and the third is derived so the split always sums to 100
- [x] 1.4 Unit tests for target derivation (180/240/80 at 2400·30/40/30; proportional scaling at 3000) and split-sum invariant

## 2. Extend daily totals with quality nutrients (day-nutrition-score data)

- [x] 2.1 Add `saturatedFatG`, `sugarG`, `saltG` to `DailyNutritionTotals` in `ui/entries/stats/MealStats.kt`
- [x] 2.2 Sum the three fields in `buildDailyTotals` (`MealStatsCalculator.kt`) and in `List<SavedMeal>.toDailyNutritionTotals()` (`MealEntriesScreen.kt`)
- [x] 2.3 Build green (`:app:compileDebugKotlin`)

## 3. Goal-relative adherence sub-scores (day-nutrition-score curves)

- [x] 3.1 Implement the four asymmetric sub-score curves in `MealStatsCalculator.kt` against targets: calories (U: full [0.95,1.05], 0 at 1.25 over / 0.65 under), protein (100 at r≥1, →0 at r=0.5), carbs (full [0.8,1.2], 0 at 0.4/1.8), fat (full [0.8,1.2], 0 at 0.4/1.6); all clamp 0–100
- [x] 3.2 Replace the old fixed-threshold `normalizeCalories/Protein/Carbs/Fat`; remove the 0g-carbs/0g-fat=ideal behavior
- [x] 3.3 Unit tests per curve incl. edge cases (over vs under asymmetry: r=1.10→75 vs r=0.90→≈83; 0g carbs/fat→0; protein r=0.75→50, r=1.5→100)

## 4. Activity-adjusted calorie target (day-nutrition-score + energy-balance)

- [x] 4.1 Compute `calorieTarget = calorieGoal + 0.5 × dayActivityBurned` in the score; do NOT add base burn or re-scale macro targets
- [x] 4.2 Thread the day's activity-burned total into `nutritionScore` from the daily header (`MealEntriesScreen.kt`) and the heatmap per-day loop (`MealStatsOverview.kt`)
- [x] 4.3 Unit test: goal 2400 + 600 burned ⇒ target 2700 (calorie sub-score 100 at 2700 consumed); no activity ⇒ target unchanged

## 5. Quality penalty multiplier (day-nutrition-score)

- [x] 5.1 Compute limits (satFat `0.10×calorieTarget/9`, sugar `0.10×calorieTarget/4`, salt fixed 5g), per-nutrient overage `clamp((actual−limit)/limit,0,1)`, penalty `0.10×each` (cap 0.30), `multiplier = 1−penalty`
- [x] 5.2 Compose final score `base × multiplier`, clamped 0–100
- [x] 5.3 Unit tests: within limits ⇒ ×1.0; sugar 120g/60g ⇒ ×0.90 (base 100→90); all three ≥2× ⇒ ×0.70 floor

## 6. Wire goals into score call sites

- [x] 6.1 Read goals (calorie goal + split → `MacroTargets`) from `ThemeSettingsStore` and pass into `nutritionScore` at both call sites (header + heatmap)
- [x] 6.2 Confirm defaults make the score live with zero configuration (2400 · 30/40/30)
- [x] 6.3 Re-verify `ScoreToColorTest` mapping still holds (green=best, unchanged)

## 7. Specs sync + verification

- [x] 7.1 Rewrite `MealStatsCalculatorTest.kt` for the new model (remove assertions tied to old curves, e.g. the 2600/180/0/0 = 100 case)
- [x] 7.2 Full `:app:compileDebugKotlin :app:testDebugUnitTest` green
- [x] 7.3 `:app:assembleDebug` succeeds
- [x] 7.4 Sync delta specs into `openspec/specs/` on completion (day-nutrition-score rewrite, energy-balance amendment, new nutrition-goals) via the archive/sync flow
- [ ] 7.5 On-device smoke test: set a goal + split, log a day with activity, confirm score reacts to goals, activity, and a high-salt/sugar meal
