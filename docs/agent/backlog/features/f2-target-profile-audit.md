---
description: Audit whether the app's goals, scoring and activity handling match the user's stated nutrition and training target profile.
tags: [backlog, features, audit, nutrition-goals]
---

# F2: Audit the code against the target profile

**Status:** audited — findings in [f2-findings.md](f2-findings.md)
**Source:** personal note, 2026-08-20
**Likely capability:** `openspec/specs/nutrition-goals/spec.md`, `openspec/specs/energy-balance/spec.md`, `openspec/specs/activity-logging/spec.md` (guess, not a commitment)

## Raw note (verbatim)
> F2: Check if the code now fits to this target description:
> "Eat 130–180 g protein/day, 60–80 g fat/day, and fill the rest with carbs; prioritize vegetables, fruit, lean protein, whole grains, legumes, and 25–40 g fiber/day. Keep a 300–500 kcal deficit, lift weights 3–4 times weekly, walk 8,000–12,000 steps daily, add 2–3 easy cardio sessions weekly, and aim to lose 0.3–0.7 kg per week."

## What it means
This is an audit, not a feature. The deliverable is a findings list: for each element of the target
profile, whether the app currently represents it, represents it wrongly, or ignores it. Anything
that needs building is then captured as its own request rather than done inside the audit.

The profile broken into checkable elements:

| Element | Target |
|---|---|
| Protein | 130–180 g/day |
| Fat | 60–80 g/day |
| Carbs | remainder of the calorie budget |
| Fiber | 25–40 g/day |
| Food quality | vegetables, fruit, lean protein, whole grains, legumes prioritised |
| Calorie balance | 300–500 kcal deficit |
| Resistance training | 3–4 sessions/week |
| Steps | 8,000–12,000/day |
| Easy cardio | 2–3 sessions/week |
| Weight change | 0.3–0.7 kg/week |

## Open questions
- Are targets ranges or single numbers in the current model? Several of these are ranges, and the
  day score may assume point targets.
- Carbs "fill the rest" is derived, not fixed — does the code treat it that way?
- Weekly elements (training, cardio) and weight trend are not daily values; does the app hold any
  weekly or trend concept at all, or is everything per-day?
- Are steps and weight even tracked today, or would that need new input paths?
