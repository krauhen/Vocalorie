---
description: The meal cache retains duplicate meals; no date leak — the normalized key keeps counting words and typos.
tags: [backlog, bugs, defect, meal-caching]
---

# B5: Cache keeps duplicate meals

**Status:** promoted → openspec/changes/2026-08-20-fix-cache-key-normalization
**Source:** personal note, 2026-08-20
**Likely capability:** `openspec/specs/meal-caching/spec.md` (guess, not a commitment)

## Raw note (verbatim)
> B5: Caching does keep duplicate items. Propably a time or date value influences it.

## What it means
Observed: the same food accumulates repeated cache entries instead of matching an existing one.
Suspected cause: a timestamp or date participates in the cache key or the equality check, so an
otherwise identical item never matches a previous one. Effect: the cache stops saving LLM calls
and any "recent items" surface fills with near-duplicates.

## Open questions
- What exactly is the cache key today, and does it include a time/date field?
- Is the duplication in the key, in a normalisation step (case, whitespace, units), or in an
  upsert that should be a conflict-replace?
- What should count as "the same item" — name plus unit, name plus macros per unit?
- Do existing duplicate rows need a one-off cleanup, or is fixing the key enough?

## Investigation (2026-08-20)
**The suspected date/time leak is refuted, and the item/meal wording was wrong.**

No timestamp participates in any cache key or row. `CachedMealEntity` is keyed on `normalizedKey`
alone, explicitly "no id/createdAt" (`data/CachedMealEntity.kt:10, 14-28`); `CachedItemEntity` is
keyed on `normalizedName` (`data/CachedItemEntity.kt:15`); the migration SQL adds no time columns
(`VocalorieDatabase.java:120-146`); writes are `OnConflictStrategy.REPLACE` on the primary key
(`CacheDao.java:40-44`) from a single writer (`data/MealRepository.kt:58-67, 77-86`). One key can
therefore only ever hold one row.

### Measured on the live device DB
- `cached_items`: 280 rows, **0 duplicate display names**. The item cache is clean.
- `cached_meals`: 171 rows, **11 duplicated titles**. The duplication is in cached *meals*.

### Actual cause — key divergence in normalization (`data/MealMappers.kt:335-348`)
`normalizedKey` is a sorted bag of query words. Line 337 strips amount tokens with only
`\b\d+([.,]\d+)?\s*(g|ml|kg|l)\b` (pattern at `:420`), so three classes of noise survive into the
key. Real observed pairs:

- **Counting words** — `karotten zwei` vs `karotten vier`; `aprikose eine` vs `aprikosen vier`;
  `pflaumen rote zwei` vs `drei pflaumen rote`.
- **Count-prefixed amounts** the regex misses — `1x106g … sprotten`, `2x106g … sprotten`,
  `3x106g … sprotten` (three rows, one food).
- **Voice-transcription typos** — `grillgemüse` vs `grillgwmüse`; `brie` vs `briw`.

Also unstripped: the German unit words the prompt itself lists — EL, TL, Stück, Scheibe, Prise,
Portion, Glas (`ai/KoogNutritionAgent.kt:293`) — so "Buttermilch" and "ein Glas Buttermilch"
diverge. And spoken kcal hints land in the key verbatim (`102kcal 169kcal a einem esslöffel …`).
There is no diacritic or plural folding, so `Müsli`/`Muesli` and `Ei`/`Eier` are distinct keys.

## Open questions
- Should the key drop counting words and units entirely, given `amountGml` carries the real size,
  and scale a cache hit by portion instead (`MealMappers.kt:376-379` already does this)?
- Typos cannot be normalized away by rule — is a fuzzy match acceptable, or is this out of scope?
- Changing the rule changes existing primary-key values: re-key the 171 rows in a migration, or
  let stale rows become unreachable and prune them?
