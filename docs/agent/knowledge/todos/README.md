---
description: Deferred Vocalorie defects and audits; entries here are a record, not authorization to implement.
tags: [knowledge, todos]
---

# Todos

Known defects and pending audits live here, one file per item. A file in this directory is a
record, not a decision and not authorization to implement.

Lifecycle: captured here → promoted to an `openspec/changes/<id>/` proposal when chosen → the
file marked `Status: promoted → openspec/changes/<id>` or deleted, since git holds the history.
Each item names a best-guess target capability spec; the guess is a starting point for the
proposal, not a commitment.

## Open defects

- [B1 — quantity and amount on an item, redundant?](b1-item-quantity-vs-amount.md)
- [B2 — scaling does not scale quantity](b2-scaling-ignores-quantity.md)
- [B3 — tips show too eagerly and unfiltered](b3-tips-shown-when-not-needed.md)
- [B4 — "Today" is stale across midnight](b4-today-stale-across-midnight.md)
- [B5 — cache keeps duplicate items](b5-cache-duplicates.md)

## Open audits

- [F2 — audit the code against the target profile](f2-target-profile-audit.md)
