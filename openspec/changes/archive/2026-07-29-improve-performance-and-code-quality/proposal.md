## Why

A full read-only audit of all 68 source files found ~90 performance and code-quality issues. They are not scattered accidents — they trace to three structural causes, and several have already produced user-visible defects.

- **No state holder exists anywhere.** Zero ViewModels, zero `Flow`. `ui/MealCaptureScreen.kt` is one 746-line composable holding 35 `mutableStateOf` slots while owning database access, settings persistence, backup import/export, AI orchestration, routing and four overlays — with seven repository-shaped functions declared *inside* the composable body. That single file is why most other findings are expensive to fix: the data layer has no callable surface, so it cannot be tested, reused, or made reactive. It also means rotation cancels an in-flight OpenAI call and discards the review draft and attached photos, and a save can commit the meal row while its cache write is cancelled.
- **The persistence layer is structurally blocking.** The four Room files are Java, and a Java `@Dao` cannot be `suspend` or return `Flow`. Reactivity is therefore hand-rolled: nine manual refresh sites re-read whole tables after every write, and a primary-key cache lookup runs as a linear scan over an in-memory copy of the entire table held in Compose state.
- **Nothing is written down.** `agentic/guidance/CODING.md` is 15 lines and mandates no architecture at all; `TESTING.md` is 9 lines and mandates essentially nothing. The code violates no documented standard because there is no documented standard — so the same problems recur.

Three findings are urgent independently of the architecture: attaching photos freezes the UI for seconds on the app's primary input path; the live-network OpenAI test is not `@Ignore`d, so the repo's own mandated verification command bills a real API key on every run; and `cached_meals` has no `category` column, so **every cache hit silently downgrades a meal to `OTHER`**, quietly regressing the food-type feature shipped in the previous change.

### Verified on device

A walkthrough of every screen on the physical Samsung Galaxy S23 (`SM-S911B`) captured a 20-frame visual baseline and confirmed three things.

Two audit findings are already visible defects in real data, not theoretical risks:

- Settings shows *Calories burned per 1,000 steps* as **29.999999329447746** where `30` was entered — the `kcalPerStep`-persisted-as-`Float` defect.
- The meal editor renders **816.6500000000000004** kcal, **5.7000000000000004** g saturates and **3.450000000000004** g salt, and an item card renders **1.7500000000000002** g salt — the `Double.toString()` defect in the editable-number formatter.

The cost concern is live on this device: a Brave key is saved and the agent workflow step limit is **64**, so grounding runs on every parse at up to 64 LLM turns.

And the current visual design is explicitly worth preserving: calorie-state tinting that distinguishes a 1223 kcal meal from a 190 kcal one at a glance, macro colour coding, per-row food-type icons, the score-gradient heatmap, and the green/blue palette swap between the Meals and Activities tabs. **This change must be visually invisible apart from an enumerated allowlist**, which is why `visual-baseline` is one of its capabilities.

## What Changes

**P1 — Responsiveness (felt performance)**
- Move gallery-attachment processing (read, EXIF parse, two bitmap decodes, rotate, two rescales, JPEG compress, up to 4 images) off the main thread with a progress indicator.
- Memoize whole-history stats: `computeMealStats` currently takes `Instant.now()` and is unremembered, so it re-runs three fold passes, two streak walks and two 98-day sequence builds on every recomposition — every day-navigation and heatmap tap. Hoist the clock to a parameter; precompute heatmap cell scores and colours.
- Make the meal editor skip: child lambdas capture the changing draft, so a 10-item meal recomposes ~110 text fields per character typed. Replace the 8-positional-`String` nutrition callback with one value type, which also removes a hazard where mis-ordering silently swaps carbs with sugar.
- Remove per-recomposition `SharedPreferences` reads and AndroidKeystore crypto from composition, and the unremembered per-keystroke meal search.

**P2 — Cost and silent failure in the AI path**
- Reuse one HTTP client and one prompt executor. Today a new `HttpClient(Android)` is built per tool invocation (up to 32 per parse) and a `MultiLLMPromptExecutor` + `OpenAILLMClient` + Ktor factory is built per estimate and never closed, leaking engine threads.
- Add a request timeout and bounded retry to the estimate call; a hung socket currently pins the loading state until force-kill. Lower the grounding agent's default iteration cap from 64 — it is the dominant latency and cost term.
- Surface grounding failures instead of discarding them, and treat a non-2xx Brave response as a failure rather than a legitimate empty result. Both currently degrade silently, so a bad key produces a plausible ungrounded estimate with no signal.
- Bound the WebFetch body read (currently the whole body is read before truncating to 4000 chars, with the URL chosen by the LLM), fail closed on DNS resolution failure, and validate redirects per hop.
- Extract a `NutritionEstimator` interface and an `HttpTextFetcher` seam so this layer becomes testable without live network calls.

