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

All six captured defects were investigated on 2026-08-20 — B1–B5 against the source and the live
device database, B6 against the source alone — and all six are now promoted, so this directory holds
no defect files. Each investigation, with its file:line root causes and measured evidence, lives in
its change's `proposal.md`; git holds the original backlog files, and in B5's case the investigation
refutes the raw note it came from.

## Open defects

None; all six captured defects have been promoted.

## Promoted

Their backlog files are removed; the OpenSpec change is the live record.

- B1 (quantity vs amount) and B2 (scaling ignores quantity) → `openspec/changes/2026-08-20-fix-item-quantity-scaling`
- B3 (tips show too eagerly) and B4 ("Today" stale across midnight) → `openspec/changes/2026-08-20-gate-day-score-tips-and-live-day-rollover`
- B5 (cache keeps duplicate meals) → `openspec/changes/2026-08-20-fix-cache-key-normalization`
- B6 (visual clipping and mixed-language date labels) → `openspec/changes/2026-08-20-fix-entries-visual-clipping-and-locale`
