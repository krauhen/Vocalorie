---
description: Deferred Vocalorie defects; entries here are a record, not authorization to implement.
tags: [backlog, bugs]
---

# Bugs

Known defects live here, one file per item. A file in this directory is a record, not a decision
and not authorization to implement.

Lifecycle: captured here → promoted to an `openspec/changes/<id>/` proposal when chosen → the
file marked `Status: promoted → openspec/changes/<id>` or deleted, since git holds the history.
Each item names a best-guess target capability spec; the guess is a starting point for the
proposal, not a commitment.

B3–B5 were investigated on 2026-08-20 against the source and the live device database, B6 against
the source alone; each file
carries an `## Investigation` section with file:line root causes and measured evidence. Read that
section before proposing — it replaces the guesswork in the raw notes, and in B5's case refutes it.

## Open defects

None; all six captured defects have been promoted.

## Promoted

- B1 (quantity vs amount) and B2 (scaling ignores quantity) → `openspec/changes/2026-08-20-fix-item-quantity-scaling`; the backlog files are deleted, git holds their investigations
- [B3 — tips show too eagerly and unfiltered](b3-tips-shown-when-not-needed.md) and
  [B4 — "Today" is stale across midnight](b4-today-stale-across-midnight.md) → `openspec/changes/2026-08-20-gate-day-score-tips-and-live-day-rollover`
- [B5 — cache keeps duplicate meals](b5-cache-duplicates.md) → `openspec/changes/2026-08-20-fix-cache-key-normalization`
- [B6 — visual clipping and mixed-language date labels](b6-visual-clipping-and-locale.md) → `openspec/changes/2026-08-20-fix-entries-visual-clipping-and-locale`
