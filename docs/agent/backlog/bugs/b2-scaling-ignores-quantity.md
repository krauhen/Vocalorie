---
description: Scaling an entry does not scale its quantity, so a scaled meal reports an inconsistent size.
tags: [backlog, bugs, defect, scaling]
---

# B2: Scaling does not scale quantity

**Status:** investigated
**Source:** personal note, 2026-08-20
**Likely capability:** `openspec/specs/energy-balance/spec.md` plus whichever spec owns entry scaling (guess, not a commitment)

## Raw note (verbatim)
> B2: Scaling does not scale Quantity.

## What it means
Observed: scaling an entry adjusts the nutrition values but leaves the quantity untouched.
Expected: after scaling by a factor, the displayed quantity describes the scaled portion — a
2× scaled "1 slice" should not still read "1 slice". Closely tied to B1: if quantity and amount
overlap, it may be that only one of them is being scaled.

## Open questions
- Which fields does the scale operation currently multiply?
- Is quantity always numeric, or can it be free text the LLM produced (which cannot be scaled)?
- Should a non-numeric quantity be scaled, cleared, or annotated with the factor?

## Investigation (2026-08-20)
Root cause: `model/MealDraft.kt:86-99`. `withItemsScaledByFactor` enumerates exactly the fields it
scales — `amountGml` plus the seven nutrition fields — and `item.copy` carries `quantity` (and
`name`) through untouched.

Every scaling entry point funnels through that one function:
- `withItemsScaledByPortion` (`MealDraft.kt:66-69`)
- `withItemsScaledByPortionFromBaseline` (`MealDraft.kt:71-78`), used by the portion buttons at
  `ui/components/MealEditor.kt:184-190`
- cached-meal reuse scaling (`data/MealMappers.kt:376-379`)

So it is a display-string desync, not a math error: totals stay correct because they read
`amountGml` (`MealDraft.kt:55-64`). **No Room migration involved** — `quantity` lives in
`itemsJson`, not a column.

### Measured on the live device DB (550 meals)
- 541 items carry an `amountGml`; 321 of those have a numerically parseable `quantity`.
- **72 of those 321 are desynced**, each meal showing one uniform ratio — the scaling signature:
  - meal 588 — `150 g` vs `112.15`, `200 g` vs `149.53`, `60 g` vs `44.86` (factor ≈ 0.748)
  - meal 570 — `40 ml` vs `16.0`, `75 ml` vs `30.0`, `25 ml` vs `10.0` (factor 0.4)
  - meal 578 — `50 g` vs `40.0` (factor 0.8)
- The remaining **220 items have a non-numeric `quantity`** and cannot be scaled arithmetically
  at all.

## Blocking decision (see B1)
`quantity` is unconstrained free text, so "multiply it" is not implementable for ~40% of real
items — `scaledEditableNumber` already returns non-numeric input unchanged (`MealDraft.kt:104-107`).
The proposal must first settle what `quantity` means, then make scaling honour that contract.
Existing desynced rows are historical data: decide whether to re-derive them or leave them.
