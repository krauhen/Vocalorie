# Agentic guidance

This directory contains repository-specific context for agents working on **Vocalorie**.

Vocalorie is a working Android/Jetpack Compose nutrition-tracking app. Required behaviour lives in `openspec/specs/`; architecture and its ADRs live in `docs/arc42.md`. This directory holds the operating rules and the project's own accumulated knowledge.

## Directory purposes

- `guidance/` stores durable operating rules split by topic.
- `knowledge/features/` stores feature intent, constraints, implementation notes, and verification strategy.
- `knowledge/todos/` stores deferred tasks and follow-ups for future triage.
- `knowledge/requests/` stores rough asks before they become feature knowledge.
- `sessions/raw/` stores detailed session notes when needed.
- `sessions/summaries/` stores concise reusable handoffs.

## Current snapshot

- Android app identity: `app.vocalorie.personal`.
- Kotlin namespace: `com.example.vocalorie`.
- UI stack: Jetpack Compose with Material 3.
- Persistence: Room at schema version 10, four entities, additive migrations only.
- Layering: UI → state holder → repository → DAO, wired by `AppContainer`; no DI framework, no navigation library, no CI.
