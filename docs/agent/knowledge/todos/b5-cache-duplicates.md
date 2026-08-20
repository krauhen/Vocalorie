---
description: The meal cache retains duplicate items, likely because a time or date value leaks into the cache key.
tags: [knowledge, todos, defect, meal-caching]
---

# B5: Cache keeps duplicate items

**Status:** captured
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
