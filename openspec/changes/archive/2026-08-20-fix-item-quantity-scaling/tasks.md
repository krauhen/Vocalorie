## 1. The pure quantity-label rule (meal-item-quantity logic)

- [x] 1.1 Add `private fun String.scaledQuantityLabel(factor: BigDecimal, scaledAmountGml: String): String` to `app/src/main/java/com/example/vocalorie/model/MealDraft.kt`, directly beside `scaledEditableNumber` (`MealDraft.kt:104-107`) as its text-preserving sibling. Pure Kotlin only — no Android types — so it is JVM-testable per `docs/agent/guidance/testing.md`.
- [x] 1.2 Split a leading number off the trimmed label: an optional sign, digits, an optional `.` or `,` decimal part. Parse it by reusing `String.toEditableBigDecimalOrNull()` (`MealDraft.kt:118-121`), which already maps `,` to `.`, so `"1,5 l"` parses. Keep the untouched remainder of the string, including its leading separator, verbatim.
- [x] 1.3 On a parsed leading number: multiply by `factor`, `setScale(1, RoundingMode.HALF_UP)`, then render with `BigDecimal.toEditableNumberText()` (`model/EditableNumberText.kt:40`) so `stripTrailingZeros()` drops the trailing `.0`. Concatenate with the preserved remainder — `"2 eggs"` × 2 → `"4 eggs"`, `"1 Scheibe"` × 1.5 → `"1.5 Scheibe"`.
- [x] 1.4 On no leading number: if `scaledAmountGml` parses to a value greater than zero via `toEditableBigDecimalOrNull()`, return that amount's text plus a space plus the inferred unit — `ml` when the original label mentions `ml` or `l` as a standalone token (case-insensitive), `g` otherwise. Match tokens, not substrings, so "Milch" and "Salat" do not read as `l`.
- [x] 1.5 On no leading number and no positive `scaledAmountGml`: return the original string unchanged. Empty input with no amount stays empty; the function is total, with no throwing path.
- [x] 1.6 Add `app/src/test/java/com/example/vocalorie/model/MealDraftQuantityLabelTest.kt` beside `MealDraftTotalsTest.kt`, covering the rule table: `"2 eggs"` × 2 → `"4 eggs"`; `"500 ml"` × 1.5 → `"750 ml"`; `"1 Scheibe"` × 1.5 → `"1.5 Scheibe"`; `"1,5 l"` × 2 → `"3 l"`; `"1 Scheibe"` × 0.748 → `"0.7 Scheibe"`; `"eine Handvoll"` with scaled amount `200` → `"200 g"`; `"einige ml"` with scaled amount `300` → `"300 ml"`; `"ein Schluck Milch"` with scaled amount `300` → `"300 g"`; `"eine Handvoll"` with an empty amount → unchanged; `"eine Handvoll"` with amount `0` → unchanged; empty label with amount `100` → `"100 g"`; empty label with an empty amount → empty.
- [x] 1.7 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 2. Wire the rule into scaling (meal-item-quantity behaviour)

