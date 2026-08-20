---
description: How Vocalorie work is sequenced — keeping architecture rules and ADRs in step, recording deliberate omissions as accepted debt, and where current project status is specified.
tags: [workflow, adr, arc42, accepted-debt, process]
---

# Workflow guidance

## Safety policy

The universal safety boundaries live in `docs/agent/hard-rules.md` and apply to every task. Read them there; they are not repeated here.

## Keeping rules and architecture in step

A change to an architectural rule updates `docs/agent/guidance/` **and** the matching ADR in `docs/arc42.md` in the same commit. A rule with no ADR, or an ADR the guidance contradicts, is a defect — supersede the older statement explicitly rather than leaving both standing.

Something deliberately not done belongs in the `docs/arc42.md` §11.1 accepted-debt table with a one-line reason, so a later audit does not re-raise it.

## Current project status

Vocalorie is a working Android/Jetpack Compose app. Required behaviour is specified in `openspec/specs/`; architecture and decisions in `docs/arc42.md`. Read the relevant capability spec before changing behaviour it covers.
