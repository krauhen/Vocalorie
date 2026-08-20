---
description: Open question whether a meal item needs both a quantity and an amount field, or whether the two are redundant.
tags: [backlog, bugs, data-model, meal-items]
---

# B1: Quantity and amount on an item — redundant?

**Status:** captured
**Source:** personal note, 2026-08-20
**Likely capability:** entry/meal item data model — `openspec/specs/meal-type-classification/spec.md` is the closest, but this may turn out to be a coding-guidance concern rather than a behaviour spec (guess, not a commitment)

## Raw note (verbatim)
> B1: Quantity and Amount in Item?

## What it means
A meal item currently carries two size-like fields and it is unclear whether both are needed or
what each one means. If they overlap, one is dead weight that the LLM, the UI and the scaling
logic all have to agree on — and B2 suggests they already do not.

## Open questions
- What does each field actually hold today, and which one does the LLM populate?
- Do both reach the UI, and does either affect nutrition math?
- If one is redundant: drop it, or redefine it (e.g. quantity = count, amount = mass/volume)?
- A schema change would need an additive Room migration plus a `BACKUP_SCHEMA_VERSION` bump.
