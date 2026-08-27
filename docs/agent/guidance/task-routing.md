---
description: Per-task-type reading lists — which guidance, spec and source files to open for each kind of Vocalorie change, from Android code to Room migrations to architecture-rule updates.
tags: [routing, task-types, navigation, source-map]
---

# Task routing

Use this list to decide which files to read for a given task type. The always-read rules and the general loading sequence live in `docs/agent/hard-rules.md`; project orientation and the documentation-area map live in `docs/agent/README.md`.

## Android app code change

- `docs/agent/guidance/coding.md`
- `docs/agent/guidance/workflow.md`
- `docs/agent/guidance/testing.md`
- Relevant `openspec/specs/` capability, and relevant feature/session files.

## State holder, layering, or dependency-wiring change

- `docs/agent/guidance/coding.md` (the layering rule)
- `app/src/main/java/com/example/vocalorie/AppContainer.kt`
- `app/src/main/java/com/example/vocalorie/ui/capture/`
- `app/src/main/java/com/example/vocalorie/data/repository/`

## Koog prompt, LLM structured-output schema, or nutrition-estimation change

- `docs/agent/guidance/coding.md`
- `app/src/main/java/com/example/vocalorie/ai/KoogNutritionAgent.kt`
- `app/src/main/java/com/example/vocalorie/model/NutritionEstimateDtos.kt`
- `app/src/test/java/com/example/vocalorie/ai/NutritionPromptContractTest.kt` (contract test asserting exact prompt/DTO wording — update it deliberately alongside any wording change)

## Room schema, migration, or persisted meal-data change

- `app/src/main/java/com/example/vocalorie/data/VocalorieDatabase.java`
- `app/src/main/java/com/example/vocalorie/data/MealEntity.kt`
- `app/src/main/java/com/example/vocalorie/data/MealMappers.kt`
- `app/src/main/java/com/example/vocalorie/data/VocalorieBackup.kt` (the backup version moves with the schema version)
- `openspec/specs/data-backup/spec.md`
- `docs/agent/guidance/testing.md`

## Agent tools (Brave Search / WebFetch), Settings, or API-key handling

- `app/src/main/java/com/example/vocalorie/tools/AgentTools.kt`
- `app/src/main/java/com/example/vocalorie/settings/`
- `docs/agent/guidance/coding.md`

## Voice input or gallery-image attachment

- `app/src/main/java/com/example/vocalorie/ui/voice/`
- `docs/agent/guidance/testing.md`

## Test change or test run

- `docs/agent/guidance/testing.md`
- `docs/agent/guidance/workflow.md`

## New feature request capture

- Relevant files under `docs/agent/backlog/features/`.
- `docs/agent/guidance/workflow.md`

## Bug or follow-up capture

- Relevant files under `docs/agent/backlog/bugs/`.
- `docs/agent/guidance/workflow.md`

## Architecture rule change

- `docs/agent/guidance/coding.md` (the binding architecture rules)
- `docs/agent/guidance/workflow.md`

## Documentation-only work

- The target documentation file.
- `docs/agent/guidance/workflow.md`
