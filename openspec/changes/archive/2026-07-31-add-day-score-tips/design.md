## Context

`nutritionScore` (`ui/entries/stats/MealStatsCalculator.kt`) already computes everything a tip needs:

```kotlin
val calorieTarget = goals.calorieGoalKcal + 0.5 * activityBurnedKcal
val caloriesScore = calorieAdherence(totals.caloriesKcal, calorieTarget)   // weight 0.40
val proteinScore  = proteinAdherence(totals.proteinG, targets.proteinG)     // weight 0.30
val carbsScore    = carbsAdherence(totals.carbsG, targets.carbsG)           // weight 0.15
val fatScore      = fatAdherence(totals.fatG, targets.fatG)                 // weight 0.15
val base = ...
return (base * qualityMultiplier(totals, calorieTarget)).coerceIn(0.0, 100.0)
```

All five helpers are `internal` in the same package. The weighted loss of each component — `weight × (100 − subScore)` — is exactly "how many score points this shortfall costs you", which is the definition of leverage we want to rank by. Nothing needs to be recomputed or re-thresholded; the tips layer is a *presentation* of an existing decomposition.

The header that would host the strip is `SelectableStatsHeader` in `ui/entries/MealEntriesStatsHeader.kt`; its inputs (including `dayScore`) are derived inline in `MealEntriesScreen.kt` via `remember`.

## Goals / Non-Goals

**Goals:**
- One to three blunt, actionable tips for *today*, ranked by score leverage, in a strip small enough for a stats header.
- Tip and score can never disagree: both read the same sub-scores.
- Rule text is always available and always correct offline; the LLM only changes wording.
- Deterministic, JVM-testable ranking and gating; no Android or network dependency in the logic.

**Non-Goals:** notifications, past-day/trend tips, per-meal tips, tip categories outside the score's components, any change to the score itself. Reasons are in `proposal.md`.

## Decisions

### D1 — Tips derived in the state holder, not the composable

**Decision.** A pure `ui/entries/stats/DayScoreTips.kt` does ranking, wording, gating and LLM-reply validation. `MealCaptureViewModel` calls it and publishes the result on `MealCaptureUiState`; `SelectableStatsHeader` renders what it is handed.

**Alternative rejected.** Keep derivation in `MealEntriesScreen` next to the existing `selectedDayScore` `remember` block, and pass a rewrite lambda down from the state holder.

**Why it lost.** The rewording path needs a repository and a scope, and `agentic/guidance/CODING.md` plus ADR-6/ADR-8 in `docs/arc42.md` forbid reaching across the state-holder boundary from a composable. A callback down would leave the tip list owned by the composable and its reworded replacement owned by the state holder — two owners of one value, with the crossfade animation reading whichever won the race.

**Consequence to call out.** This puts the first *derived aggregate* on `MealCaptureUiState`, which so far carries only raw lists (`savedMeals`, `savedActivities`) and settings (`nutritionGoals`, `baseCaloriesBurned`, `kcalPerStep`). It is a deliberate, narrow precedent: only the tip list moves, not the score, totals, burn or balance. If later work wants those in the state holder too, that is a separate change with its own ADR note.

### D2 — Rank by weighted score loss, reusing the existing sub-score functions

**Decision.** `rank = weight × (100 − subScore)` using the score's own weights (calories 0.40, protein 0.30, carbs 0.15, fat 0.15). A component at 100 emits no tip. Quality nutrients rank by their own dock (`0.10 × overage × 100`, so at most 10), which places them below any meaningful macro gap — matching the score's "quality modifies but never dominates" rule. The activity tip ranks at half the calorie tip's value, so it always sits directly below it.

**Alternative rejected.** A fixed priority list (calories → protein → carbs → fat → quality).

**Why it lost.** A fixed order shows "eat more protein" for a 5g shortfall while a 600 kcal overshoot waits behind it. Weighted loss self-orders and needs no tuning: the tip you see is the one that costs the most points.

