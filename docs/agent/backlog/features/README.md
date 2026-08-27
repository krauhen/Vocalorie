---
description: Rough Vocalorie feature asks and pending audits captured before they are promoted to an OpenSpec change.
tags: [backlog, features]
---

# Features

Rough feature asks and pending audits live here, one file per item. A file in this directory is a
captured idea and a record — not a decision and not authorization to implement.

Lifecycle: captured here → promoted to an `openspec/changes/<id>/` proposal when chosen → the
backlog file marked `Status: promoted → openspec/changes/<id>` or deleted, since git holds the
history. Each item names a best-guess target capability spec; the guess is a starting point for the
proposal, not a commitment.

F1 and F4 were promoted to OpenSpec changes on 2026-08-20 and their files removed; git holds their
investigations, and the changes themselves now carry the current state. F2 was an audit, not a
change; it ran on 2026-08-20 and its deliverable is [f2-findings.md](f2-findings.md), whose gap list
is the source for future items.

Required behaviour is only ever specified in `openspec/specs/`; this directory never becomes a
second source of truth about it.

## Open feature requests

- [F3 — better streak gamification and incentives](f3-streak-gamification.md) — **deferred**, blocked
  on the goal-model questions F2 answered; see that file's `## Deferred` note

## Promoted

Their backlog files are removed; the OpenSpec change is the live record.

- F1 — narrate estimation progress → `openspec/changes/archive/2026-08-21-2026-08-20-narrate-estimation-progress`
- F4 — date-picker widget in edit mode → `openspec/changes/archive/2026-08-21-2026-08-20-date-time-picker-in-entry-editors`

## Completed audits

- [F2 — audit the code against the target profile](f2-target-profile-audit.md) →
  [findings](f2-findings.md)
