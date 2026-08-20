## Context

A meal item carries two size-like fields on `FoodItemEstimate` (`model/NutritionEstimateDtos.kt:38-53`) and its editable mirror `EditableFoodItem` (`model/MealDraft.kt:26-39`):

- `quantity: String` — free text, filled by the LLM, with no `@LLMDescription` and no mention in the system prompt beyond "Use German for all quantity descriptions" (`ai/KoogNutritionAgent.kt:291`).
- `amountGml: Double?` — numeric, the sole input to every calculation. Totals sum it (`MealDraft.kt:55-64`); the item cache derives its per-100 basis from it (`data/MealMappers.kt:191-209`); `CachedItemEntity` has no quantity column at all (`data/CachedItemEntity.kt:14-26`).

The B1 investigation asked whether the two are redundant and answered no: they hold different kinds of value, and `quantity` is the only field that can say `"2 eggs"`. What it lacks is a contract.

Scaling is a single function:

```kotlin
fun EditableMealDraft.withItemsScaledByFactor(factor: BigDecimal): EditableMealDraft = copy(
    items = items.map { item ->
        item.copy(
            amountGml = item.amountGml.scaledEditableNumber(factor),
            caloriesKcal = item.caloriesKcal.scaledEditableNumber(factor),
            // ... six more nutrition fields
        )
    },
).withTotalsSummedFromItems()
```

It enumerates what it scales, so `item.copy` carries `quantity` (and `name`) through untouched — the whole of B2's root cause, at `MealDraft.kt:86-99`. All three entry points funnel through it, so there is exactly one place to fix.

Two measurements from the live device DB (550 meals) drive every decision below. Of 541 items with an `amountGml`: **321 have a numerically-parseable `quantity`, 72 of them already desynced**; the remaining **220 carry text no arithmetic can touch**.

## Goals / Non-Goals

**Goals:**
- A written contract for `quantity`: display label, never a calculation input.
- After scaling, the label agrees with the amount beside it for the numeric-prefix majority.
- The non-numeric minority ends with a truthful label rather than a stale one.
- Deterministic, pure, JVM-testable logic with no Android dependency.
- One fix point, no call-site changes, no schema change.

**Non-Goals:** prompt or `@LLMDescription` changes, migrating the 72 desynced historical rows, a unit field on `amountGml`, written-out number parsing, any change to `name`, totals, or the cache's per-100 basis. Reasons are in `proposal.md`.

## Decisions

### D1 — `quantity` is a display label; `amountGml` is the sole numeric basis

**Decision.** Specify `quantity` as a human-readable label for an item's portion, explicitly barred from participating in any nutrition or amount calculation. `amountGml` remains the only numeric input, exactly as the code already behaves.

**Alternative rejected.** Drop `quantity` and render a label from `amountGml` alone.

**Why it lost.** 220 of 541 real on-device items carry descriptive text — `"2 eggs"`, `"1 Scheibe"`, `"eine Handvoll"` — that a bare `g`/`ml` number cannot express. Dropping the field would flatten `"2 eggs"` to `"120 g"` and lose the only human-legible portion description the app has. It would also be a JSON-shape change to `itemsJson` with a `BACKUP_SCHEMA_VERSION` decision attached, for a strictly worse UI.

**Alternative also rejected.** Constrain the LLM to emit a parseable `count + unit` via a new `@LLMDescription` and a prompt clause.

**Why it lost.** It fixes nothing on its own — the 550 meals already in the database keep their free text, so the scaling rule still needs a non-numeric path. It buys stricter future input at the cost of touching a prompt whose wording is pinned by `ai/NutritionPromptContractTest.kt`, and it can be added later on top of this contract without changing it.

**Consequence to call out.** Writing the bar down means any future feature that wants a count (per-piece nutrition, "how many slices did you eat") must add its own numeric field rather than parsing this label. That is the intended trade: the label stays free text precisely because nothing depends on its shape.

### D2 — Scaling multiplies the leading number and preserves the trailing text

**Decision.** Parse a leading number out of `quantity`, multiply it by the factor, and re-emit it followed by the original remainder of the string verbatim. `"2 eggs"` × 2 → `"4 eggs"`; `"500 ml"` × 1.5 → `"750 ml"`.

**Alternative rejected.** Scale only fully-numeric quantities, leaving anything else untouched — which is what `scaledEditableNumber` (`MealDraft.kt:104-107`) already does when handed non-numeric input.

**Why it lost.** That is the current behaviour and the defect. B1's "40% non-numeric" figure means *not a bare number*, not *no number*: `"2 eggs"`, `"1 Scheibe"`, `"500 ml"` all start with a digit. A leading-number parse therefore reaches nearly all of them, where a strict full-string parse reaches none.

**Alternative also rejected.** Append the factor as an annotation — `"1 Scheibe (×1.5)"`.

**Why it lost.** It is an audit trail, not a label. It compounds across two successive scalings, and the portion chips at `ui/components/MealEditor.kt:184-190` rescale from a baseline rather than from the current value, so the annotation would not even reflect how the item got there.

### D3 — Scaled numbers round to one decimal, trailing `.0` trimmed

**Decision.** `RoundingMode.HALF_UP` at scale 1, then `BigDecimal.toEditableNumberText()` (`model/EditableNumberText.kt:40`, `stripTrailingZeros().toPlainString()`). So `"2 eggs"` × 2 → `"4 eggs"`, and `"1 Scheibe"` × 1.5 → `"1.5 Scheibe"`.

**Alternative rejected.** Round to a whole number.

**Why it lost.** The portion factors that actually occur are fractional — the measured desyncs show 0.748, 0.4, 0.8 — so whole-number rounding sends `"1 Scheibe"` × 1.5 back to `"1 Scheibe"`, reproducing the exact bug this change fixes.

