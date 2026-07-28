## Why

The daily nutrition score is currently a hardcoded 4-metric weighted average (`calories×10, protein×3, carbs×2, fat×1`) with fixed population thresholds and — worse — curves that reward **0g carbs and 0g fat** as the ideal day. It is not personal (no goals exist), it is nutritionally backwards on two of four metrics, and it ignores data the app already stores (saturated fat, sugar, salt) and the user's own activity. We want a score that means "how well did today match *your* goals," grounded in mainstream nutrition guidance (WHO / EFSA / IOM-AMDR / AHA / ISSN).

## What Changes

- **Goal-relative scoring.** Replace fixed thresholds with adherence to a user-configurable **daily calorie goal + macro split (%)**. Macro gram targets are derived from the calorie goal and split. A day scores 100 when it lands on all targets.
- **Sensible defaults so it works out-of-the-box.** Ship a default calorie goal (reusing the existing 2400 base-burn value) and a balanced **30% protein / 40% carbs / 30% fat** split. Score is live immediately; user overrides in Settings.
- **Asymmetric per-target penalties.** Direction matters, grounded in guidance: exceeding calories penalized more steeply than a mild undershoot (but excessive deficit penalized again — asymmetric "U"); under-protein penalized more than over-protein (firm floor, soft ceiling); carbs/fat penalized for deviation in either direction from their derived targets. **BREAKING** (spec-level): removes the current "0g carbs/fat = ideal" curves.
- **Activity raises the calorie target.** The day's calorie target = calorie goal + a **discounted 50%** of that day's activity-burned calories. Active days let you eat more without penalty. **BREAKING** (spec-level): reverses the current rule that activity never affects the score.
- **Nutritional-quality penalty.** Use the currently-unused per-meal saturated fat, sugar, and salt as a capped multiplicative penalty against WHO/AHA reference limits (sat-fat ≤10% energy, free sugar <10% energy, salt <5g/day fixed).
- **Composite = adherence base × capped quality penalty.** Weighted calorie+macro adherence forms a 0–100 base; quality overages apply a bounded multiplier so quality modifies but never dominates.
- **Confidence stays out of the score** (estimate uncertainty is not diet quality).

## Capabilities

### New Capabilities
- `nutrition-goals`: user-configurable daily calorie goal and protein/carbs/fat percentage split, persisted in settings with defaults (2400 kcal, 30/40/30), plus the derivation of per-macro gram targets. Provides the target inputs the score reads.

### Modified Capabilities
- `day-nutrition-score`: replace the fixed-threshold weighted average and the 0g-ideal carbs/fat curves with goal-relative, asymmetric adherence sub-scores against the calorie/macro targets, an activity-adjusted calorie target, and a capped saturated-fat/sugar/salt quality penalty on the composite.
- `energy-balance`: amend the "score and heatmap unaffected by activities" requirement so that activity-burned calories raise the day's calorie target (discounted 50%); the balance/burned header figures are unchanged.

## Impact

- **Scoring**: `ui/entries/stats/MealStatsCalculator.kt` — new goal-relative sub-score curves, activity-adjusted calorie target, quality-penalty multiplier; `DailyNutritionTotals` (`ui/entries/stats/MealStats.kt`) extended with saturated fat / sugar / salt, and both aggregators updated (`buildDailyTotals`, `List<SavedMeal>.toDailyNutritionTotals()` in `ui/entries/MealEntriesScreen.kt`).
- **Score inputs**: score computation now needs the day's activity-burned total and the user's goals; `MealEntriesScreen.kt` header + `MealStatsOverview.kt` heatmap must pass activities + goals into the score (heatmap already has per-day activity access to check).
- **Settings/persistence**: `settings/ThemeSettingsStore.kt` (new calorie-goal + macro-split keys, defaults) and `settings/SettingsScreen.kt` (goal + split editing UI, following the existing numeric-setting pattern).
- **Tests**: `ui/entries/stats/MealStatsCalculatorTest.kt` (rewrite for new curves), `ScoreToColorTest.kt` (unchanged mapping, re-verify), new goal-derivation + quality-penalty tests.
- **Specs**: rewrite `openspec/specs/day-nutrition-score/spec.md`, amend `openspec/specs/energy-balance/spec.md`, add `openspec/specs/nutrition-goals/spec.md`.
- **Dependencies**: none new expected; confirm before adding.