**P3 — Data integrity**
- **BREAKING** (data): additive migration v9 → v10 adding `category` to `cached_meals`, fixing the silent food-type loss on every cache hit.
- Accept backup schema versions 8–10 on import. `BACKUP_SCHEMA_VERSION` is already out of sync (8 vs DB 9), and bumping it against the current equality check would reject every backup file the user has already exported.
- Stop destroying user data on failure paths: a decrypt failure currently deletes the stored API key outright; malformed item JSON becomes a real-looking 0-kcal meal; an unknown activity type silently becomes `RUNNING`.
- Leave the nutrition database inside Android auto-backup. The audit flagged that `allowBackup="true"` excludes only the two API-key preference files, so the complete meal and activity history reaches cloud backup and device transfer — but the existing `data-backup` capability requires exactly that as a deliberate passive safety net for irreplaceable history. The privacy tradeoff is recorded in `docs/arc42.md` §11 as accepted debt so it stays a visible decision rather than an unnoticed side effect.

**P4 — Architecture and testability**
- Introduce a state holder plus a repository layer: composables render an immutable `UiState` and emit events, repositories own `Dispatchers.IO` and expose `Flow`, and the database becomes the single source of change notification — deleting all nine manual refresh sites.
- Convert the Room layer to Kotlin with `suspend`/`Flow` DAOs, keyed cache lookups instead of full-table scans, `@Transaction` for the multi-write save and the backup export (there are currently zero transactions in the codebase), and a stats projection that reads the eight stored total columns instead of decoding item JSON.
- Release the microphone when the app is backgrounded, and stop a pending restart from overriding an explicit Stop.
- Deduplicate the byte-identical `EntryTimestampField` (which duplicates a bug that clobbers mid-edit text), the byte-identical Keystore crypto across two stores, and the three editable-number formatters with divergent numeric semantics.
- Record the resulting rules in `agentic/guidance/CODING.md` and `TESTING.md`, and the deliberately accepted debt in `docs/arc42.md` §11.

**P5 — Visual invariance**
- Treat the captured 20-frame device baseline as the acceptance reference: every stage is re-walked on the S23 and compared against it, and any visual difference outside the allowlist below is a defect in this change rather than an improvement.
- The allowlist is exactly: raw `Double` precision no longer leaking into numeric fields and labels; a progress indication while gallery attachments are prepared; and a warning surfaced on an estimate whose grounding pass failed.
- Two pre-existing layout squeezes found during the walkthrough are fixed as optional polish, clearly separable if not wanted: the portion-scaling "All" chip renders its label vertically (`A`/`l`/`l`) because four chips are squeezed into the row, and the item card's "Carbohydrate g" label wraps mid-word.

**Explicitly out of scope**, recorded as accepted debt rather than silently dropped: converting items-as-JSON to relational tables, externalising ~220 hardcoded strings, indices at current data scale, retroactive migration tests for v1–v8 (no historical schema JSONs exist), excluding the database from auto-backup (the `data-backup` capability deliberately requires its inclusion), R8/release-signing hardening for an app that is never published, and a DI framework or navigation library (ADR-5 rejected both).

## Capabilities

### New Capabilities
- `ui-responsiveness`: no bitmap decode, crypto, preference read or JSON decode on the main thread; whole-history derivations are memoized rather than recomputed per recomposition.
- `app-architecture`: UI → state holder → repository → DAO layering with unidirectional data flow; an in-flight estimate, review draft and attached photos survive configuration change; a reviewed save commits meal and cache rows atomically.
- `secret-storage`: a decrypt failure never deletes the stored key, and key crypto never runs on the main thread.
- `voice-input`: the microphone is released when the app is backgrounded, and a Stop request is never overridden by a pending restart.
- `visual-baseline`: the app's appearance is unchanged by this work except for an enumerated allowlist, and numeric values render in human-readable form rather than leaking raw floating-point precision.

