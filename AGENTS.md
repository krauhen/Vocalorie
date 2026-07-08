# AGENTS.md

This file is the landing page for agents and LLMs working in this repository.

It is intentionally not the full operating manual. Before starting work, agents must read the task-relevant files under `./agentic/` and must not plan, edit, test, or answer implementation questions from `AGENTS.md` alone.

---

## Project

This repository contains **Vocalorie**, a personal Android nutrition-tracking app built around an LLM meal-parsing spike.

- Package identity: namespace `com.example.vocalorie`, applicationId `app.vocalorie.personal`, app label "Vocalorie".
- `minSdk 35`, `targetSdk 36`, `compileSdk 36`.
- Not a git repository (no `.git` directory) as of 2026-07-08; there is no branch/commit history to rely on for prior-change context.

Current implementation, read from actual source (the `agentic/` guidance below still describes an earlier "fresh starter" state and should not be trusted for feature scope):

- UI is Jetpack Compose with Material 3: a spike screen for meal input plus a meal-entries history screen, meal editor, voice-input overlay, gallery-image attachment, and a settings screen.
- Meal parsing uses Koog (JetBrains agentic framework) against an OpenAI model, requesting a structured nutrition estimate (`NutritionEstimateDtos.kt`) with items, totals, assumptions, warnings, and confidence, from either a typed/spoken query, an attached photo, or both.
- The OpenAI API key is runtime BYOK, entered in-app; `local.properties` may prefill `openai.api.key` for local convenience and must never be committed.
- Reviewed meals are persisted to a local Room database (`VocalorieDatabase`, currently at schema version 4 via additive `Migration` steps — no `fallbackToDestructiveMigration`).
- App-owned Brave Search and WebFetch tools (`AgentTools.kt`) default to deterministic mock responses; real network calls are opt-in from Settings and require a locally stored Brave API key.
- Voice input uses Android's native `SpeechRecognizer`; availability depends on the device's installed speech service.

