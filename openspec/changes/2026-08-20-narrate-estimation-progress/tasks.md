## 1. Spike: confirm Koog's event surface in the resolved 1.0.0 artifact (unverified assumption, design D6)

- [ ] 1.1 Resolve the Koog artifact pinned at `gradle/libs.versions.toml:6` and inspect `ai.koog.agents.features.eventHandler.feature.EventHandlerConfig` in it (IDE navigation into the resolved jar, or `javap`/`unzip -l` over the artifact in the Gradle cache). Record the exact member names present.
- [ ] 1.2 Record which of these exist, by their exact names: a per-tool-call starting callback, a per-tool-call completed callback, an LLM-call starting callback, and the install entry point (`handleEvents { }` versus `install(EventHandler.Feature) { }`) accepted inside the trailing config lambda of `AIAgent(...)` as constructed at `app/src/main/java/com/example/vocalorie/ai/KoogNutritionAgent.kt:181-188`.
- [ ] 1.3 Write the outcome into this file as a checked note: either the confirmed names that groups 2 and 4 will use, or "no usable event API in 1.0.0". A negative result is a valid outcome — per design D6 the change then ships the reading step from `onUrlFetched` (`app/src/main/java/com/example/vocalorie/tools/AgentTools.kt:208-215`) alone, and tasks 2.4 and 4.4 are struck rather than the change abandoned.
- [ ] 1.4 Confirm no dependency change is needed: the event-handler feature ships inside the umbrella artifact already declared, or the spike stops here and the negative path in 1.3 applies. Adding a dependency is out of scope for this change (`docs/agent/hard-rules.md`).
- [ ] 1.5 Verify: `./gradlew :app:compileDebugKotlin --no-daemon` still passes with no source change, confirming the spike changed nothing.

## 2. The progress type and the seam (estimation-progress plumbing)

- [ ] 2.1 Add a sealed `EstimationProgress` type in `app/src/main/java/com/example/vocalorie/ai/` beside `NutritionEstimateOutcome` (`ai/KoogNutritionAgent.kt:57-60`), with exactly four cases: `Preparing`, `SearchingSources`, `ReadingSource(host: String)`, `CalculatingNutrition`. Pure Kotlin, no Android types, so it is JVM-testable per `docs/agent/guidance/testing.md`. It carries no display strings (design D2).
- [ ] 2.2 Add `onProgress: (EstimationProgress) -> Unit = {}` as the last parameter of `NutritionEstimator.estimate(...)` (`ai/KoogNutritionAgent.kt:41-48`) and of its Koog implementation. The default keeps every existing call site and every test fake compiling unchanged — confirm that by compiling before touching any caller.
- [ ] 2.3 Emit from the implementation: `Preparing` before the grounding decision (`ai/KoogNutritionAgent.kt:101-104`); `SearchingSources` immediately before `agent.run(researchInput)` (`:194`); `ReadingSource(host)` from inside the existing `onUrlFetched` lambda (`:174-180`), taking the host from the value `normalizeSourceUrl(...)` already produces there (design D5); `CalculatingNutrition` immediately before the `withBoundedRetry { ... }` block (`:148-152`), once for the whole phase including retries (design D7).
- [ ] 2.4 If and only if task 1.3 confirmed the event API: install it in the `AIAgent(...)` config lambda (`:181-188`) using the confirmed names, emitting `SearchingSources` on the search tool's call-starting event. Map the tool identity to the progress case in this layer — the raw tool name never leaves it.
- [ ] 2.5 Add `app/src/test/java/com/example/vocalorie/ai/EstimationProgressTest.kt` covering the host-extraction helper used by 2.3 as a pure function: a long URL with a path and query yields its host alone; a URL that fails normalization yields no emission. Do not add a test that runs the real agent — `src/test` is pure JVM with no network (`docs/agent/guidance/testing.md` rule 1).
- [ ] 2.6 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 3. The state field (capture state holder)

