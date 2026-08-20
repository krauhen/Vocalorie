---
description: Findings from the F2 audit — how the app's goals, scoring and activity handling compare with the user's stated nutrition and training target profile.
tags: [backlog, features, audit, findings, nutrition-goals]
---

# F2 findings: the app against the target profile

**Audit run:** 2026-08-20
**Item:** [F2 — audit the code against the target profile](f2-target-profile-audit.md)
**Deliverable:** this findings list. No code was changed and no proposal was written; every gap below is
captured as its own backlog item rather than built inside the audit.

## Verdicts

| Element | Target | Verdict | Evidence | Note |
|---|---|---|---|---|
| Protein | 130–180 g/day | represented wrongly | `model/NutritionGoals.kt:19,27`; `openspec/specs/nutrition-goals/spec.md:21,33` | A single derived point target (`cal × p% / 4`; the 2400 kcal / 30% default lands at 180 g, the top of the range), not a range. |
| Fat | 60–80 g/day | represented wrongly | `model/NutritionGoals.kt:21,27`; settings input at `:52-53` | Single point target (default 80 g, the top of the range); the fat *percentage* is itself computed as `100 − protein − carbs` at the settings input. |
| Carbs | remainder of the calorie budget | represented wrongly | `model/NutritionGoals.kt:20,52-53` | Inverted: carbs is a user-entered percentage and **fat** is the derived remainder. |
| Fiber | 25–40 g/day | absent | no `fiber`/`fibre` hits in `openspec/specs/` or `app/src/`; nutrient fields at `model/NutritionEstimateDtos.kt:43-49`, `ui/entries/stats/MealStats.kt:17-25` | Not in the LLM estimate DTO, day totals, goals or score. |
| Food quality | veg, fruit, lean protein, whole grains, legumes prioritised | represented wrongly | `ui/entries/stats/MealStatsCalculator.kt:199-220`; `openspec/specs/day-nutrition-score/spec.md:60` | "Quality" is only a saturated-fat/sugar/salt penalty; no food-group concept anywhere. |
| Calorie balance | 300–500 kcal deficit | represented wrongly | `ui/entries/MealTimeWindows.kt:228-229`; `ui/entries/MealEntriesScreen.kt:111-113`; `ui/entries/stats/MealStatsCalculator.kt:139,155-163` | Balance is computed and shown, but there is no *target* deficit: any negative balance reads as good, and the score targets intake ≈ calorie goal, not goal minus deficit. |
| Resistance training | 3–4 sessions/week | represented wrongly | `model/ActivityModels.kt:7-22`; `openspec/specs/activity-logging/spec.md:33` | `KETTLEBELL`/`GYM` are loggable, but nothing counts sessions per week; activities feed kcal only. |
| Steps | 8,000–12,000/day | represented wrongly | `model/ActivityModels.kt:15,60,64`; `ui/components/ActivityEditor.kt:107-118`; `ui/settings/SettingsScreen.kt:230-237` | Step count is captured and converted to kcal, but there is no step goal and steps enter the score only as burned calories. |
| Easy cardio | 2–3 sessions/week | represented wrongly | `model/ActivityModels.kt:8-14` | `RUNNING`/`BIKE`/`SWIMMING` exist; intensity and weekly session counts do not. |
| Weight change | 0.3–0.7 kg/week | absent | searched `weight`, `bodyweight`, `weightKg`, `kg` across `app/src/main` and `openspec/specs/` — only `FontWeight`, score weights, and unit-parsing regexes (`data/MealMappers.kt:390,418`) | No body-weight entity, no input path, no trend. |

Paths are relative to `app/src/main/java/com/example/vocalorie/` unless they start with `openspec/`.

## The three open questions, answered

**Are targets ranges or point values?** Point values only. `NutritionGoals` holds one
`calorieGoalKcal: Int` plus three integer percentages (`model/NutritionGoals.kt:10-14`), and
`macroTargets()` returns single `Double` gram values (`:16-23`, `:79-83`). Ranges exist only as
tolerance bands inside the scoring curves — calories at full credit for a ratio in `[0.95, 1.05]`
(`ui/entries/stats/MealStatsCalculator.kt:155-163`), carbs and fat in `[0.8, 1.2]` (`:173-192`),
protein at 100 for a ratio at or above `1.0` (`:166-171`). The profile's ranges are therefore not
stored, not displayed, and not user-settable.

**Is carbohydrate a derived remainder?** No, and the derivation runs the other way. The user enters
a calorie goal, a protein percentage and a carbs percentage; **fat** is computed as
`100 - protein - carbs` (`model/NutritionGoals.kt:53`), while carbs gets its own gram target
`cal × carbs% / 4` (`:20`). `openspec/specs/nutrition-goals/spec.md:21,34` specifies this design, so
this is a divergence from the profile, not a bug against the spec.

**Any weekly or trend state? Are steps and weight tracked?** No weekly-aggregate or trend state
exists anywhere. All scoring and statistics are per calendar day
(`ui/entries/stats/MealStatsCalculator.kt:84-102`; `nutritionScore` takes one day's
`DailyNutritionTotals` at `:131-135`). The only `week` usage is a heatmap column label
(`ui/entries/stats/MealStatsOverview.kt:349-354`), not state. Steps **are** tracked, as an activity
type with a `stepsCount` field and a kcal-per-step conversion (`model/ActivityModels.kt:15,60,64`;
`ui/components/ActivityEditor.kt:107-118`), but with no goal attached. Body weight is **not** tracked
at all; it would need a new input path and store.

## Spec/source disagreements found on the way

These are findings in their own right, independent of the target profile:

- `openspec/specs/activity-logging/spec.md:33` mandates "exactly this set" of seven activity types,
  but the model has nine, including `STEPS` and `OTHER` (`model/ActivityModels.kt:7-22`) — and the
  same spec references `STEPS` at `:44`, contradicting itself.
- `openspec/specs/activity-logging/spec.md:22` lists the activity fields without `stepsCount`, which
  exists in the model (`model/ActivityModels.kt:60`) and in the editor UI.

## Gaps to capture as their own backlog items

Named only; none of these is designed here.

- Range-valued nutrition targets (min/max per macro) instead of point values.
- Carbohydrate as the derived remainder after protein and fat targets.
- Fiber capture in the estimate DTO, day totals, goals and score.
- A food-quality or food-group signal beyond the saturated-fat/sugar/salt penalty.
- A target calorie *deficit* range, distinct from the intake goal.
- Weekly training and cardio session goals, with per-week aggregation.
- A daily step goal (the step data already exists; the target does not).
- Body-weight logging and a weekly rate-of-change trend.
- Reconcile the `activity-logging` spec's type set and field list with the implemented model.

## What this means for other items

The first two gaps — ranges, and carbs as the remainder — reshape `NutritionGoals` and the scoring
curves that read it. F3 depends on that answer (see `f3-streak-gamification.md`), because a
qualifying streak day defined against goals is defined against whichever goal model survives.