**Alternative also rejected.** Keep the factor's full precision, as the numeric fields do.

**Why it lost.** `portionScaleFactor` divides at scale 12 (`MealDraft.kt:80-84`), so full precision yields `"1.4999999999985 Scheibe"`. The numeric fields carry that precision deliberately, to keep totals exact; a label has no such duty and is read by a human.

**Consequence to call out.** The label is deliberately less precise than `amountGml`. A one-decimal label beside a twelve-decimal amount is not a desync — it is a rounded presentation of the same scaling, and the spec says the label is never a calculation input precisely so that this rounding can never leak into a total.

### D4 — No leading number: derive the label from the scaled `amountGml`

**Decision.** When `quantity` has no leading number and the scaled `amountGml` is present and positive, replace the label with that amount plus a unit — `"eine Handvoll"` on a 100 g item × 2 → `"200 g"`.

**Alternative rejected.** Leave the label untouched.

**Why it lost.** It keeps a label the app knows to be wrong. `"eine Handvoll"` beside a doubled amount is the same lie as `"1 Scheibe"` beside a halved one, only unparseable — and it is 220 of 541 items, the larger half of the problem.

**Alternative also rejected.** Clear the label.

**Why it lost.** An empty quantity line is a regression in the editor: the item loses its only descriptive portion text and shows a blank field where it used to show something. A derived amount is less expressive than `"eine Handvoll"` but it is true, and it is what the app would show if `quantity` had never been populated.

### D5 — The derived label's unit is inferred from the original text

**Decision.** Emit `ml` when the original `quantity` mentions `ml` or `l`, and `g` otherwise.

**Alternative rejected.** Always emit `g`.

**Why it lost.** `"ein Schluck"` on a 200 ml drink would read `"200 g"` — a plainly wrong unit on a beverage, in an app whose real data is full of `ml` quantities.

**Alternative also rejected.** Add a unit field to `amountGml` and read it.

**Why it lost.** `amountGml` is a single `Double?` with no unit anywhere in the model (`model/NutritionEstimateDtos.kt:38-53`); g and ml are genuinely not distinguished. Adding the field means changing the DTO, the editable mirror, the item cache, the editor UI and the `itemsJson` shape — a data-model change with a `BACKUP_SCHEMA_VERSION` decision — to serve one fallback branch.

**Consequence to call out.** This is an approximation and the spec states it as one. The app does not know the unit; it guesses from the text it is replacing. The failure mode is a wrong unit on a label that was already stale, so the guess never makes an item worse than leaving it alone would.

### D6 — No leading number and no positive amount: leave `quantity` unchanged

**Decision.** If both inputs are missing, return the original string.

**Alternative rejected.** Clear it, on the grounds that it is known-stale.

**Why it lost.** Nothing is known. Without a positive `amountGml` there is no scaled quantity to describe, so blanking removes information and adds none. Leaving it is the honest no-op, and it keeps the function total: every input maps to a defined output.

### D7 — One pure function, added to the single existing scaling site

**Decision.** A pure `String.scaledQuantityLabel(factor: BigDecimal, scaledAmountGml: String): String` in `MealDraft.kt`, beside `scaledEditableNumber` (`:104-107`) as its text-preserving sibling, called from the `item.copy` block in `withItemsScaledByFactor` (`:86-99`).

**Alternative rejected.** Handle it in the UI, formatting a scaled label at render time in `ui/components/MealEditor.kt`.

**Why it lost.** The label is persisted — it round-trips through `itemsJson` (`data/MealMappers.kt:270-283`) and through cached-meal reuse (`:370-381`), neither of which passes through the editor. A render-time fix would leave the stored value desynced and would not fix cache reuse at all. `docs/agent/guidance/coding.md` also keeps derivation out of composables.

**Alternative also rejected.** Extend `scaledEditableNumber` to handle trailing text.

**Why it lost.** That helper is used for nine numeric fields whose contract is "exact, full precision, empty stays empty". Teaching it about trailing words and one-decimal rounding would put label formatting on the path that produces the totals — the one coupling D1 exists to forbid.

**Consequence to call out.** The function takes the *already-scaled* `amountGml` string rather than the original plus a factor, so the derived label can never disagree with the amount field written in the same `copy`. Ordering inside the `item.copy` block therefore matters: scale the amount into a local first, then pass it in.

## Risks / Trade-offs

- **A wrong inferred unit on a derived label.** Mitigated by D5's `ml`/`l` check and bounded by the fact that the alternative was a label already known to be stale. Not detectable at runtime, since the model has no unit to check against.
- **A label whose trailing text no longer agrees with its number.** `"1 Scheibe"` × 1.5 → `"1.5 Scheibe"` is grammatically odd in German, and `"4 eggs"` from `"2 eggs"` is fine only because English plurals happen to survive. Accepted: the number is the part that carries the portion, and inflecting nouns per locale is out of scope by design.
- **A one-decimal label beside a twelve-decimal amount.** Deliberate (D3). The risk is a user reading the label as exact; the spec's "never a calculation input" rule keeps the imprecision out of every total.
- **The 72 historical desyncs stay desynced.** They will read differently from newly-scaled items, which is mildly inconsistent. Rewriting them would mean inventing the factor that produced them.
- **Scaling twice compounds rounding.** `× 1.5` then `× 1.5` on `"1 Scheibe"` gives `2.3`, not `2.25`. The portion chips rescale from a baseline (`withItemsScaledByPortionFromBaseline`, `MealDraft.kt:71-78`) rather than from the current value, so the common path does not compound; manual re-scaling can.

## Open Questions

None. D2, D4 and D5 close the question B1's investigation left open — what `quantity`'s contract should be — and D6 closes the "scale, clear, or annotate" question from B2.