- [ ] 3.1 Add `val estimationProgress: EstimationProgress? = null` to `app/src/main/java/com/example/vocalorie/ui/capture/MealCaptureUiState.kt`, in the new-meal capture block beside `isLoading` (`:57`), modelled on the narrower in-flight field `tipsRewordingInFlight` (`:74`). Do not add it to `isBusy` (`:116`) — `isLoading` already covers "an estimate is running", and this field is descriptive, not a gate.
- [ ] 3.2 In `runEstimate` (`app/src/main/java/com/example/vocalorie/ui/capture/MealCaptureViewModel.kt:277-316`), pass `onProgress = { update { state -> state.copy(estimationProgress = it) } }` to `nutritionEstimator.estimate(...)` (`:286-291`), routing every emission through the existing `update { }` path so an off-main-thread callback cannot touch state directly (design D1).
- [ ] 3.3 Clear the field in the existing `finally` block (`:314-316`) alongside `isLoading = false`, so it is gone on both the success and the failure path (design D4).
- [ ] 3.4 In the two failure `catch` branches (`:303-313`), append the last-seen step to the `diagnostic` text using the same `listOfNotNull(...).joinToString("\n\n")` pattern already used for the grounding diagnostic (`:296-299`), so a failure while reading a source is distinguishable from one before any request was made.
- [ ] 3.5 Add a JVM test for the state holder driving a fake `NutritionEstimator` that invokes `onProgress` with a known sequence: the state field holds the latest emitted step during the call, is null after a successful call, and after a failing call is null while the diagnostic names the last step. Place it beside the existing capture state-holder tests under `app/src/test/java/com/example/vocalorie/ui/capture/`.
- [ ] 3.6 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 4. The live step line (capture UI)

- [ ] 4.1 Add a pure `fun EstimationProgress.displayText(): String` in the UI layer beside `LoadingRow` (`app/src/main/java/com/example/vocalorie/ui/components/CommonUi.kt:203`), mapping each case to its user-facing wording — preparing the estimate, looking for sources, reading `<host>`, computing nutrition values. Wording lives here and only here (design D2). Keep it out of the composable body so it is JVM-testable (`docs/agent/guidance/testing.md` rule 3).
- [ ] 4.2 Replace `if (isLoading) LoadingRow("Estimating…")` (`app/src/main/java/com/example/vocalorie/ui/voice/VoiceInputOverlay.kt:260`) with the progress-driven line: when a step is present show its `displayText()`, otherwise keep the existing fallback wording so the line is never empty while loading. Leave `:261` (`"Saving locally…"`) and `:536` (`"Preparing photos…"`) untouched — they are out of scope.
- [ ] 4.3 Thread the new state field into the overlay's parameters the same way `isLoading` is threaded today; no composable reads the state holder directly, per `docs/agent/guidance/coding.md`.
- [ ] 4.4 If task 1.3 was negative: confirm on this path that a research-enabled run still shows the reading step from `onUrlFetched`, and that only the searching step is missing. Record it.
- [ ] 4.5 Add a JVM test for `displayText()` covering all four cases, including that `ReadingSource("fddb.info")` renders the host and that no case renders a tool identifier.
- [ ] 4.6 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 5. Specs, backlog and on-device confirmation

- [ ] 5.1 Install: `./gradlew :app:installDebug --no-daemon`
- [ ] 5.2 On-device check, research enabled: estimate a meal that needs lookups and confirm the line moves through preparing, looking for sources, at least one `Reading <host>`, and computing — and that it disappears when the draft appears.
- [ ] 5.3 On-device check, research disabled (no Brave key, or `maxResearchToolCalls` set to 0 in settings): confirm the line still shows the preparing and computing steps and never goes blank while the estimate runs.
- [ ] 5.4 On-device check, failure path: force a failure (for example by clearing the OpenAI key mid-run or going offline) and confirm the line clears, the error card appears, and its diagnostic names the step that was in flight.
- [ ] 5.5 Confirm the backlog reflects the promotion — the F1 file is removed from `docs/agent/backlog/features/` and `docs/agent/backlog/features/README.md` lists it under `## Promoted` pointing at this change. Done when the proposal was written; no edit expected. Its investigation is in git history if the file:line current state is needed.
- [ ] 5.6 Verify: `openspec validate 2026-08-20-narrate-estimation-progress --strict` passes, and `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon` is green.