- [x] 2.1 In `withItemsScaledByFactor` (`app/src/main/java/com/example/vocalorie/model/MealDraft.kt:86-99`), compute the scaled amount into a local inside the `items.map` block — `val scaledAmount = item.amountGml.scaledEditableNumber(factor)` — and use that local for the `amountGml` assignment, so exactly one value is written.
- [x] 2.2 Add `quantity = item.quantity.scaledQuantityLabel(factor, scaledAmount)` to the same `item.copy` block (`MealDraft.kt:88-97`), passing the already-scaled amount from 2.1 so the derived label can never disagree with the amount written beside it (design D7). Leave `name` and the seven nutrition assignments exactly as they are.
- [x] 2.3 Confirm no call site needs changing: all three entry points already funnel through this function — `withItemsScaledByPortion` (`MealDraft.kt:66-69`), `withItemsScaledByPortionFromBaseline` (`MealDraft.kt:71-78`, used by the portion chips at `ui/components/MealEditor.kt:184-190`), and `toPreparedCachedDraft` (`data/MealMappers.kt:370-381`). Read each and record that no edit was needed.
- [x] 2.4 Add draft-level tests to the new test file: a draft scaled by `withItemsScaledByFactor` moves each item's `quantity` alongside its `amountGml`; a `withItemsScaledByPortionFromBaseline` call with recipe 4 / ate 1 on an item reading `"400 g"` yields `"100 g"`; an item with an unparseable label and a positive amount gains a derived label consistent with the scaled amount in the same result.
- [x] 2.5 Confirm totals are unaffected: run the existing `app/src/test/java/com/example/vocalorie/model/MealDraftTotalsTest.kt` unchanged — `withTotalsSummedFromItems` (`MealDraft.kt:55-64`) reads `amountGml` and the macros only, never `quantity`.
- [x] 2.6 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 3. Cached-meal reuse coverage (meal-caching)

- [x] 3.1 Read `toPreparedCachedDraft` (`app/src/main/java/com/example/vocalorie/data/MealMappers.kt:370-381`) and confirm the requested-amount factor path reaches `withItemsScaledByFactor` unchanged, so labels scale on reuse with no edit to the mapper.
- [x] 3.2 Add or extend a JVM test for cached reuse scaling: a `SavedMeal` whose item reads `"200 g"` at `200`, reused for a request of `"Buttermilch 100g"`, yields a prepared draft item reading `"100 g"` at `100`; and one whose item reads `"eine Handvoll"` at `100`, reused for `50 g`, yields `"50 g"`. Place it beside the existing mapper tests under `app/src/test/java/com/example/vocalorie/data/` if one exists there, otherwise in the new model test file, driving `withItemsScaledByFactor` with the factor the mapper computes.
- [x] 3.3 Confirm the item cache is untouched: `data/MealMappers.kt:191-209` derives the per-100 basis from `amountGml`, and `data/CachedItemEntity.kt:14-26` has no quantity column — no change, no migration, and therefore no `BACKUP_SCHEMA_VERSION` move.
- [x] 3.4 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 4. Specs and backlog (documentation)

- [x] 4.1 Add `openspec/specs/meal-item-quantity/spec.md` from this change's delta, with a `# meal-item-quantity` title and a `## Purpose` block, followed by the requirements under `## Requirements`.
- [x] 4.2 Apply this change's `meal-caching` delta to `openspec/specs/meal-caching/spec.md`, replacing the "Whole-meal cache matches only on exact normalized query" requirement (`:18`) and the "Item-name cache stores nutrition per 100 g/ml and scales on use" requirement (`:37`) with the modified text, scenarios included.
- [x] 4.3 Confirm the backlog already reflects the promotion — `docs/agent/backlog/bugs/b1-item-quantity-vs-amount.md` and `b2-scaling-ignores-quantity.md` carry `Status: promoted → openspec/changes/2026-08-20-fix-item-quantity-scaling`, and `docs/agent/backlog/bugs/README.md` lists them under `## Promoted` rather than `## Open defects`. This was done when the proposal was written; no edit expected.
- [x] 4.4 Verify: `openspec validate 2026-08-20-fix-item-quantity-scaling --strict` passes, and the main specs still parse (`openspec list --specs`).

## 5. On-device confirmation

- [x] 5.1 Install: `./gradlew :app:installDebug --no-daemon`
- [x] 5.2 Open a meal holding an item with a descriptive quantity such as `"1 Scheibe"`, tap a portion chip in `ui/components/MealEditor.kt`'s portion row (`:184-190`), and confirm the quantity line moves together with the amount line — and that tapping back to the original portion returns the original label.
- [ ] 5.3 Log a repeat meal that hits the whole-meal cache with a different amount in the query (for example a cached "Buttermilch" reused as "Buttermilch 100g") and confirm the reused draft's quantity labels match its amounts.
- [x] 5.4 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`
