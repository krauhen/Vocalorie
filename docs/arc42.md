# Vocalorie — Architecture Documentation (arc42)

> Detail level: **LEAN**. This is a personal, single-developer Android app with no backend, no team, and no external customers — sections are kept as short as the content justifies, and sections with nothing meaningful to say are marked accordingly rather than padded.

---

## 1. Introduction and Goals

### 1.1 Requirements Overview

Vocalorie is a personal Android nutrition-tracking app that lets a single user log meals via voice, photo, or text, and uses an LLM to turn that input into a structured nutrition estimate.

**Essential Features**
- Log a meal by voice, photo, typed text, or a combination of these.
- Parse meal input into a structured nutrition estimate (items, totals, assumptions, warnings, confidence) via an LLM (Koog + OpenAI).
- Review and edit the parsed estimate before saving.
- Persist reviewed meals locally in a Room database, viewable in a meal-entries history screen with stats.
- Bring-your-own-key (BYOK) OpenAI API key entry, with optional local prefill via `local.properties`.
- Optional agent tools (Brave Search, WebFetch) to help ground nutrition estimates, off by default.

**Business Context**

Built for the author's own nutrition tracking. The value is reducing the friction of manual calorie logging — speak or snap a photo of a meal instead of looking up and typing nutrition data by hand.

### 1.2 Quality Goals

| Priority | Quality Goal | Concrete Scenario |
|:--------:|-------------|-------------------|
| 1 | #usable — low-friction logging | Logging a meal takes at most 4 steps (open → speak/capture → review → save), with no required typing for the common case. |
| 2 | #reliable — local data durability | Room schema migrations are additive only (no `fallbackToDestructiveMigration`); an app update must never drop or corrupt existing meal history across schema versions. |
| 3 | #secure — API key handling | OpenAI/Brave API keys (entered in-app or via `local.properties`) never appear in git history, logs, or crash reports. |
| 4 | #suitable — nutrition parsing correctness | The LLM estimate is trusted by default; the user reviews and can correct it before saving, and spot-checks results occasionally rather than relying on an automated accuracy benchmark. |

See Section 10 for detailed quality scenarios.

### 1.3 Stakeholder

| Role / Name | Contact | Expectations from Architecture |
|-------------|---------|--------------------------------|
| Author / sole user / maintainer | henning.krause@ti8m.ch | Understands the app well enough to extend it safely; documentation also onboards any coding agent working on the repo. |

**Quality goal sign-off:** Author (sole stakeholder).

---

## 2. Constraints

| Type | Constraint | Rationale / Source |
|------|-----------|---------------------|
| Technical | `minSdk 35`, `targetSdk 36`, `compileSdk 36` | `app/build.gradle.kts` |
| Technical | Kotlin 2.2.21, AGP 9.2.1, Compose BOM 2025.11.00 | `gradle/libs.versions.toml` |
| Technical | Single build type; release reuses debug signing, optimization disabled | `app/build.gradle.kts` — no release signing/minification set up, since this never ships to a store |
| Technical | UI framework fixed to Jetpack Compose + Material 3 | Established convention, not to be changed without explicit approval per `AGENTS.md` |
| Technical | Persistence fixed to Room; schema changes must be additive `Migration` steps, never `fallbackToDestructiveMigration` | `AGENTS.md`, `VocalorieDatabase.java` (schema version 4) |
| Technical | Meal parsing fixed to Koog (JetBrains agentic framework) against OpenAI models | `AGENTS.md`, `ai/KoogNutritionAgent.kt` |
| Organizational | No CI/CD pipeline exists; verification is manual (`./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`) | No `.github/workflows` found in repo |
| Organizational | Solo developer, no code review process, no release process — this is a personal tool, not a shipped product | Project nature |
| Conventions | Package identity (namespace `com.example.vocalorie`, applicationId `app.vocalorie.personal`, app label "Vocalorie") must be preserved unless explicitly changed | `AGENTS.md` |
| Conventions | Agent-facing operating rules live in `AGENTS.md` / `agentic/`; must be read before non-trivial changes | `AGENTS.md` |
| Legal/Privacy | API keys (OpenAI, Brave) and `local.properties`/`.env*` must never be committed | `AGENTS.md` |

