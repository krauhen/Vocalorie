---
description: Day-score tips show for any imperfect score; they should appear only below score 50 and only the highest-impact ones.
tags: [backlog, bugs, defect, day-score-tips]
---

# B3: Tips show too eagerly and unfiltered

**Status:** promoted → openspec/changes/2026-08-20-gate-day-score-tips-and-live-day-rollover
**Source:** personal note, 2026-08-20
**Likely capability:** `openspec/specs/day-score-tips/spec.md` (guess, not a commitment)

## Raw note (verbatim)
> B3: Hints/Tips always show until not perfect, show only below score 50 the ones that are of the most

## What it means
Observed: tips appear whenever the day score is anything less than perfect, and all applicable
tips are listed. Expected: tips appear only when the score is below 50, and only the ones with the
largest impact are shown — so the section reads as help when the day is genuinely off track,
rather than as permanent nagging.

## Open questions
- Is 50 a hard threshold, or should it be a range with fewer tips as the score rises?
- "The ones that are of the most" — most impact on the score, or most calories/macros off target?
- How many tips at most? One, or a small fixed cap?
- Note the score improves through the day, so tips would appear and vanish as meals are logged —
  acceptable, or should the threshold be evaluated against the day's projected total?

## Investigation (2026-08-20)
There is **no score-based gate anywhere** — the 50-point threshold does not exist yet. The only
conditions today:

- `ui/entries/stats/DayScoreTips.kt:79` — returns empty only when `!totals.hasData()`.
- `ui/capture/MealCaptureViewModel.kt:880-893` — tips computed only for `selectedDayOffset == 0`;
  every other day gets none.
- `ui/entries/MealEntriesStatsHeader.kt:167` — `if (tips.isEmpty()) return`. The tips path never
  reads `dayScore`; that value is used only for the score text at `:94-101`.

So any single band violation emits a tip even at score 95: calories outside 0.95–1.05, carbs or fat
outside 0.8–1.2, or any quality overage above 0.

Ordering already exists and is roughly what the note asks for: rank is `weight × (100 − subScore)`,
sorted descending (`DayScoreTips.kt:104-107`), with eat-more tips suppressed after 21:00
(`DayScoreTips.kt:45, 103-105`). The count cap is **UI-only** — `COLLAPSED_TIP_COUNT = 3` rotating
and `EXPANDED_TIP_COUNT = 5` (`MealEntriesStatsHeader.kt:149-150, 171, 187`); the pure function
returns everything.

Device check (2026-08-20 16:37, score 11) showed one tip, "Carbs are low — add rice, bread or
fruit." — correct behaviour at a low score, so the over-eager case was not reproduced visually.
It is proven in source instead: nothing gates on score.

## Remaining shape for the proposal
Two separate changes, not one: add a score gate (below 50) in the pure function, and decide whether
"only the highest-impact ones" means a cap inside `dayScoreTips` rather than in the header.

## Files
`DayScoreTips.kt`, `MealCaptureViewModel.withDayScoreTips`, `MealEntriesStatsHeader.kt`,
`openspec/specs/day-score-tips/spec.md`, tests `DayScoreTipsTest.kt` / `DayScoreTipsStateTest.kt`.
