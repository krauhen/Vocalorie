---
description: Orientation for Vocalorie — what the app is, its stack and current implementation state, and the map of the agent documentation area with its naming conventions.
tags: [orientation, project, architecture, documentation-map, conventions]
---

# Agent orientation

This directory contains repository-specific context for agents working on **Vocalorie**.

Vocalorie is a personal Android nutrition-tracking app that turns voice, photo or typed meal input into a structured nutrition estimate via an LLM. Required behaviour lives in `openspec/specs/`; architecture and its ADRs live in `docs/arc42.md`. This directory holds the operating rules and the project's own accumulated knowledge.

## Project

- Package identity: namespace `com.example.vocalorie`, applicationId `app.vocalorie.personal`, app label "Vocalorie".
- `minSdk 35`, `targetSdk 36`, `compileSdk 36`.
- This is a Git repository; inspect the working tree and recent commits before editing.

Current implementation, read from actual source:

- UI is Jetpack Compose with Material 3: a meal-capture screen plus a meal-entries history screen with stats, meal and activity editors, voice-input overlay, gallery-image attachment, and a settings screen.
- Architecture is one-directional: UI → state holder (`ui/capture/MealCaptureViewModel.kt`) → repository (`data/repository/`) → DAO, wired by `AppContainer.kt`. No `Context` and no DAO reference above the repository boundary. No DI framework, no navigation library, no CI.
- Meal parsing uses Koog (JetBrains agentic framework) against an OpenAI model, requesting a structured nutrition estimate (`NutritionEstimateDtos.kt`) with items, totals, assumptions, warnings, and confidence, from either a typed/spoken query, an attached photo, or both.
- The OpenAI API key is runtime BYOK, entered in-app; `local.properties` may prefill `openai.api.key` for local convenience and must never be committed.
- Reviewed meals, activities and the meal cache are persisted to a local Room database (`VocalorieDatabase`, four entities, currently at schema version 10 via additive `Migration` steps — no `fallbackToDestructiveMigration`). `exportSchema` is on; `app/schemas/` is committed from v8 onward.
- App-owned Brave Search and WebFetch tools (`AgentTools.kt`) make **real network calls only** — there is no mock path, and `AgentToolsRealOnlyContractTest` fails the build if one is reintroduced. Grounding is active only when a Brave API key is stored and the tool-call budget is above zero, and a failed pass surfaces a warning instead of degrading silently.
- Voice input uses Android's native `SpeechRecognizer`; availability depends on the device's installed speech service.

## Documentation areas

Two documentation areas sit outside `docs/agent/` and are part of task routing:

- `openspec/` — the spec-driven change workflow. `openspec/specs/<capability>/spec.md` states required behaviour; `openspec/changes/<id>/` holds an in-flight proposal, design and tasks. Read the relevant capability spec before changing behaviour it covers.
- `docs/arc42.md` — architecture documentation: constraints, building blocks, the ADRs, and the §11.1 accepted-debt table. Read it before an architectural change; an architectural rule change updates `docs/agent/guidance/` **and** the matching ADR in the same commit.

## Directory structure

```text
docs/agent/
├── README.md
├── hard-rules.md
├── guidance/
│   ├── task-routing.md
│   ├── setup.md
│   ├── coding.md
│   ├── testing.md
│   └── workflow.md
├── sessions/
│   ├── raw/
│   └── summaries/
└── knowledge/
    ├── features/
    ├── todos/
    └── requests/
```

### Directory purposes

- `hard-rules.md` stores the always-read rules: context loading and universal safety boundaries.
- `guidance/` stores durable operating rules split by topic. Read from it before changing documentation, code, tests, or workflows.
- `knowledge/features/` stores feature intent, design assumptions, behavior, constraints, implementation ideas, and verification strategy.
- `knowledge/todos/` records deferred tasks and follow-ups; entries are not authorization to implement.
- `knowledge/requests/` records rough asks and early-stage requirements before promotion to feature knowledge.
- `sessions/summaries/` stores condensed reusable handoffs. Use summaries first when restoring context from prior work.
- `sessions/raw/` stores near-raw session records when summaries are not detailed enough.

### Naming conventions

Session files:

```text
YYYY-MM-DD__ticket-or-topic__short-slug.md
```

Feature directories:

```text
<ticket-id>__<feature-slug>/
```

If there is no ticket ID:

```text
<feature-slug>/
```

## Current snapshot

- Android app identity: `app.vocalorie.personal`.
- Kotlin namespace: `com.example.vocalorie`.
- UI stack: Jetpack Compose with Material 3.
- Persistence: Room at schema version 10, four entities, additive migrations only.
- Layering: UI → state holder → repository → DAO, wired by `AppContainer`; no DI framework, no navigation library, no CI.
