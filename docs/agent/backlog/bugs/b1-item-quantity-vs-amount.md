---
description: Open question whether a meal item needs both a quantity and an amount field, or whether the two are redundant.
tags: [backlog, bugs, data-model, meal-items]
---

# B1: Quantity and amount on an item — redundant?

**Status:** investigated
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

## Investigation (2026-08-20)
Answered: **not redundant, keep both** — they hold different kinds of value.

- `quantity: String` (free text) and `amountGml: Double?` (numeric) both sit on `FoodItemEstimate`
  (`model/NutritionEstimateDtos.kt:39-49`) and its editable mirror `EditableFoodItem`
  (`model/MealDraft.kt:26-39`).
- The LLM fills both. `quantity` carries **no** `@LLMDescription` and the system prompt never names
  the field — only "Use German for all quantity descriptions" (`ai/KoogNutritionAgent.kt:291`).
  So its form is unconstrained: on-device data holds `"500 ml"`, `"2 eggs"`, `"1 Scheibe"`.
- Both reach the UI: read-only at `ui/components/MealEditor.kt:285-286`, editable "Quantity" field
  at `:321-327`.
- Nutrition math uses `amountGml` only. Totals sum `amountGml` plus macros (`MealDraft.kt:55-64`);
  the item cache derives per-100 values from `amountGml` (`data/MealMappers.kt:191-209`) and
  `CachedItemEntity` has no quantity column (`data/CachedItemEntity.kt:14-26`).
- **No Room migration needed to change it:** `quantity` lives inside `itemsJson` for both `meals`
  and `cached_meals` (`data/MealMappers.kt:270-283`), not in a column. A JSON-shape change would
  still need a `BACKUP_SCHEMA_VERSION` decision.

Measured on the live device DB (550 meals): of 541 items carrying an `amountGml`, **220 have a
non-numeric `quantity`** ("2 eggs", "1 Scheibe"). Any rule that treats quantity as a scalable
number therefore fails for ~40% of real data. This is the decision B2 hangs on.

## Resolved direction
Redefine rather than drop: `amountGml` stays the single source for math, `quantity` becomes a
display label with a documented contract. Whether that contract is "parseable count + unit",
"derived from amountGml", or "free text that scaling blanks" is the open decision for the proposal.