---

## 3. Context and Scope

### 3.1 Business Context

Vocalorie has one human actor (the author, as end user) and three external systems it depends on. There is no Vocalorie-owned backend — all outbound calls originate directly from the device.

```
                    ┌─────────────────────┐
                    │        User          │
                    │  (author, sole user)  │
                    └──────────┬───────────┘
                               │ voice / photo / text input
                               │ reviews & corrects estimate
                               ▼
                    ┌─────────────────────┐
                    │      Vocalorie       │
                    │   (Android app)      │
                    └──┬─────────┬─────────┘
        text+image+key │         │ query text (opt-in)
                        ▼         ▼
              ┌─────────────┐ ┌─────────────┐   ┌───────────────────┐
              │  OpenAI API │ │ Brave Search│   │ Android platform  │
              │ (nutrition  │ │  API (opt-in │   │ SpeechRecognizer, │
              │  estimate)  │ │  grounding)  │   │ Camera/Gallery     │
              └─────────────┘ └─────────────┘   └───────────────────┘
```

| Partner | Direction | Data crossing the boundary |
|---------|-----------|------------------------------|
| User | in/out | Spoken/typed meal description, photo; reviewed/corrected nutrition estimate |
| OpenAI API | out/in | Meal text + optional images + system prompt out; structured JSON nutrition estimate in. Uses the user's own BYOK key. |
| Brave Search API | out/in | Search query out (opt-in, off by default; mocked otherwise); search results in |
| Android platform (SpeechRecognizer, Camera/Gallery) | in | Spoken audio → transcribed text; picked photo → image attachment |

A generic `WebFetchTool` also exists (`tools/AgentTools.kt`) that can fetch arbitrary URLs when agent tools are enabled — same opt-in/mock-by-default boundary as Brave Search.

### 3.2 Technical Context