### D3 — LLM rewords, never generates

**Decision.** The refresh action sends the already-computed rule tips and the day's numbers, and asks for the same list reworded, same order, each 5–10 words. The reply is accepted only if the count matches and every entry is 5–10 whitespace-separated words. Rejection is wholesale, not per tip, and silent.

**Alternative rejected.** Let the model produce tips directly from the day's totals and goals.

**Why it lost.** Generated tips can contradict the score ("eat more carbs" on a carb-heavy day), can invent categories the app has no data for, and cannot be unit-tested — `src/test` is offline by rule (`openspec/config.yaml`), so a generative path would have no deterministic coverage at all. Rewording keeps the ground truth in Kotlin and reduces the model to a stylist whose output is cheap to validate.

**Alternative also rejected.** Per-tip fallback, keeping the valid rewrites and the rule text for the rest.

**Why it lost.** A mixed strip reads as inconsistent voice mid-rotation, and one prompt failure usually means all entries are malformed the same way. Wholesale rejection is one rule to reason about.

### D4 — Refresh-only trigger, no automatic call

**Decision.** No call unless the refresh icon is tapped. The icon is hidden when no OpenAI key is stored.

**Alternative rejected.** Debounced automatic rewording whenever the day's totals change materially, with an in-memory cache keyed by the rule set.

**Why it lost.** The strip lives in a scrolled, frequently-recomposed surface, and the app is BYOK — an implicit call means the user pays and waits for wording they did not ask to change. Explicit refresh also makes the failure case invisible instead of confusing: nothing was requested, so nothing appears to fail.

### D5 — Late-day cutoff at 21:00 local

**Decision.** At or after 21:00 local, suppress the tips that ask the user to eat more (calories under, protein under, carbs under, fat under). Over-budget, quality and activity tips remain. If suppression empties the list, the strip hides.

**Alternative rejected.** A cutoff derived from the last logged meal's timestamp, or from the fraction of the calorie budget consumed.

**Why it lost.** Both are guesses about intent dressed as inference: a late snack does not mean another meal is planned, and a low budget fraction at 22:00 still does not make "add a protein-heavy meal" good advice. A fixed hour is predictable, explainable in one sentence, and trivially testable with an injected clock.

### D6 — Rotation interval as a persisted setting, `0` = off

**Decision.** New `NutritionSettingsStore` key (`tip_rotation_seconds`, default `5`), accepted range 2–60, plus `0` meaning "no rotation". Read through `ThemeSettingsSnapshot` / `ThemeSettingsRepository.currentSnapshot()`, surfaced on `MealCaptureUiState`, edited via a `SettingsEvent.SaveTipRotationSeconds(input: String)` handled in `MealCaptureViewModel` with the same trim/`toIntOrNull`/range-check/`settingsMessage` shape as the existing `saveBaseCaloriesBurned(input: String)` (`MealCaptureViewModel.kt:566`), and rendered as a numeric `OutlinedTextField` + Save button following the `baseCaloriesBurned` field in `ui/settings/SettingsScreen.kt` (~lines 182–196).

**Alternative rejected.** Hardcode 5 seconds.

**Why it lost.** Rotation speed is exactly the kind of preference that is wrong for someone: 5s is too fast to finish reading a tip for one user and too slow to notice for another, and there is no defensible default that also serves people who want no motion at all. `0` doubles as the reduced-motion escape hatch without adding a second setting.

**Why not a lower bound of 0 < n < 2.** A sub-2-second crossfade in a stats header is unreadable and would look like a rendering bug; the range is clamped rather than trusted.

### D7 — Rotation state is view state, tips are the model

**Decision.** The rotating index and the expanded flag live in the composable (`remember`); only the ranked tip list crosses the state-holder boundary. Expanding stops rotation; collapsing resumes it. Rotation also stops when the day is not today or the list has fewer than two entries.

