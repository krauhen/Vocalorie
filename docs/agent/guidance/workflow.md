---
description: How Vocalorie work is sequenced — where the architecture rules live, recording deliberate omissions as accepted debt, and where current project status is specified.
tags: [workflow, architecture-rules, accepted-debt, process]
---

# Workflow guidance

## Safety policy

The universal safety boundaries live in `docs/agent/hard-rules.md` and apply to every task. Read them there; they are not repeated here.

## Keeping the architecture rules coherent

`docs/agent/guidance/coding.md` is the single source of truth for the binding architecture rules; there is no separate architecture document. Two rules that contradict each other are a defect — supersede the older statement explicitly in the same commit rather than leaving both standing.

Something deliberately not done belongs in `docs/agent/backlog/` with a one-line reason, so a later audit does not re-raise it.

## Current project status

Vocalorie is a working Android/Jetpack Compose app. Required behaviour is specified in `openspec/specs/`; the architecture rules in `docs/agent/guidance/coding.md`. Read the relevant capability spec before changing behaviour it covers.
