## Context

Today's `nutritionScore` (in `ui/entries/stats/MealStatsCalculator.kt`) is a weighted average of four fixed-threshold curves. Two of them (carbs, fat) peak at **0g**, so a zero-carb, zero-fat day scores 100 — nutritionally backwards. Thresholds are hardcoded population constants; there are no user goals anywhere in the app. Saturated fat, sugar, and salt are stored per meal (`NutritionEstimateDtos.kt`, `MealEntity.kt`) but unused by the score. Activity/burned calories exist (`ActivityEntity.kt`, base-burn setting in `ThemeSettingsStore.kt`) but the `energy-balance` spec explicitly forbids them from touching the score.

This design makes the score mean **"how well did today match your goals"**, grounded in mainstream guidance:
- **AMDR** (IOM/NASEM): carbs 45–65%, fat 20–35%, protein 10–35% of energy.
- **Protein** (ISSN/ACSM): firm floor ~0.8 g/kg, active band 1.2–2.2 g/kg; deficits harmful, excess largely benign.
- **WHO/AHA limits**: saturated fat ≤10% energy, free sugar <10% energy, salt <5 g/day (fixed).
- **Activity add-back**: exercise-calorie estimates over-report; a ~50% discount is the common practical compromise.

## Goals / Non-Goals

**Goals:**
- Goal-relative 0–100 score: 100 = landed on the user's calorie + macro targets.
- Works out-of-the-box via defaults (2400 kcal, 30% P / 40% C / 30% F), user-overridable in Settings.
- Asymmetric, direction-aware penalties grounded in the guidance above.
- Activity-adjusted calorie target (discounted 50%).
- Capped saturated-fat / sugar / salt quality penalty using data already stored.
- Keep the existing score→color mapping, heatmap, and daily-header display intact.

**Non-Goals:**
- Body-stats-derived targets (Mifflin-St Jeor, weight/height/age). Deferred; defaults + manual override only.
- Micronutrients, fibre, fruit/veg positive scoring (Nutri-Score/HEI style) — not stored.
- Per-day goal overrides; goals are global settings like base-burn.
- Changing confidence handling — it stays out of the score.
- Migrating the persisted schema — no new DB columns (sat-fat/sugar/salt already persist).

## Decisions

### D1 — Targets derived from calorie goal + macro %
Store `calorieGoal` (kcal) and three split percentages `pProtein/pCarbs/pFat` (sum 100). Derive gram targets with standard Atwater factors (protein/carbs 4 kcal/g, fat 9 kcal/g):
```
proteinTarget = calorieGoal * pProtein/100 / 4
carbsTarget   = calorieGoal * pCarbs/100   / 4
fatTarget     = calorieGoal * pFat/100     / 9
```
Defaults: `calorieGoal = 2400` (reuse the base-burn default so the number is familiar), split `30/40/30`. *Alternative considered:* explicit gram targets — rejected as more fields for the user; *body-stats derivation* — rejected as a bigger feature, deferred.

### D2 — Activity-adjusted calorie target (50% add-back)
```
calorieTarget = calorieGoal + 0.5 * sum(activity.caloriesBurnedKcal for the day)
```
Base-burn is **not** added — the calorie goal already expresses intended intake; only *logged activity* expands the allowance, discounted 50% because exercise-calorie estimates over-report. Macro gram targets are **not** re-scaled by activity (only the calorie component moves). *Alternative:* 100% add-back — rejected (erases intended deficit); *separate net-balance sub-score* — rejected as harder to reason about than shifting the target.

### D3 — Asymmetric adherence sub-scores (each 0–100)
Let `r = actual / target`. Piecewise-linear, clamped to [0,100].

