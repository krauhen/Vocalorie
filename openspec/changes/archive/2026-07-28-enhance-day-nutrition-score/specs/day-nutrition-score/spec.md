## MODIFIED Requirements

### Requirement: Weighted day nutrition score
The system SHALL compute a single 0–100 nutrition score for any calendar day that has at least one logged meal, as a **goal-adherence** score of the form `score = base × qualityMultiplier`, clamped to 0–100. The `base` SHALL be a weighted average of four per-metric adherence sub-scores (each 0–100) measured against the user's targets from the `nutrition-goals` capability, with weights **calories 0.40, protein 0.30, carbs 0.15, fat 0.15** (sum 1.0). The `qualityMultiplier` SHALL be the capped saturated-fat/sugar/salt penalty defined below. This aggregation is implemented in `app/src/main/java/com/example/vocalorie/ui/entries/stats/MealStatsCalculator.kt`; `DailyNutritionTotals` and its aggregators SHALL be extended to sum saturated fat, sugar, and salt per day alongside calories/protein/carbs/fat.

#### Scenario: On-target day scores near 100
- **WHEN** a day's totals hit the derived targets (e.g. 2400 kcal, 180g protein, 240g carbs, 80g fat at the default goal) with no activity and saturated fat, sugar, and salt all under their limits
- **THEN** the computed day score is 100 (every sub-metric at its peak and no quality penalty)

#### Scenario: Day with no logged meals is not scored
- **WHEN** a calendar day has zero logged meals
- **THEN** the system does not compute a numeric score for that day and instead treats it as a distinct "no data" state

#### Scenario: Score is goal-relative
- **WHEN** two users log identical meals but have different calorie/macro goals configured
- **THEN** each day's score is computed against that user's own targets, so the same food can score differently

### Requirement: Per-metric adherence curves
The system SHALL normalize each metric to a 0–100 adherence sub-score using asymmetric, goal-relative curves. Let `r = actual / target` for each metric (the calorie target is the activity-adjusted target defined below; macro targets are the derived gram targets from `nutrition-goals`). All sub-scores clamp to 0–100.

- **Calories** — asymmetric U about the calorie target: full credit for `r ∈ [0.95, 1.05]`; on the over side (`r > 1.05`) linear falloff to 0 at `r = 1.25`; on the under side (`r < 0.95`) linear falloff to 0 at `r = 0.65`. Overshoot is penalized more steeply than a mild undershoot, while an excessive deficit is still driven toward 0.
- **Protein** — firm floor, soft ceiling: `r ≥ 1.0` scores 100 (overshoot not penalized); `r < 1.0` falls linearly to 0 at `r = 0.5`.
- **Carbs** — full credit for `r ∈ [0.8, 1.2]`; linear falloff to 0 at `r = 0.4` (under) and `r = 1.8` (over).
- **Fat** — full credit for `r ∈ [0.8, 1.2]`; linear falloff to 0 at `r = 0.4` (under) and `r = 1.6` (over).

#### Scenario: Calorie overshoot penalized more than undershoot
- **WHEN** a day is 10% over the calorie target (`r = 1.10`) versus another day 10% under (`r = 0.90`)
- **THEN** the over day's calorie sub-score (75) is lower than the under day's (≈83)

#### Scenario: Zero carbs and zero fat no longer score 100
- **WHEN** a day has 0g carbs and 0g fat against non-zero targets
- **THEN** the carbs and fat sub-scores are 0 (the previous "0g is ideal" behavior is removed)

#### Scenario: Under-protein penalized steeply
- **WHEN** a day's protein is 75% of the protein target (`r = 0.75`)
- **THEN** the protein sub-score is 50, reflecting the firm protein floor

#### Scenario: Over-protein is not penalized
- **WHEN** a day's protein is 150% of the protein target (`r = 1.5`)
- **THEN** the protein sub-score is 100

## ADDED Requirements

### Requirement: Activity-adjusted calorie target
The system SHALL compute the calorie sub-score against an **activity-adjusted calorie target**, defined as `calorieGoal + 0.5 × (sum of that day's logged activity calories burned)`. Logged activity raises the day's allowed intake at a 50% discount (exercise-calorie estimates over-report); the "base calories burned" setting SHALL NOT be added to this target, and macro gram targets SHALL NOT be re-scaled by activity.

#### Scenario: Activity raises the calorie target
- **WHEN** the calorie goal is 2400 and the day's activities burned 600 kcal
- **THEN** the calorie target used for scoring is 2700 kcal (2400 + 0.5 × 600), so consuming 2700 kcal scores the calorie sub-score at 100

#### Scenario: No activity leaves the target at the goal
- **WHEN** a day has no logged activities
- **THEN** the calorie target equals the calorie goal unchanged

### Requirement: Nutritional-quality penalty for saturated fat, sugar, and salt
The system SHALL apply a capped multiplicative quality penalty to the base score using the day's total saturated fat, free sugar, and salt against reference limits: saturated fat `0.10 × calorieTarget / 9` g (WHO ≤10% energy), sugar `0.10 × calorieTarget / 4` g (WHO <10% energy), and salt a fixed `5 g/day` (WHO). For each nutrient the overage `o = clamp((actual − limit)/limit, 0, 1)` (saturating at twice the limit); the total penalty is `0.10×o_satFat + 0.10×o_sugar + 0.10×o_salt` (maximum 0.30), and `qualityMultiplier = 1 − penalty` (range 0.70–1.0). Quality therefore modifies but never dominates the score.

#### Scenario: Within all limits applies no penalty
- **WHEN** a day's saturated fat, sugar, and salt are all at or below their limits
- **THEN** the quality multiplier is 1.0 and the base score is unchanged

#### Scenario: One nutrient at twice its limit docks its full share
- **WHEN** a day's sugar is 120g against a 60g limit (2×) and saturated fat and salt are within limits
- **THEN** the quality multiplier is 0.90 (a 10% dock), so a base of 100 yields a score of 90

#### Scenario: Quality penalty is capped
- **WHEN** all three of saturated fat, sugar, and salt are at or beyond twice their limits
- **THEN** the quality multiplier bottoms out at 0.70 (never lower), so quality cannot dominate the score
