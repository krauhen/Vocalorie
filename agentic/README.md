# Agentic guidance

This directory contains repository-specific context for agents working on **Vocalorie**.

Vocalorie is currently a fresh Android/Jetpack Compose starter. Product requirements, domain behavior, and validation evidence should be added here as they become real decisions.

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
- Current implementation: minimal buildable starter screen only.
