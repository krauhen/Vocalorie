---
description: Scaling an entry does not scale its quantity, so a scaled meal reports an inconsistent size.
tags: [knowledge, todos, defect, scaling]
---

# B2: Scaling does not scale quantity

**Status:** captured
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
