## Why

Scaling a meal leaves the quantity line lying. Tap a portion chip to halve a meal and every number moves — the amount, the calories, the macros — except the one string the user actually reads first: `"1 Scheibe"` still says `"1 Scheibe"` next to an amount that is now `44.86`. On the live device database, **72 of 321 numerically-parseable item quantities are already desynced this way**, each meal showing one uniform ratio — the scaling signature (`docs/agent/backlog/bugs/b2-scaling-ignores-quantity.md`).

The totals are not wrong. Nutrition math reads `amountGml` only (`model/MealDraft.kt:55-64`), so a scaled meal still sums correctly. What is wrong is that the item row shows two size-like values that disagree, and the user has no way to tell which one the app believes.

The reason it was never fixed is that `quantity` has no contract at all. It carries no `@LLMDescription` and the system prompt never names the field (`ai/KoogNutritionAgent.kt:291` only says "Use German for all quantity descriptions"), so its form is whatever the model emits: `"500 ml"`, `"2 eggs"`, `"1 Scheibe"`, `"eine Handvoll"`. `220 of 541` real items hold text `amountGml` cannot express, which is why "just multiply it" was never implementable and why the defect sat open.

This change writes the contract down — `quantity` is a display label, `amountGml` is the only numeric basis — and then makes scaling honour it, so the label can no longer drift from the amount beside it.

## What Changes

- **`quantity` is defined as a display label.** It SHALL NOT participate in any nutrition or amount calculation. This is a specification of what already happens in code, not a behaviour change, and it is what makes the rest of this change safe: nothing downstream can start reading the label as a number.
- **Scaling scales the leading number and keeps the words.** `"2 eggs"` × 2 → `"4 eggs"`; `"500 ml"` × 1.5 → `"750 ml"`. The trailing descriptive text is preserved verbatim, which covers the numeric-prefix majority of real data without needing to understand German portion nouns.
- **Scaled numbers render to at most one decimal, trailing `.0` trimmed.** `"1 Scheibe"` × 1.5 → `"1.5 Scheibe"`, not `"1.4999999 Scheibe"` and not a whole-number `"1 Scheibe"` that would still read as unscaled.
- **A label with no leading number is replaced by one derived from the scaled amount.** `"eine Handvoll"` on a 100 g item × 2 → `"200 g"`. The item keeps a truthful label instead of a stale one; the unit is inferred from the original text (see Impact).
- **An underivable label is left alone.** No leading number *and* no positive `amountGml` means there is nothing to derive from, so `quantity` stays untouched rather than being blanked.
- **Cached-meal reuse gets the same treatment.** Reusing a cached meal scaled to a requested amount already funnels through the one scaling function, so the labels move there too — and `meal-caching`'s two reuse-scaling requirements are amended to say so explicitly.
- **Existing desynced rows are left as they are.** No historical data is rewritten (see Non-goals).

## Capabilities

### New Capabilities

- `meal-item-quantity`: what a meal item's `quantity` label means, that it is never a calculation input, and how it behaves when an item is scaled — the leading-number rule, the one-decimal format, the derived-label fallback, and the leave-alone case.

### Modified Capabilities

- `meal-caching`: amend the "Whole-meal cache matches only on exact normalized query" and "Item-name cache stores nutrition per 100 g/ml and scales on use" requirements so that a reused entry scaled to the requested amount has its item quantity labels scaled by the same rule. Matching, normalization, the per-100 basis and the nutrition math are unchanged.

## Impact

- **One fix point.** `withItemsScaledByFactor` (`model/MealDraft.kt:86-99`) enumerates the fields it scales; `quantity` joins that `item.copy` block. All three scaling entry points already funnel through it — `withItemsScaledByPortion` (`:66-69`), `withItemsScaledByPortionFromBaseline` (`:71-78`, driven by the portion chips at `ui/components/MealEditor.kt:184-190`), and cached-meal reuse via `toPreparedCachedDraft` (`data/MealMappers.kt:370-381`). No call site changes.
- **One new pure function.** `String.scaledQuantityLabel(factor, scaledAmountGml)` in `MealDraft.kt`, sitting beside its existing numeric sibling `scaledEditableNumber` (`:104-107`). No Android types, so it is directly JVM-unit-testable as the testing guidance requires for extracted pure functions.
- **Reuses existing helpers.** `String.toEditableBigDecimalOrNull()` (`MealDraft.kt:118-121`) already handles comma decimals, so `"1,5 l"` parses; `BigDecimal.toEditableNumberText()` (`model/EditableNumberText.kt:40`) is `stripTrailingZeros().toPlainString()`, which trims the trailing `.0` for free once the value is rounded to one decimal with `RoundingMode.HALF_UP`.
- **A documented approximation: the unit is inferred, not known.** `FoodItemEstimate.amountGml` is a single `Double?` with no unit field anywhere in the model (`model/NutritionEstimateDtos.kt:38-53`) — g and ml are not distinguished. The derived-label fallback therefore reads the original `quantity` text: `ml` when it mentions ml or l, `g` otherwise. The app does not know the unit; it guesses from the label it is replacing, and a wrong guess produces a wrong unit on a label that was already unscaled.
- **No Room change.** `quantity` lives inside `itemsJson` for both `meals` and `cached_meals` (`data/MealMappers.kt:270-283`), not in a column. No schema bump, therefore no `BACKUP_SCHEMA_VERSION` move and no `connectedDebugAndroidTest` requirement.
- **Tests**: a new JVM test beside `app/src/test/java/com/example/vocalorie/model/MealDraftTotalsTest.kt` covering the rule table; existing totals tests untouched, since totals still read `amountGml`.
- **Specs**: add `openspec/specs/meal-item-quantity/spec.md`, amend `openspec/specs/meal-caching/spec.md`.
- **Backlog**: B1 and B2 close as promoted to this change.

## Non-goals

- **No LLM prompt or `@LLMDescription` change.** The label is already usable text; constraining the model's output format is a separate, riskier change whose wording is pinned by `ai/NutritionPromptContractTest.kt`, and this fix does not need it.
- **No migration of the 72 already-desynced rows.** They are historical records of meals the user actually logged; their totals are correct, and re-deriving a label from an amount whose original factor is unknown would be a guess written into history.
- **No unit field on `amountGml`.** Adding one would touch the DTO, the item cache, the editor and the JSON shape — a data-model change with its own migration decision, for a fallback path that affects the minority of labels with no leading number.
- **No parsing of written-out numbers ("zwei", "eine").** Every extra word is a locale-specific guess, and the derived-label fallback already gives those items a truthful label.
- **No change to `name`.** Scaling adjusts how much of a thing there is, never what the thing is.
- **No change to totals, nutrition math, or the item cache's per-100 basis.** They read `amountGml` and are already correct; this change deliberately keeps the label out of every calculation.
- **Nothing about B3–B6.** They touch unrelated capabilities and stay on the backlog for their own proposals.
