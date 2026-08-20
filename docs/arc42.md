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
- Optional agent tools (Brave Search, WebFetch) to help ground nutrition estimates, gated on a stored Brave API key.

**Business Context**

Built for the author's own nutrition tracking. The value is reducing the friction of manual calorie logging — speak or snap a photo of a meal instead of looking up and typing nutrition data by hand.

### 1.2 Quality Goals

| Priority | Quality Goal | Concrete Scenario |
|:--------:|-------------|-------------------|
| 1 | #usable — low-friction logging | Logging a meal takes at most 4 steps (open → speak/capture → review → save), with no required typing for the common case. |
| 2 | #reliable — local data durability | Room schema migrations are additive only (no `fallbackToDestructiveMigration`); an app update must never drop or corrupt existing meal history across schema versions. |
| 3 | #secure — API key handling | OpenAI/Brave API keys (entered in-app or via `local.properties`) never appear in git history, logs, or crash reports. |
| 4 | #suitable — nutrition parsing correctness | The LLM estimate is trusted by default; the user reviews and can correct it before saving, and spot-checks results occasionally rather than relying on an automated accuracy benchmark. |
| 5 | #performant — no felt stalls | Attaching four photos, navigating days or heatmap cells, and typing in a ten-item meal never block the main thread or visibly stutter; no bitmap, crypto, preference or JSON work runs on it. |

See Section 10 for detailed quality scenarios.

### 1.3 Stakeholder

| Role / Name | Contact | Expectations from Architecture |
|-------------|---------|--------------------------------|
| Author / sole user / maintainer | — (single developer, contact intentionally not recorded here) | Understands the app well enough to extend it safely; documentation also onboards any coding agent working on the repo. |

**Quality goal sign-off:** Author (sole stakeholder).

---

## 2. Constraints

| Type | Constraint | Rationale / Source |
|------|-----------|---------------------|
| Technical | `minSdk 35`, `targetSdk 36`, `compileSdk 36` | `app/build.gradle.kts` |
| Technical | Kotlin 2.2.21, AGP 9.2.1, Compose BOM 2025.11.00 | `gradle/libs.versions.toml` |
| Technical | Single build type; release reuses debug signing, optimization disabled | `app/build.gradle.kts` — no release signing/minification set up, since this never ships to a store |
| Technical | UI framework fixed to Jetpack Compose + Material 3 | Established convention, not to be changed without explicit approval per `AGENTS.md` |
| Technical | Persistence fixed to Room; schema changes must be additive `Migration` steps, never `fallbackToDestructiveMigration` | `AGENTS.md`, `VocalorieDatabase.java` (schema version 10) |
| Technical | Meal parsing fixed to Koog (JetBrains agentic framework) against OpenAI models | `AGENTS.md`, `ai/KoogNutritionAgent.kt` |
| Organizational | No CI/CD pipeline exists; verification is manual (`./gradlew :app:compileDebugKotlin :app:testDebugUnitTest`, plus `connectedDebugAndroidTest` for schema changes) | No `.github/workflows` found in repo; command owned by `docs/agent/guidance/testing.md` |
| Organizational | Solo developer, no code review process, no release process — this is a personal tool, not a shipped product | Project nature |
| Conventions | Package identity (namespace `com.example.vocalorie`, applicationId `app.vocalorie.personal`, app label "Vocalorie") must be preserved unless explicitly changed | `AGENTS.md` |
| Conventions | Agent-facing operating rules live in `AGENTS.md` / `docs/agent/`; must be read before non-trivial changes | `AGENTS.md` |
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
        text+image+key │         │ query text (key-gated)
                        ▼         ▼
              ┌─────────────┐ ┌──────────────┐  ┌───────────────────┐
              │  OpenAI API │ │ Brave Search │  │ Android platform  │
              │ (nutrition  │ │ API (grounding│  │ SpeechRecognizer, │
              │  estimate)  │ │ , key-gated)  │  │ Camera/Gallery     │
              └─────────────┘ └──────────────┘  └───────────────────┘