Standard local commands:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Preferred verification after a change:

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon
```

Install and try the current debug build on a running emulator/device:

```bash
./gradlew :app:installDebug --no-daemon
```

Validation targets seen in prior sessions: an Android emulator (`emulator-5554`), and previously a physical Samsung Galaxy S23 (`SM-S911B`, adb serial `RFCW20LALNM`).

---

## Mandatory context loading

Before starting any task, follow this sequence:

1. Identify the task type.
2. Read `agentic/README.md`.
3. Read the matching files under `agentic/guidance/`.
4. If the task relates to existing feature, product, or preparation knowledge, read the relevant files under `agentic/knowledge/`.
5. If the task continues or references a previous coding/product session, read the relevant files under `agentic/sessions/summaries/`.
6. If the session summary is insufficient, read the relevant raw session under `agentic/sessions/raw/`.
7. Only then start planning or execution.

If the correct files are unclear, read these defaults first:

- `agentic/guidance/WORKFLOW.md`
- `agentic/guidance/CODING.md`
- `agentic/guidance/TESTING.md`

As of 2026-07-08, `agentic/knowledge/*` subfolders and `agentic/sessions/*` are empty placeholders, and `agentic/guidance/*` and `agentic/README.md` still describe the app as a fresh starter — treat those as stale for feature scope, but still authoritative for durable operating rules (app identity, dependency approval, safety boundaries) until explicitly updated. Prefer reading actual source under `app/src/main/java/com/example/vocalorie/` and existing tests under `app/src/test/java/com/example/vocalorie/` for current behavior.

---

## Agentic directory structure

```text
agentic/
├── README.md
├── guidance/
│   ├── SETUP.md
│   ├── CODING.md
│   ├── TESTING.md
│   └── WORKFLOW.md
├── sessions/
│   ├── raw/
│   └── summaries/
└── knowledge/
    ├── features/
    ├── todos/
    └── requests/
```

---

## Directory purposes

### `agentic/README.md`

Index for the `agentic/` documentation area.

Use it to understand the available documentation groups and how they relate to each other. `AGENTS.md` remains the mandatory first-read landing page.

### `agentic/guidance/`

Standing operating instructions for agents.

Read files from this directory before changing documentation, code, tests, or workflows.

### `agentic/sessions/`

Session records for product, coding, architecture, and documentation work.

- `agentic/sessions/summaries/` stores condensed reusable handoffs.
- `agentic/sessions/raw/` stores near-raw session records when summaries are not detailed enough.

Use summaries first when restoring context from prior work.

Session file naming convention:

```text
YYYY-MM-DD__ticket-or-topic__short-slug.md
```

### `agentic/knowledge/`

Durable project knowledge.

- `agentic/knowledge/features/` stores feature intent, design assumptions, behavior, constraints, implementation ideas, and verification strategy.
- `agentic/knowledge/todos/` records deferred tasks and follow-ups; entries are not authorization to implement.
- `agentic/knowledge/requests/` records rough asks and early-stage requirements before promotion to feature knowledge.

Feature directory naming convention:

```text
<ticket-id>__<feature-slug>/
```

If there is no ticket ID:

```text
<feature-slug>/
```

---

## Task routing

Use this list to decide which files to read.

### Android app code change

- `agentic/guidance/CODING.md`
- `agentic/guidance/WORKFLOW.md`
- `agentic/guidance/TESTING.md`
- Relevant feature/session files.

### Koog prompt, LLM structured-output schema, or nutrition-estimation change

- `agentic/guidance/CODING.md`
- `app/src/main/java/com/example/vocalorie/ai/KoogNutritionSpike.kt`
- `app/src/main/java/com/example/vocalorie/model/NutritionEstimateDtos.kt`
- `app/src/test/java/com/example/vocalorie/ai/NutritionPromptContractTest.kt` (contract test asserting exact prompt/DTO wording — update it deliberately alongside any wording change)

### Room schema, migration, or persisted meal-data change

- `app/src/main/java/com/example/vocalorie/data/VocalorieDatabase.java`
- `app/src/main/java/com/example/vocalorie/data/MealEntity.kt`
- `app/src/main/java/com/example/vocalorie/data/MealMappers.kt`
- `agentic/guidance/TESTING.md`

### Agent tools (Brave Search / WebFetch), Settings, or API-key handling

- `app/src/main/java/com/example/vocalorie/tools/AgentTools.kt`
- `app/src/main/java/com/example/vocalorie/settings/`
- `agentic/guidance/CODING.md`

### Voice input or gallery-image attachment

- `app/src/main/java/com/example/vocalorie/ui/voice/`
- `agentic/guidance/TESTING.md`

### Test change or test run

- `agentic/guidance/TESTING.md`
- `agentic/guidance/WORKFLOW.md`

### New feature request capture

- Relevant files under `agentic/knowledge/requests/`.
- `agentic/guidance/WORKFLOW.md`

### TODO/follow-up capture

- Relevant files under `agentic/knowledge/todos/`.
- `agentic/guidance/WORKFLOW.md`

### Documentation-only work

- The target documentation file.
- `agentic/guidance/WORKFLOW.md`

---

## Universal safety rules

These rules always apply.

- Do not touch unrelated modified files.
- Do not commit secrets, including `local.properties`, `.env*`, OpenAI/Brave API keys, `key.pem`, or `cert.pem`.
- Do not drop untracked or uncommitted changes.
- Do not delete, move, or rename files without explicit approval.
- Do not change dependencies without explicit approval.
- Do not change build, CI/CD, or infrastructure files without explicit approval.
- Do not add data artifacts, generated build outputs, APKs, screenshots, databases, or copied personal data.
- Do not include personal details, names, contact information, addresses, or copied CV/project details in generated docs.
- Preserve existing project conventions (including app identity: namespace, applicationId, app label) unless the task explicitly asks to change them.
- Prefer minimal, focused changes.
- Ask before non-trivial or ambiguous changes.