### Modified Capabilities
- `food-sources`: a grounding failure surfaces to the user instead of silently degrading to an unsourced estimate; a non-2xx Brave response is a failure rather than an empty result; fetched source URLs are collected race-free; fetches are size-bounded and redirect-guarded.
- `meal-caching`: a cache hit preserves the meal's food-type category (currently always lost); cache lookup is a keyed point lookup rather than a full-table scan held in UI state.
- `data-backup`: import accepts schema versions 8–10 so existing exports stay readable; export runs in a transaction so it cannot capture a torn snapshot.
- `image-attachment`: attachment processing runs off the main thread with a progress indicator.
- `meal-stats-overview`: stats and heatmap recomputation is memoized and reads stored meal totals instead of decoding item JSON.

## Impact

- **Dependencies** (approved): KSP plugin replacing `annotationProcessor` for Room, `room-ktx`, `lifecycle-viewmodel-compose`, `kotlinx-coroutines-test`, `ktor-client-mock`, and a currently non-existent `androidTestImplementation` configuration (`room-testing`, test runner, `ext:junit`).
- **Build** (approved): `app/build.gradle.kts`, `gradle/libs.versions.toml`, `exportSchema = true` plus a committed `app/schemas/` directory and `testInstrumentationRunner`. **Gated:** this project uses AGP 9.2.1's built-in Kotlin with no `org.jetbrains.kotlin.android` plugin, so KSP compatibility must be spiked before anything depends on it; a repository-level fallback preserves every downstream benefit if it is not clean.
- **UI**: `ui/MealCaptureScreen.kt` (746 → ~150 lines), new `ui/capture/` state holder and state types, `ui/settings/SettingsScreen.kt` (39 parameters → `state`/`onEvent`/`onBack`), `ui/entries/MealEntriesScreen.kt` (927 lines, split along its seams), `ui/entries/stats/MealStatsOverview.kt`, `ui/components/MealEditor.kt`, `ui/components/ActivityEditor.kt`, `ui/components/CommonUi.kt`, `ui/voice/VoiceInputOverlay.kt`, `ui/VocalorieTheme.kt`, `MainActivity.kt`.
- **Data**: `data/MealDao.java`, `ActivityDao.java`, `CacheDao.java`, `VocalorieDatabase.java` converted to Kotlin; new `data/repository/`; additive `MIGRATION_9_10`, schema v9 → v10; `data/MealMappers.kt`, `ActivityMappers.kt`, `CachedMealEntity.kt`, `VocalorieBackup.kt`.
- **AI/tools**: `ai/KoogNutritionAgent.kt` (interface seam, client lifecycle, timeouts, cause-chain error mapping), `tools/AgentTools.kt` (client reuse, status checks, bounded reads, redirect and DNS guards), `settings/ToolSettings.kt`.
- **Settings**: shared Keystore codec extracted from `OpenAiApiKeyStore.kt` and `ToolSettingsStore.kt` (~70 duplicated lines); nutrition settings split out of `ThemeSettingsStore.kt`; `kcalPerStep` persisted as `Double` rather than `Float`.
- **Manifest/resources**: unchanged. `AndroidManifest.xml`, `res/xml/backup_rules.xml` and `res/xml/data_extraction_rules.xml` keep the database inside auto-backup, per the existing `data-backup` requirement.
- **Tests**: `ai/KoogNutritionAgentLiveHarnessTest.kt` becomes `@Ignore`d; the five copy-contract tests are retargeted to resolve files by name and fail loudly on 0 or multiple matches (11 hardcoded source paths currently block any file move); `ai/NutritionPromptContractTest.kt` may need a deliberate update since it asserts literal implementation syntax; new behaviour tests for the tool layer, the extracted pure policies and the state holder. The six existing behaviour suites (`MealTimeWindowsTest`, `MealStatsCalculatorTest`, `MealMappersTest`, `MealCacheTest`, `VocalorieBackupTest`, `MealDraftTotalsTest`) are the regression net and stay green unmodified wherever possible.
- **Documentation**: `agentic/guidance/CODING.md`, `agentic/guidance/TESTING.md`, `agentic/guidance/WORKFLOW.md`, `agentic/README.md`, `AGENTS.md` (six stale claims, including an inverted statement that agent tools default to mocks with network opt-in), `docs/arc42.md` (superseding ADR-4, new ADRs, a `#performant` quality goal, schema version corrections, §11 accepted debt), `openspec/config.yaml` (the commented-out `context`/`rules` blocks).
- **Verification artefacts**: a 20-frame device baseline captured on the S23 before any change, held outside the repository — `AGENTS.md` forbids committing screenshots or copied personal data, and the frames contain real meal history.