Single Android APK; no Vocalorie-owned server or API. All three external boundaries above are consumed via direct HTTPS calls from the device (Ktor HTTP client for Brave/WebFetch, Koog's `OpenAILLMClient` for OpenAI). No message queues, no additional protocols.

---

## 4. Solution Strategy

- **Platform**: native Android, Jetpack Compose + Material 3 — single-activity app, no navigation library; screen switching is handled by local Compose state within `MealCaptureScreen`.
- **LLM integration**: Koog (JetBrains agentic framework) wraps the OpenAI client and enforces a structured-output contract (`NutritionEstimateDtos.kt`) rather than free-text parsing, so downstream code works with typed data. The exact prompt/DTO contract is pinned by a dedicated contract test (`NutritionPromptContractTest.kt`) to catch silent wording drift.
- **Trust model for AI output**: the LLM estimate is applied directly but always passed through a human review/edit step before it is persisted — this is the app's actual correctness mechanism, not automated validation (see Quality Goal 4).
- **Persistence**: Room, additive migrations only, to guarantee zero data loss across the app's own evolution (Quality Goal 2) — chosen over destructive fallback specifically because this is the user's real personal history.
- **Security posture for secrets**: no third-party secrets-management library; API keys are encrypted at rest with a hand-rolled AES/GCM scheme backed by the Android Keystore (`OpenAiApiKeyStore.kt`, `ToolSettingsStore.kt`), and BYOK keeps the author's key out of the app's own build artifacts.
- **External risk containment**: Brave Search and WebFetch tools default to deterministic mocks and require an explicit opt-in from Settings — the app doesn't make live third-party network calls unless the user chooses to.
- **No DI framework**: dependencies (stores, database, agent client) are constructed manually where needed. This is a deliberate simplicity choice appropriate for a single-screen-flow personal app, not an oversight.
- **No CI/CD**: verification is manual (`compileDebugKotlin` + `testDebugUnitTest`) run by the developer before relying on a change; acceptable given the solo, non-shipped nature of the project.

---

## 5. Building Block View

### 5.1 Level 1 — Package Overview

| Package | Responsibility |
|---------|-----------------|
| `ai/` | LLM agent orchestration — builds the Koog prompt/executor against OpenAI and turns input (text/voice transcript + optional images) into a structured nutrition estimate. |
| `data/` | Room persistence — entity, DAO, database, and mappers between entity and domain/DTO models. |
| `model/` | DTOs/domain models for nutrition estimates and meal drafts. |
| `settings/` | Encrypted local storage for the OpenAI API key and agent-tool settings/preferences. |
| `tools/` | Agent tool implementations available to the LLM (Brave Search, WebFetch) plus shared helpers and a rate limiter. |
| `ui/` | Compose screens: meal capture/root flow, entries history + stats, shared components, settings screen, voice/gallery input overlays. |
| `MainActivity.kt` | Single activity entry point; composes the root screen. |

### 5.2 Level 2 — Key Building Blocks

- **`ai/KoogNutritionAgent.kt`** — builds the Koog prompt/executor against OpenAI, holds the contract-tested `DEFAULT_SYSTEM_PROMPT`, exposes `estimate()` for text+image input, supports selectable models (GPT-4o, GPT-4o Mini, GPT-4.1 Mini, GPT-5.4 Mini). Wraps failures via `runCatching { }.getOrElse { throw NutritionAgentException(...) }`.
- **`model/NutritionEstimateDtos.kt`** — structured-output schema: items, totals, assumptions, warnings, confidence.
- **`data/VocalorieDatabase.java`, `MealEntity.kt`, `MealDao.java`, `MealMappers.kt`** — Room layer; single `meals` table, schema version 4, additive migrations only.
- **`tools/AgentTools.kt`** — `BraveSearchTool`, `WebFetchTool`, `AgentToolHelpers`, `ResearchToolCallLimiter`; mock-by-default, opt-in live calls.
- **`settings/OpenAiApiKeyStore.kt`, `ToolSettingsStore.kt`** — AES/GCM-via-Android-Keystore encrypted storage for API keys and tool preferences.
- **`ui/MealCaptureScreen.kt`** — root composable; owns local state for switching between capture, entries, and settings; hosts `runEstimate()` flow.
- **`ui/entries/MealEntriesScreen.kt`, `ui/entries/stats/MealStatsCalculator.kt`** — meal history list and aggregated stats.
- **`ui/voice/`** — `SpeechRecognizer`-backed voice input and gallery-image attachment overlays.

---

## 6. Runtime View

The only architecturally significant runtime scenario is "capture and save a meal":

1. User provides input via voice, photo, and/or typed text in `MealCaptureScreen`.
2. If voice: `ui/voice/` transcribes speech to text via Android's `SpeechRecognizer`.
3. `MealCaptureScreen.runEstimate()` calls `ai/KoogNutritionAgent.estimate()` with the assembled text + optional image attachments + the user's stored OpenAI key.
4. `KoogNutritionAgent` sends the system prompt + input to OpenAI via Koog/Ktor and parses the structured JSON response into a `NutritionEstimateDtos` result (or throws `NutritionAgentException` on failure).
5. The estimate is shown to the user for review; the user may edit any field.
6. On save, `MealMappers` converts the (possibly edited) estimate into a `MealEntity`, persisted via `MealDao`/`VocalorieDatabase`.
7. `MealEntriesScreen` reads persisted entries for history and stats display.

Not documented further at this detail level: settings screens, opt-in Brave/WebFetch tool calls — these are secondary flows without notable architectural complexity beyond what Section 3 and 5 already cover.

---

## 7. Deployment View

Single Android APK, sideloaded onto a device or emulator for personal use — there is no server-side component to deploy.

| Environment | Mechanism |
|-------------|-----------|
| Development / only environment | `./gradlew :app:installDebug --no-daemon` onto an Android emulator (`emulator-5554`) or a physical device (previously a Samsung Galaxy S23, `SM-S911B`) |

No CI/CD pipeline, no release build signing, no store distribution. The release build type exists in Gradle but reuses debug signing with optimization disabled — it is not a genuine production artifact.

---

## 8. Crosscutting Concepts

- **Domain model**: a meal is text/voice/photo input → an LLM-produced structured nutrition estimate (items, totals, assumptions, warnings, confidence) → optionally user-edited → persisted as a `MealEntity`.
- **Persistence**: Room, single `meals` table, schema version 4, strictly additive migrations (see Section 4).
- **Security / secrets handling**: OpenAI and Brave API keys are encrypted at rest using AES/GCM with a key held in the Android Keystore (`settings/OpenAiApiKeyStore.kt`, `settings/ToolSettingsStore.kt`) — a hand-rolled equivalent of Jetpack `EncryptedSharedPreferences`, not that library itself. Keys are BYOK (entered in-app) with an optional local-only `local.properties` prefill that must never be committed.
- **Error handling**: LLM calls are wrapped with `runCatching { }.getOrElse { throw NutritionAgentException(...) }` in `ai/KoogNutritionAgent.kt`, converting client/network failures into a typed, user-facing exception rather than propagating raw exceptions.
- **UI architecture**: Jetpack Compose + Material 3 throughout; no navigation library — screen switching is local Compose state within `ui/MealCaptureScreen.kt`.
- **Dependency construction**: no DI framework (no Hilt/Koin); classes like `OpenAiApiKeyStore` are constructed manually where needed. Deliberate given the app's small, single-flow scope.
- **External tool safety**: `tools/AgentTools.kt`'s `BraveSearchTool`/`WebFetchTool` default to deterministic mocks; live calls require explicit Settings opt-in, and `WebFetchTool` applies a `requireSafeFetchUrl` guard plus a `ResearchToolCallLimiter` rate limit.
- **Logging**: no dedicated logging framework found; take care that no API keys or full prompt/response payloads are ever logged, per the #secure quality goal.

---

## 9. Architecture Decisions

> Lightweight ADRs (Nygard format) for the decisions with lasting architectural impact, reconstructed from the current codebase and `AGENTS.md`. No `agentic/sessions/` records exist to cite as original decision context.

### ADR-1: Use Koog against OpenAI for meal parsing, with a structured-output contract
- **Status**: Accepted
- **Context**: Need to turn unstructured voice/photo/text meal input into usable nutrition data.
- **Decision**: Use JetBrains Koog as the agentic framework against an OpenAI model, requesting a structured DTO (`NutritionEstimateDtos.kt`) rather than parsing free text, and pin the exact prompt/DTO wording with `NutritionPromptContractTest.kt`.
- **Consequences**: Downstream code works with typed, predictable data; prompt changes are deliberate (test forces an explicit update) rather than silent drift. Tied to OpenAI's API and BYOK key model.

### ADR-2: Room with additive-only migrations, no destructive fallback
- **Status**: Accepted
- **Context**: This is the user's real, irreplaceable personal meal history.
- **Decision**: All schema changes go through explicit `Migration` steps; `fallbackToDestructiveMigration` is never used.
- **Consequences**: Slightly more migration-authoring effort per schema change, in exchange for a hard guarantee against data loss.

### ADR-3: Hand-rolled AES/GCM + Android Keystore for API key storage instead of a secrets library
- **Status**: Accepted
- **Context**: OpenAI/Brave keys must never leak into logs, commits, or backups.
- **Decision**: Encrypt keys at rest directly via `javax.crypto` + Android Keystore in `settings/OpenAiApiKeyStore.kt` / `ToolSettingsStore.kt`, rather than adopting Jetpack Security's `EncryptedSharedPreferences`.
- **Consequences**: One fewer dependency; the encryption code is bespoke and its correctness rests on the app's own tests/review rather than a maintained library.

### ADR-4: Opt-in, mock-by-default external tools (Brave Search, WebFetch)
- **Status**: Accepted
- **Context**: Grounding nutrition estimates with web search/fetch is useful but adds a live external dependency and cost/privacy surface.
- **Decision**: `AgentTools.kt` defaults to deterministic mock responses; real network calls require explicit Settings opt-in and a stored Brave API key.
- **Consequences**: Safe default behavior for a personal, low-maintenance app; real grounding is available but never surprises the user.

### ADR-5: No DI framework, no navigation library, no CI/CD
- **Status**: Accepted
- **Context**: Solo developer, single-activity app with one primary flow.
- **Decision**: Construct dependencies manually; switch screens via local Compose state; verify changes manually (`compileDebugKotlin` + `testDebugUnitTest`) instead of building a pipeline.
- **Consequences**: Minimal ceremony appropriate to scope; would need revisiting only if the app grows multiple contributors, screens, or a release process.

---

## 10. Quality Requirements

Expands the goals from Section 1.2 into concrete scenarios.

| # | Quality Goal | Scenario | Related Decision |
|---|-------------|----------|-------------------|
| Q1 | #usable — low-friction logging | User opens the app, captures a meal by voice or photo, reviews the estimate, and saves — at most 4 steps, no required typing. | Solution Strategy §4 (single-activity, no nav library) |
| Q2 | #reliable — local data durability | An app update that changes the Room schema must run an additive `Migration`, not a destructive fallback; existing meal rows (all fields) survive the update unchanged. | ADR-2 |
| Q3 | #secure — API key handling | Given a git history scan and a review of Settings/logging code, no OpenAI or Brave key value ever appears in a commit, log line, or crash report. | ADR-3 |
| Q4 | #suitable — nutrition parsing correctness | Given a captured meal, the LLM estimate is shown to the user with assumptions/warnings/confidence before save, and the user can edit any field; correctness is maintained by this review step plus the developer's occasional spot-checks, not an automated benchmark. | ADR-1 |

---

## 11. Risks and Technical Debt

| Risk / Debt | Impact | Mitigation / Notes |
|-------------|--------|---------------------|
| No automated accuracy benchmark for LLM nutrition estimates (ADR-1, Q4) | Silent drift in estimate quality could go unnoticed between occasional spot-checks. | Acceptable at current scale (solo user, human review before save); revisit if trust model changes to "apply without review." |
| Hand-rolled AES/GCM key storage instead of a maintained library (ADR-3) | Any subtle bug in the custom crypto code directly risks key exposure — the exact risk it's meant to prevent. | No dedicated crypto-focused tests observed; consider a targeted test or migrating to Jetpack Security if this code is touched again. |
| No CI/CD (ADR-5) | Regressions can be committed without any automated check. | Deliberate tradeoff for a solo project; mitigated by the manual `compileDebugKotlin` + `testDebugUnitTest` habit documented in `AGENTS.md`. |
| Release build type reuses debug signing with optimization disabled | The app is not distributable in a real production sense; not a risk today, would block any future store release. | Out of scope unless distribution is ever desired. |
| `WebFetchTool` fetches arbitrary URLs when enabled | SSRF-adjacent surface if the safety guard (`requireSafeFetchUrl`) has gaps. | Off by default; worth a focused review of `requireSafeFetchUrl` if this tool is ever enabled for real use. |
| No formal requirements/backlog document | Requirements live only in `AGENTS.md`/this doc and the developer's head. | Acceptable for a solo personal project. |

---

## 12. Glossary

| Term | Definition |
|------|------------|
| Koog | JetBrains' agentic framework used to orchestrate LLM calls and structured-output parsing for meal nutrition estimation. |
| BYOK | Bring-your-own-key — the user supplies their own OpenAI (and optionally Brave) API key rather than the app providing one. |
| Nutrition estimate | The structured LLM output for a meal: line items, totals, assumptions, warnings, and a confidence level. |
| `MealEntity` | The Room entity representing one persisted, reviewed meal. |
| Agent tools | Optional LLM-callable capabilities (Brave Search, WebFetch) that can ground a nutrition estimate with external information; mocked by default. |
| Contract test | `NutritionPromptContractTest.kt` — a test asserting the exact wording of the LLM system prompt/DTO contract, so changes are deliberate, not accidental. |
