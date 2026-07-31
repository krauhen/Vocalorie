## Why

The daily nutrition header shows a bare `Score 63`. The user learns *where they stand* and nothing about *what to do about it* — they have to open the macro line, compare four numbers against targets they may not remember, and work out which gap actually matters. In practice they don't; the number becomes decoration.

The app already knows the answer. `nutritionScore` in `ui/entries/stats/MealStatsCalculator.kt` decomposes the day into four weighted adherence sub-scores (calories 0.40, protein 0.30, carbs 0.15, fat 0.15) times a saturated-fat/sugar/salt `qualityMultiplier`. Whichever sub-score contributes the largest weighted loss *is* the highest-leverage thing to fix today. That ranking is computed on every render and thrown away.

This change surfaces it: a compact tips strip in the daily nutrition section naming the 1–3 highest-leverage actions for today, in blunt second-person wording, derived from the score's own components — so the tip and the number can never disagree.

## What Changes

- **Rule-derived tips ranked by leverage.** A new pure module ranks shortfalls by `weight × (100 − subScore)` — reusing the existing `calorieAdherence` / `proteinAdherence` / `carbsAdherence` / `fatAdherence` / `qualityMultiplier` functions rather than re-deriving thresholds — and maps each shortfall to one blunt 5–10 word tip. A 20-point calorie gap therefore outranks a 20-point fat gap, matching how the score itself weights them.
- **An activity tip.** When the day is over its calorie budget and no activity is logged, one tip suggests logging sport. This is the only tip that reads activity data rather than nutrition totals.
- **Time-awareness.** "Eat more X" tips are suppressed after a late-day cutoff (21:00 local); over-budget and activity tips survive, because they are still actionable at 22:00 and "add a protein-heavy meal" is not.
- **Compact rotating presentation.** The top 3 tips rotate with a crossfade at a user-configured interval; tapping freezes rotation and expands the full ranked list (max 5). The strip is hidden entirely on a day with no logged meals, and on any day other than today.
- **Configurable rotation interval.** A new persisted setting, default 5 seconds, accepted range 2–60, with `0` meaning "no rotation — show the top tip only". Follows the existing numeric-setting pattern (`NutritionSettingsStore` key + default, `ThemeSettingsRepository` accessor, `SettingsEvent` + trim/parse/validate in the state holder, numeric field in `SettingsScreen`).
- **Optional LLM rewording, explicit only.** A refresh icon in the strip asks the model to reword the *existing* rule tips — same count, same order, same meaning, each 5–10 words. It never invents tips and never runs automatically. The rewritten set is accepted only if the count matches and every tip is 5–10 words; any other outcome (no key, network failure, wrong count, over-length reply) silently keeps the rule wording with no error surface. The icon is hidden when no OpenAI key is stored.
- **Tips computed in the state holder.** Derivation moves out of the composable into `MealCaptureViewModel` / `MealCaptureUiState`, because the LLM rewrite path needs a repository and a coroutine scope and a composable has neither.

## Capabilities

### New Capabilities

- `day-score-tips`: derivation, ranking, wording, time-gating, presentation, optional LLM rewording, and the configurable rotation interval for the actionable tips shown under the day nutrition score.

### Modified Capabilities

- `day-nutrition-score`: amend the "Day score shown in the heatmap and daily stats header" requirement so the daily header renders the tips strip beneath the score number for today. The score's own computation, curves, weights and colour scale are unchanged.

## Impact

- **New pure module**: `ui/entries/stats/DayScoreTips.kt` — ranking, catalogue, time-gating, LLM-reply validation. No Android dependencies, fully JVM-testable.
- **Widened visibility**: `calorieAdherence`, `proteinAdherence`, `carbsAdherence`, `fatAdherence`, `qualityMultiplier` in `MealStatsCalculator.kt` are already `internal` — reused as-is, not duplicated.
- **State holder**: `ui/capture/MealCaptureUiState.kt` gains the tip list, the rewording in-flight flag and the rotation interval; `ui/capture/MealCaptureViewModel.kt` gains tip derivation and the refresh handler. This is the first *aggregate* (rather than raw or settings) value in `MealCaptureUiState` — see design D1.
- **UI**: `ui/entries/MealEntriesStatsHeader.kt` (`SelectableStatsHeader` gains the strip below the score row); `ui/entries/MealEntriesScreen.kt` passes the tips through instead of deriving them.
- **AI**: a narrow rewording entry point alongside `ai/KoogNutritionAgent.kt`, deliberately not folded into the nutrition-estimate prompt, whose exact wording is pinned by `NutritionPromptContractTest`.
- **Settings**: `settings/NutritionSettingsStore.kt`, `data/repository/ThemeSettingsRepository.kt`, `ui/settings/SettingsUiState.kt`, `ui/settings/SettingsScreen.kt` for the rotation interval.
- **No Room change.** Every input already exists (`SavedMeal.totals`, `SavedActivity.caloriesBurnedKcal`, `NutritionGoals`). No schema bump, therefore no `BACKUP_SCHEMA_VERSION` move and no `connectedDebugAndroidTest` requirement.
- **Tests**: new `DayScoreTipsTest` (ranking order, all twelve catalogue strings, late-day gating, empty case, LLM-reply validation); `MealStatsCalculatorTest` untouched.
- **Specs**: add `openspec/specs/day-score-tips/spec.md`, amend `openspec/specs/day-nutrition-score/spec.md`.

## Non-goals

- **No notifications or reminders.** There is no scheduling infrastructure in the app, and a tip is a glanceable thing you read while looking at today's numbers — pushing it turns advice into nagging.
- **No tips for past days or multi-day trends.** A tip is an instruction for the rest of today; "you were short on protein last Tuesday" is not actionable, and retrospective wording would double the copy set for no benefit.
- **No per-meal tips.** The score is a per-day goal-adherence measure; a single meal has no target to be short of.
- **No toggle for the AI rewording.** The stored OpenAI key already gates it, and a tap is already explicit — a second switch would be a setting whose only job is to hide a button. The rotation interval is the one setting this change adds.
- **No automatic LLM calls.** The strip lives in a scrolled surface that recomposes constantly; an implicit call there would mean latency and cost on every day change.
- **No new tip categories beyond the score's own components.** Anything the score does not measure (hydration, meal timing, fibre) has no ground truth here and would be invented advice.
- **No change to the score, its curves, weights, or the score→colour scale.** This change only reads the decomposition that already exists.
