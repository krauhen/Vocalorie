---
description: Day-score tips show for any imperfect score; they should appear only below score 50 and only the highest-impact ones.
tags: [backlog, bugs, defect, day-score-tips]
---

# B3: Tips show too eagerly and unfiltered

**Status:** captured
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