**Alternative rejected.** Drive the rotation index from the state holder so it survives configuration changes.

**Why it lost.** It would put a 5-second ticker in the state holder purely to preserve which of three tips was on screen — state nobody misses when it resets — and would make every unit test of the state holder time-dependent. Tests assert against the ranked list; the index is untested by design.

## Tip catalogue

Twelve entries, one per shortfall the score can express. Each trigger is a sub-score already computed by `MealStatsCalculator.kt`; `r` is `actual / target` against the activity-adjusted calorie target or the derived macro gram target. Wording lives here rather than in the spec so it stays revisable — the spec pins only the two entries that carry the tone decision (rows 3 and 12).

| # | Shortfall | Trigger | Tip |
|---|---|---|---|
| 1 | Calories under | `r < 0.95` | You're under budget — eat a bit more today. |
| 2 | Calories over | `1.05 < r < 1.25` | Ease off — you're over your calorie budget today. |
| 3 | Calories far over | `r ≥ 1.25` (adherence 0) | You're well over budget — consider stopping for today. |
| 4 | Protein under | `r < 1.0` | Protein is short — add a protein-heavy meal. |
| 5 | Carbs under | `r < 0.8` | Carbs are low — add rice, bread or fruit. |
| 6 | Carbs over | `r > 1.2` | Carbs are high — cut back on starchy sides. |
| 7 | Fat under | `r < 0.8` | Fat is low — add nuts, oil or dairy. |
| 8 | Fat over | `r > 1.2` | Fat is high — trim oils and fatty cuts. |
| 9 | Saturated fat over | over `0.10 × calorieTarget / 9` g | Saturated fat is high — choose leaner options today. |
| 10 | Sugar over | over `0.10 × calorieTarget / 4` g | Sugar is high — skip sweets and sugary drinks. |
| 11 | Salt over | over 5 g | Salt is high — go easy on salty foods. |
| 12 | No activity while over budget | intake over calorie target and no activity logged | Over budget — log some sport to offset it. |

Every string is 7–8 words, inside the 5–10 bound with room for rewording. Rows 1, 4, 5 and 7 are the eat-more set suppressed after 21:00 (D5).

**There is deliberately no "protein over" entry.** `proteinAdherence` returns 100 for `r ≥ 1.0` — the score does not penalize protein overshoot, so the catalogue must not advise against it. The absence is the catalogue tracking the curves rather than inventing symmetry.

Rows 9–11 rank by their own penalty share (max 10.0), below any material macro gap, so on most days they never surface. They are kept because they are the only tips covering data the app already stores but never acts on; if they prove to be dead weight in use, removing them is a one-row change with no effect on ranking.

## Risks / Trade-offs

- **Motion in a stats header.** A crossfade in a surface covered by the `visual-baseline` capability risks flaky visual comparisons. Mitigation: tests assert the ranked tip list and the strip's presence, never the visible index; `0` disables motion entirely for anyone capturing baselines.
- **Copy quality is now a spec surface.** Pinning exact tip wording in scenarios makes the copy a contract; reworking a tip's phrasing later means a spec edit. Accepted deliberately — only two strings are pinned (the blunt over-budget tip and the activity tip), because those two carry the tone decision.
- **Tone.** The over-budget tip is the bluntest string in the app. It is capped at "consider stopping for today" wording — no fasting instruction, no medical framing, no streak or shame language. This is a single-user personal app, which is why blunt is acceptable at all.
- **`internal` reuse across the tips module.** `DayScoreTips.kt` sits in the same package as `MealStatsCalculator.kt` specifically so the `internal` sub-score functions stay `internal` rather than being widened to `public` for a caller elsewhere.

## Open Questions

- None blocking. Two settled by decision rather than evidence, cheap to revisit after living with them: the 21:00 cutoff (D5) and the 2–60 second accepted range (D6).