```

| Partner | Direction | Data crossing the boundary |
|---------|-----------|------------------------------|
| User | in/out | Spoken/typed meal description, photo; reviewed/corrected nutrition estimate |
| OpenAI API | out/in | Meal text + optional images + system prompt out; structured JSON nutrition estimate in. Uses the user's own BYOK key. |
| Brave Search API | out/in | Search query out (only when a Brave API key is stored and the tool-call budget is above zero); search results in |
| Android platform (SpeechRecognizer, Camera/Gallery) | in | Spoken audio → transcribed text; picked photo → image attachment |

A generic `WebFetchTool` also exists (`tools/AgentTools.kt`) that can fetch arbitrary URLs during a grounding pass — same key-gated boundary as Brave Search, plus a `requireSafeFetchUrl` guard, a bounded body read and a content-type check.

### 3.2 Technical Context

Single Android APK; no Vocalorie-owned server or API. All three external boundaries above are consumed via direct HTTPS calls from the device (Ktor HTTP client for Brave/WebFetch, Koog's `OpenAILLMClient` for OpenAI). No message queues, no additional protocols.

---

## 4. Solution Strategy

- **Platform**: native Android, Jetpack Compose + Material 3 — single-activity app, no navigation library; screen switching is handled by state in the capture flow's state holder.
- **Layering**: one direction only — UI → state holder → repository → DAO. Composables render state and emit events; `MealCaptureViewModel` orchestrates; `data/repository/` owns dispatching, entity mapping, `Flow` change notification and transactions. `Context` and DAO references stop at the repository boundary (ADR-6).
- **LLM integration**: Koog (JetBrains agentic framework) wraps the OpenAI client and enforces a structured-output contract (`NutritionEstimateDtos.kt`) rather than free-text parsing, so downstream code works with typed data. The exact prompt/DTO contract is pinned by a dedicated contract test (`NutritionPromptContractTest.kt`) to catch silent wording drift.
- **Trust model for AI output**: the LLM estimate is applied directly but always passed through a human review/edit step before it is persisted — this is the app's actual correctness mechanism, not automated validation (see Quality Goal 4).
- **Persistence**: Room, additive migrations only, to guarantee zero data loss across the app's own evolution (Quality Goal 2) — chosen over destructive fallback specifically because this is the user's real personal history.
- **Security posture for secrets**: no third-party secrets-management library; API keys are encrypted at rest with a hand-rolled AES/GCM scheme backed by the Android Keystore (`OpenAiApiKeyStore.kt`, `ToolSettingsStore.kt`), and BYOK keeps the author's key out of the app's own build artifacts.
- **External risk containment**: the research tools are real-only (ADR-7 supersedes ADR-4) — there is no mock path to fall back to, so a failure is reported rather than dressed up as a result. Grounding runs only when a Brave API key is stored, and is bounded by a tool-call budget and an iteration cap.
- **Injected I/O seams**: every out-of-process dependency is reached through a narrow interface (`HttpTextFetcher`, `NutritionEstimator`, `TableChangeSource`, `TransactionRunner`, a `clock: () -> Instant`), so the JVM test suite drives them with a lambda and never a device or a network (ADR-8).
- **No DI framework**: dependencies (stores, database, repositories, agent client) are constructed once in a hand-written `AppContainer`, shaped like the existing `VocalorieDatabase.get(context)` singleton. A deliberate simplicity choice, not an oversight (ADR-6).
- **No CI/CD**: verification is manual (`compileDebugKotlin` + `testDebugUnitTest`) run by the developer before relying on a change; acceptable given the solo, non-shipped nature of the project.

---

## 5. Building Block View

### 5.1 Level 1 — Package Overview

| Package | Responsibility |
|---------|-----------------|
| `ai/` | LLM agent orchestration — builds the Koog prompt/executor against OpenAI and turns input (text/voice transcript + optional images) into a structured nutrition estimate. |
| `data/` | Room persistence — entities, DAOs, database, mappers, and backup envelope. |
| `data/repository/` | The boundary everything above depends on: dispatching, entity↔domain mapping, `Flow` change notification, transactions, and the JVM-testable seams (`TableChangeSource`, `TransactionRunner`). |
| `model/` | DTOs/domain models for nutrition estimates and meal drafts. |
| `settings/` | Encrypted local storage for the OpenAI API key and agent-tool settings/preferences. |
| `tools/` | Agent tool implementations available to the LLM (Brave Search, WebFetch) plus shared helpers and a rate limiter. |
| `ui/` | Compose screens: meal capture/root flow, entries history + stats, shared components, settings screen, voice/gallery input overlays. |
| `ui/capture/` | Capture-flow state holder (`MealCaptureViewModel`, `MealCaptureUiState`) and the pure rules it calls (`EstimatePlan`). |
| `MainActivity.kt` | Single activity entry point; composes the root screen. |
| `AppContainer.kt` | The process-wide object graph: database, repositories, one HTTP fetcher, one nutrition agent, one settings store each. |

### 5.2 Level 2 — Key Building Blocks

- **`ai/KoogNutritionAgent.kt`** — a class implementing `NutritionEstimator`, taking an `HttpTextFetcher`; builds the Koog prompt/executor against OpenAI, caches the prompt executor and LLM client per API key, holds the contract-tested `DEFAULT_SYSTEM_PROMPT`, exposes `estimate()` for text+image input with a request timeout and bounded retry, supports selectable models. Converts failures to `NutritionAgentException` and classifies them on the cause chain.
- **`model/NutritionEstimateDtos.kt`** — structured-output schema: items, totals, assumptions, warnings, confidence.
- **`data/VocalorieDatabase.java`, `MealDao.java`, `ActivityDao.java`, `CacheDao.java`** — Room layer at schema version 10 with `exportSchema = true`; four entities (`MealEntity`, `ActivityEntity`, `CachedMealEntity`, `CachedItemEntity`) over the `meals`, `activities`, `cached_meals` and `cached_items` tables; nine registered additive migrations, no destructive fallback. These four files are the only Java left in the app — see ADR-6.
- **`data/repository/MealRepository.kt` and siblings** — `suspend` reads/writes on `Dispatchers.IO`, `observe*(): Flow` driven by Room's invalidation tracker, and multi-write units through `TransactionRunner`.
- **`tools/AgentTools.kt`** — `BraveSearchTool`, `WebFetchTool`, `AgentToolHelpers`, `ResearchToolCallLimiter`; real calls only, through the injected `HttpTextFetcher`, with status checking, a bounded body read and per-hop URL validation.
- **`settings/OpenAiApiKeyStore.kt`, `ToolSettingsStore.kt`, `KeystoreSecretCodec.kt`** — AES/GCM-via-Android-Keystore encrypted storage for API keys and tool preferences, sharing one codec.
- **`ui/MealCaptureScreen.kt` + `ui/capture/MealCaptureViewModel.kt`** — root composable rendering `MealCaptureUiState` and emitting events; the state holder owns the capture, entries and settings routing plus the estimate flow on `viewModelScope`.
- **`ui/entries/MealEntriesScreen.kt`, `ui/entries/stats/MealStatsCalculator.kt`** — meal history list and aggregated stats.
- **`ui/voice/`** — `SpeechRecognizer`-backed voice input and gallery-image attachment overlays.

---

## 6. Runtime View

The only architecturally significant runtime scenario is "capture and save a meal":

1. User provides input via voice, photo, and/or typed text in `MealCaptureScreen`.
2. If voice: `ui/voice/` transcribes speech to text via Android's `SpeechRecognizer`.
3. `MealCaptureViewModel` plans the estimate (cache hit vs. LLM call) and, on `viewModelScope`, calls `NutritionEstimator.estimate()` with the assembled text + optional image attachments + the user's stored OpenAI key — so a rotation no longer cancels a request the user is paying for.
4. `KoogNutritionAgent` sends the system prompt + input to OpenAI via Koog/Ktor and parses the structured JSON response into a `NutritionEstimateDtos` result (or throws `NutritionAgentException` on failure).
5. The estimate is shown to the user for review; the user may edit any field.
6. On save, `MealRepository` converts the (possibly edited) estimate into a `MealEntity` and writes the meal row and its cache rows as one transaction, off the main thread.
7. `MealEntriesScreen` renders the entries and stats the state holder collects from the repository `Flow`s — the database, not a manual refresh call, is the change notification.

Not documented further at this detail level: settings screens, the key-gated Brave/WebFetch grounding pass — these are secondary flows without notable architectural complexity beyond what Section 3 and 5 already cover.

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
- **Persistence**: Room, schema version 10, four entities (`meals`, `activities`, `cached_meals`, `cached_items`), strictly additive migrations (see Section 4). Meal line items are stored as a JSON column rather than a relational table (see Section 11).
- **Security / secrets handling**: OpenAI and Brave API keys are encrypted at rest using AES/GCM with a key held in the Android Keystore (`settings/OpenAiApiKeyStore.kt`, `settings/ToolSettingsStore.kt`) — a hand-rolled equivalent of Jetpack `EncryptedSharedPreferences`, not that library itself. Keys are BYOK (entered in-app) with an optional local-only `local.properties` prefill that must never be committed.
- **Error handling**: fail loud, never manufacture plausible data. LLM calls become a typed `NutritionAgentException`, classified by walking the full cause chain (Koog and Ktor wrap HTTP errors, so a rejected key usually sits in a cause). Malformed item JSON, an unreadable stored key and an unknown activity type are reported or mapped to a neutral value rather than becoming a 0-kcal meal, "no key configured", or a running workout.
- **UI architecture**: Jetpack Compose + Material 3 throughout; no navigation library — routing is state in `ui/capture/MealCaptureViewModel.kt`, rendered by `ui/MealCaptureScreen.kt`.
- **Threading**: the main thread draws and nothing else. Bitmap decoding, crypto, `SharedPreferences` access and JSON parsing run on a repository dispatcher; composables `remember` anything derived and take the clock as a parameter (#performant).
- **Dependency construction**: no DI framework (no Hilt/Koin); `AppContainer` builds the graph once per process, and the state holder is obtained via `viewModelFactory { initializer { } }`. Deliberate given the app's small, single-flow scope (ADR-6).
- **External tool safety**: `tools/AgentTools.kt`'s `BraveSearchTool`/`WebFetchTool` make real calls only, through the shared `HttpTextFetcher`. Grounding is gated on a stored Brave key and a non-zero tool-call budget; `WebFetchTool` applies `requireSafeFetchUrl` per redirect hop, fails closed on DNS resolution failure, rejects non-text content types, bounds the body read, and is rate-limited by `ResearchToolCallLimiter`.
- **Logging**: no dedicated logging framework found; take care that no API keys or full prompt/response payloads are ever logged, per the #secure quality goal.

---

## 9. Architecture Decisions

> Lightweight ADRs (Nygard format) for the decisions with lasting architectural impact, reconstructed from the current codebase and `AGENTS.md`. No records of the original decision context exist to cite.

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
- **Status**: **Superseded by ADR-7.**
- **Context**: Grounding nutrition estimates with web search/fetch is useful but adds a live external dependency and cost/privacy surface.
- **Decision**: `AgentTools.kt` defaults to deterministic mock responses; real network calls require explicit Settings opt-in and a stored Brave API key.
- **Consequences**: The mock path was intended as a safe default but behaved as a silent one — a missing key or a failed call produced fabricated search results indistinguishable from real ones, and the user paid for an estimate grounded in fiction. Removed; see ADR-7.

### ADR-5: No DI framework, no navigation library, no CI/CD
- **Status**: Accepted (extended by ADR-6)
- **Context**: Solo developer, single-activity app with one primary flow.
- **Decision**: Construct dependencies manually; switch screens via local Compose state; verify changes manually (`compileDebugKotlin` + `testDebugUnitTest`) instead of building a pipeline.
- **Consequences**: Minimal ceremony appropriate to scope; would need revisiting only if the app grows multiple contributors, screens, or a release process. ADR-6 keeps the no-framework decision while centralising the manual wiring.

### ADR-6: One-directional UI → state holder → repository → DAO layering, wired by a hand-written container
- **Status**: Accepted
- **Context**: The capture flow was a single 746-line composable holding 35 `mutableStateOf` slots while owning database access, settings persistence, backup import/export, AI orchestration and routing. Seven repository-shaped functions were declared inside the composable body, closing over `LocalContext`. Every write ran on `rememberCoroutineScope()`, so a rotation could cancel a billable OpenAI request, or commit a meal row while cancelling the cache write that followed it. Reactivity was hand-rolled across nine manual refresh sites, and `ThemeSettingsStore` existed in three instances that could not see each other's writes.
- **Decision**: Dependencies flow one way only: UI → state holder → repository → DAO. `Context` and DAO references stop at the repository boundary. `data/repository/` owns dispatching, entity mapping, `Flow` change notification from Room's invalidation tracker, and transactions. `MealCaptureViewModel` holds capture-flow state on `viewModelScope`; business rules are pure functions it calls rather than methods it contains. `AppContainer` — a hand-written double-checked singleton copying the existing `VocalorieDatabase.get(context)` idiom, not a DI framework — owns one instance of each long-lived collaborator.
- **Consequences**: An in-flight estimate survives rotation and a multi-row save is atomic. Business rules became unit-testable on the JVM for the first time. The DAOs stay Java and blocking: a spike established that KSP is incompatible with AGP 9.2.1's built-in Kotlin, so a Kotlin `@Dao` cannot be annotation-processed here. This costs nothing downstream, because the repository — not the DAO — is the seam everything above depends on. `viewModelScope` also changes cancellation semantics: work that used to die with the composable now outlives it, which is the point but had to be re-checked on every overlay dismiss path.

  **Amendment (day-score tips, 2026-07-31).** `MealCaptureUiState` now carries one *derived aggregate* — the ranked `dayScoreTips` list — alongside its raw lists and settings. The rewording path needs a repository and a coroutine scope, which a composable has neither of, so leaving derivation in `MealEntriesScreen` would have split ownership of one value across the composable and the state holder. The precedent is deliberately narrow: only the tip list moved, not the score, totals, burn or balance, and the ranking itself stays a pure function (`ui/entries/stats/DayScoreTips.kt`) the state holder calls. The rewording collaborator is an injected `TipRewordingAgent` seam per ADR-8, so the JVM suite covers every rejection path without a key or a network call.

### ADR-7: Real-only research tools; no mock path exists
- **Status**: Accepted (supersedes ADR-4)
- **Context**: ADR-4's mock default meant a grounding pass could silently fabricate its sources — and `docs/arc42.md` mitigated the `WebFetchTool` SSRF surface as "off by default", which stopped being true once grounding became key-gated.
- **Decision**: Delete the mock responses and the opt-in toggles. `BraveSearchTool` and `WebFetchTool` make real calls or report a failure. Grounding runs only when a Brave API key is stored and the tool-call budget is above zero, and a failed pass carries an explicit warning onto the result instead of degrading quietly. A contract test (`AgentToolsRealOnlyContractTest`) fails the build if a mock or toggle is reintroduced into production source.
- **Consequences**: A grounding failure is visible rather than invisible, and there is no code path that returns invented search results. The tools are no longer exercisable without a key, so their tests drive the injected `HttpTextFetcher` instead (ADR-8). The residual TOCTOU gap in `requireSafeFetchUrl` is recorded as accepted debt in Section 11.

### ADR-8: Reach out-of-process dependencies through injected seams
- **Status**: Accepted
- **Context**: The tool layer constructed an `HttpClient(Android)` per invocation and the agent rebuilt its prompt executor and LLM client per call, leaking engines and threads. Nothing was testable without a device or a billable network call, and one live harness test was running under the mandated `testDebugUnitTest`, spending real credit.
- **Decision**: Every out-of-process dependency sits behind a narrow interface owned by `AppContainer` and created once: `HttpTextFetcher` for HTTP text, `NutritionEstimator` for the LLM, `TableChangeSource` and `TransactionRunner` for Room, `DocumentTextStore` for content-resolver I/O, and a `clock: () -> Instant` for time. Tests substitute a lambda. A test needing real network, a real key or a machine path is `@Ignore`d with its reason.
- **Consequences**: `testDebugUnitTest` is pure JVM, makes no network call and reads no `local.properties`. HTTP engines and prompt executors are created once per process instead of per call. The cost is one extra indirection per dependency, and an abstract Room `@Database` still cannot be instantiated on the JVM — which is why the change source is an interface rather than a direct `invalidationTracker` call.

---

## 10. Quality Requirements

Expands the goals from Section 1.2 into concrete scenarios.

| # | Quality Goal | Scenario | Related Decision |
|---|-------------|----------|-------------------|
| Q1 | #usable — low-friction logging | User opens the app, captures a meal by voice or photo, reviews the estimate, and saves — at most 4 steps, no required typing. | Solution Strategy §4 (single-activity, no nav library) |
| Q2 | #reliable — local data durability | An app update that changes the Room schema must run an additive `Migration`, not a destructive fallback; existing meal rows (all fields) survive the update unchanged. | ADR-2 |
| Q3 | #secure — API key handling | Given a git history scan and a review of Settings/logging code, no OpenAI or Brave key value ever appears in a commit, log line, or crash report. | ADR-3 |
| Q4 | #suitable — nutrition parsing correctness | Given a captured meal, the LLM estimate is shown to the user with assumptions/warnings/confidence before save, and the user can edit any field; correctness is maintained by this review step plus the developer's occasional spot-checks, not an automated benchmark. | ADR-1 |
| Q5 | #performant — no felt stalls | Attaching four gallery photos shows progress and never freezes the UI; ten day-navigation taps and ten heatmap taps produce no stutter; typing in a ten-item meal stays smooth. Verified by a manual pass on the reference device, not by an automated benchmark. | ADR-6, ADR-8 |

---

## 11. Risks and Technical Debt

| Risk / Debt | Impact | Mitigation / Notes |
|-------------|--------|---------------------|
| No automated accuracy benchmark for LLM nutrition estimates (ADR-1, Q4) | Silent drift in estimate quality could go unnoticed between occasional spot-checks. | Acceptable at current scale (solo user, human review before save); revisit if trust model changes to "apply without review." |
| Hand-rolled AES/GCM key storage instead of a maintained library (ADR-3) | Any subtle bug in the custom crypto code directly risks key exposure — the exact risk it's meant to prevent. | No dedicated crypto-focused tests observed; consider a targeted test or migrating to Jetpack Security if this code is touched again. |
| No CI/CD (ADR-5) | Regressions can be committed without any automated check. | Deliberate tradeoff for a solo project; mitigated by the manual `compileDebugKotlin` + `testDebugUnitTest` habit documented in `AGENTS.md`. |
| Release build type reuses debug signing with optimization disabled | The app is not distributable in a real production sense; not a risk today, would block any future store release. | Out of scope unless distribution is ever desired. |
| No formal requirements/backlog document | Requirements live only in `AGENTS.md`/this doc and the developer's head. | Acceptable for a solo personal project. |

### 11.1 Accepted debt

Deliberately not done, with the reason. This table exists so a later audit does not re-raise settled decisions; each row is a decision, not an oversight.

| Accepted debt | Reason it stays |
|---------------|-----------------|
| Meal line items stay a JSON column instead of a relational table | A table rebuild against the one irreplaceable dataset, to speed up decoding a few hundred rows. |
| ~220 UI strings stay hardcoded in Kotlin (`res/values/strings.xml` holds only the app label) | One user, one language, no translation intent. |
| No indices on `createdAtEpochMillis` or any other column | Unmeasurable at a few hundred rows, and each index costs a migration. |
| Migrations 1→2, 2→3, 3→4, 5→6 and 6→7 are verified only empirically on-device | `exportSchema` was off until v10, so no historical schema JSON exists for `MigrationTestHelper` to validate against. Committed schemas start at v8; instrumented coverage exists for 4→5, 7→8 and 9→10. |
| The Room database stays inside Android auto-backup (`allowBackup=true`, no `vocalorie.db` exclusion) | Required by the `data-backup` capability as a passive safety net against an uninstall. The tradeoff: meal history — health-adjacent personal data — leaves the device into the user's cloud backup. Accepted because the sole user is the sole data subject and chose it. |
| Release builds reuse debug signing, R8 is off, and API keys reach `BuildConfig` in plaintext | The app is never published; `BuildConfig` values come from an uncommitted `local.properties` on the developer's own machine. |
| TOCTOU re-resolution in `requireSafeFetchUrl` is unaddressed — the guard resolves the host, then Ktor resolves it again independently | On a phone there is no cloud metadata service to reach, and the party choosing URLs is the app's own LLM, not an attacker. The vacuous DNS-failure pass and the unguarded redirect hops — the two exploitable gaps — were fixed. |
| The 8-field nutrition tuple is still spelled out across the mappers and DTOs | The four suites that are the app's only data safety net construct these DTOs **positionally**, so a wrapper type would force test edits in the same commit as production changes — the exact situation in which a test gets "fixed" to match a bug. The value type was introduced only where a transposition is silent and identically typed (`EditableNutrition`). |
| The tips strip crossfades inside a surface the `visual-baseline` capability screenshots | A rotating tip makes a pixel comparison of the stats header timing-dependent. Mitigated rather than removed: the tests assert the ranked list and the strip's presence, never the visible index, and setting the rotation interval to `0` stops all motion when a baseline is captured. |
| Copy-contract tests still assert production **source text** rather than behaviour | Rewriting them as behaviour tests mostly depends on string resources we are not adding. Capped by policy instead: deprecated-but-tolerated, no new ones, and any source-grep test must resolve by filename and fail loudly (`docs/agent/guidance/testing.md`). |

---

## 12. Glossary

| Term | Definition |
|------|------------|
| Koog | JetBrains' agentic framework used to orchestrate LLM calls and structured-output parsing for meal nutrition estimation. |
| BYOK | Bring-your-own-key — the user supplies their own OpenAI (and optionally Brave) API key rather than the app providing one. |
| Nutrition estimate | The structured LLM output for a meal: line items, totals, assumptions, warnings, and a confidence level. |
| `MealEntity` | The Room entity representing one persisted, reviewed meal. |
| Agent tools | LLM-callable capabilities (Brave Search, WebFetch) that ground a nutrition estimate with external information. Real calls only; active when a Brave API key is stored (ADR-7). |
| Contract test | `NutritionPromptContractTest.kt` — a test asserting the exact wording of the LLM system prompt/DTO contract, so changes are deliberate, not accidental. |