- **Calories** — asymmetric U about `calorieTarget`. Full-credit band `r ∈ [0.95, 1.05]`. Over side falls to 0 at `r = 1.25` (steep: surplus opposes the goal). Under side falls to 0 at `r = 0.65` (gentle: mild deficit usually intended; deep deficit still punished at the tail).
- **Protein** — firm floor, soft ceiling. `r ≥ 1.0 → 100` (overshoot not penalized within realistic range). Under side falls steeply to 0 at `r = 0.5`.
- **Carbs** — flexible energy. Full band `r ∈ [0.8, 1.2]`, falloff to 0 at `r = 0.4` (under) and `r = 1.8` (over).
- **Fat** — full band `r ∈ [0.8, 1.2]`, falloff to 0 at `r = 0.4` (under) and `r = 1.6` (over, slightly steeper — calorie-dense).

*Alternative:* symmetric deviation or a single tolerance band — rejected; the user chose asymmetric, and it matches the science (protein floor, calorie surplus vs deficit).

### D4 — Base weights
```
base = 0.40*cal + 0.30*protein + 0.15*carbs + 0.15*fat   (weights sum 1.0)
```
Calories dominate (energy balance is the primary lever), protein next (body composition), carbs/fat least (flexible within AMDR). Keeps the *ordering* of the old weights (cal > protein > carbs ≈ fat) but on sane, goal-relative curves.

### D5 — Quality penalty: adherence base × capped multiplier
Reference limits scale with the calorie target (salt is fixed):
```
satFatLimit = 0.10 * calorieTarget / 9      // WHO ≤10% energy  (~27g @2400)
sugarLimit  = 0.10 * calorieTarget / 4      // WHO <10% energy  (~60g @2400)
saltLimit   = 5.0 g                          // WHO fixed daily
```
Per nutrient, overage `o = clamp((actual - limit)/limit, 0, 1)` (fully saturated at 2× the limit). Combine with equal shares:
```
penalty    = 0.10*o_satFat + 0.10*o_sugar + 0.10*o_salt   // max 0.30
multiplier = 1 - penalty                                   // ∈ [0.70, 1.0]
score      = base * multiplier                             // clamp 0–100
```
Quality can dock at most 30% of the base — it modifies, never dominates (the user chose "base × capped penalty" over a flat weighted sum or weakest-link). *Alternative:* fold quality into the weighted sum — rejected (one bad salt day would tank an otherwise perfect day).

### D6 — Data plumbing
Extend `DailyNutritionTotals` (`ui/entries/stats/MealStats.kt`) with `saturatedFatG`, `sugarG`, `saltG`; update both aggregators (`buildDailyTotals` and `List<SavedMeal>.toDailyNutritionTotals()`). `nutritionScore` gains parameters for the day's activity-burned total and the user's goals (calorie goal + split), passed from `MealEntriesScreen.kt` (header) and `MealStatsOverview.kt` (heatmap — already iterates days and can sum that day's activities). Goals read from `ThemeSettingsStore.kt` (new keys, defaults baked in).

## Risks / Trade-offs

- **Salt penalty bites almost every day** (global intake ~9–11 g vs 5 g limit) → the 30% cap and equal 10% share keep one nutrient from dominating; salt estimates from the LLM are also noisy, so a hard limit is acceptable as a gentle nudge, not a cliff.
- **Noisy LLM macro/salt estimates feed the score** → tolerance bands (±5% calories, ±20% carbs/fat) absorb small estimation error; confidence deliberately stays out so we don't double-penalize uncertainty.
- **Spec reversal on activity** (`energy-balance`) → the balance/burned *header* figures are untouched; only the score's calorie target now moves. Amend the one requirement, keep the rest.
- **Behavior change is visible** — existing days will re-score (e.g. a former "0-carb = 100" day now scores lower). Acceptable: the old scores were wrong. No persisted score to migrate (score is computed on read).
- **Defaults may not fit every user** → all four numbers (goal + 3 split %) are editable in Settings; the split is validated to sum to 100.

## Open Questions

- Exact Settings UI for the macro split (three linked % fields that must sum to 100, vs two free + one derived). Leaning: two editable, third auto-derived and shown read-only, to guarantee sum = 100.
- Whether to surface the derived gram targets in the daily header as a "target vs actual" hint (nice-to-have, not required for the score).
