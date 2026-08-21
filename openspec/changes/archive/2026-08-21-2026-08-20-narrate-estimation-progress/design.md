## Context

Estimation is a two-phase operation hidden behind one `suspend` function.

`NutritionEstimator.estimate(...)` (`ai/KoogNutritionAgent.kt:41-48`) returns `NutritionEstimateOutcome` and offers no channel of any kind — no `Flow`, no callback, no shared state. Inside it:

1. **Grounding** (`:101-116`, `:165-195`) — optional, gated on `hasBraveApiKey && maxResearchToolCalls > 0`. It builds a `ToolRegistry` holding `BraveSearchTool` and `WebFetchTool` (`tools/AgentTools.kt:208-215`) and runs `AIAgent(...).run(researchInput)` inside `runGroundingAgent` (`:165-195`), a loop bounded by `maxAgentIterations`. Failure here is caught and downgraded: the estimate continues ungrounded and the throwable is carried out in `groundingFailureMessage`.
2. **Estimating** (`:118-163`) — one `executor.execute(prompt, model, emptyList())` under `ESTIMATE_REQUEST_TIMEOUT_MS` with `withBoundedRetry` (the retry block sits at `:149-153`), whose text is parsed by `outputStructure.parse(responseText)`.

Only phase 1 is stepwise, and it is exactly the phase that takes the variable, unbounded-feeling time. It already has one real per-step hook: `onUrlFetched: (String) -> Unit`, wired at `:174-180` and called by `WebFetchTool` only after a 2xx response. Nothing else in the repo observes agent execution — the only Koog surfaces used are `AIAgent(...)` and `.run(...)`.

Above the seam, `runEstimate` (`ui/capture/MealCaptureViewModel.kt:276-318`) brackets the call with `isLoading = true` / `false`, and `ui/voice/VoiceInputOverlay.kt:260` renders `LoadingRow("Estimating…")` off that boolean. The precedent for a second, narrower in-flight field is `tipsRewordingInFlight` (`ui/capture/MealCaptureUiState.kt:74`) beside `isLoading` (`:57`), with the two combined into `isBusy` (`:116`) only where "anything in flight" is what matters.

## Goals / Non-Goals

**Goals:**
- The user can tell a progressing estimate from a hung one, at a glance, without opening anything.
- Steps are named in the user's terms, never in the app's internal vocabulary.
- The seam change is additive: no existing call site, fake, or test signature moves.
- Progress flows strictly downward-as-parameter and upward-as-data, keeping the one-directional layering rule intact.
- The feature degrades to fewer steps rather than failing, if the Koog event API is not what the docs describe.

**Non-Goals:** making estimation faster, a cancel affordance, token-level streaming, progress for save/image/tips paths, an accumulated step log, any persistence. Reasons are in `proposal.md`.

## Decisions

### D1 — Progress travels as a callback parameter on `estimate(...)`, with a no-op default

**Decision.** Extend the seam to `suspend fun estimate(..., onProgress: (EstimationProgress) -> Unit = {}): NutritionEstimateOutcome`. The state holder passes a lambda that posts into UI state; everything below just calls it.

**Alternative rejected.** Change the return type to `Flow<EstimationProgress>` terminating in the outcome.

**Why it lost.** It rewrites the shape of every consumer for one narrow need. `runEstimate` (`ui/capture/MealCaptureViewModel.kt:283-316`) is built around a single `val outcome = ...` inside a `try/finally`; a `Flow` turns that into a collect loop with the terminal value extracted from the stream, and every fake `NutritionEstimator` in `src/test` has to be rewritten to emit rather than return. The two-phase internals also do not compose naturally into a flow — grounding failure is *caught and downgraded* (`ai/KoogNutritionAgent.kt:107-113`), which a flow would have to model as a non-terminal error.

**Alternative also rejected.** A shared mutable progress holder (an object or `StateFlow`) that the agent layer writes and the UI observes.

**Why it lost.** It gives a lower layer a reference to something the UI reads, which is the upward reach `docs/agent/guidance/coding.md` forbids, and it introduces shared mutable state across coroutine boundaries for a value only one caller ever wants. A parameter passed down keeps the dependency arrow pointing the right way.

**Consequence to call out.** The callback may be invoked from whatever thread Koog runs its tools on, so the state holder's lambda must marshal onto its own update path rather than touching UI state directly — the same discipline `onUrlFetched` already needs where it mutates a set built by `newFetchedUrlSet()` (`ai/KoogNutritionAgent.kt:102`).

### D2 — Progress is a closed set of semantic steps, not tool names or free text

**Decision.** Define a small sealed type in the agent layer — preparing, searching for sources, reading a source (carrying a host), calculating nutrition — and let the UI map each to display text.

**Alternative rejected.** Emit the raw tool name and arguments from the Koog event callback straight through to the line.

**Why it lost.** The user would read `web_fetch` and `brave_search`. Those are the app's internal tool ids (`tools/AgentTools.kt:214-215`), so the loading line becomes a leak of implementation naming that changes whenever a tool is renamed — and renaming a tool would silently change user-facing copy.

**Alternative also rejected.** Emit ready-made display strings from the agent layer.

**Why it lost.** It puts UI copy below the repository boundary, where it cannot be styled, localised, or shortened for the overlay's width, and it makes the agent layer the owner of wording it has no business owning.

### D3 — The line shows only the current step

**Decision.** One step at a time, replacing the previous one. The state field holds a single nullable value.

**Alternative rejected.** Accumulate a short scrolling history of steps.

**Why it lost.** The line lives inside the capture overlay directly above the draft editor (`ui/voice/VoiceInputOverlay.kt:260-273`); a growing list would push the rest of the overlay around while the user waits, and a research loop bounded by `maxResearchToolCalls` can produce more entries than the space allows. The question this feature answers — "is it still going?" — is answered by the latest step alone.

### D4 — On failure the last step is folded into the error's diagnostic, and the live line clears

**Decision.** When the estimate ends, successfully or not, the progress field is cleared in the same `finally` that clears `isLoading` (`ui/capture/MealCaptureViewModel.kt:314-316`). On failure, the step that was in flight is appended to the `diagnostic` text the error card already shows.

**Alternative rejected.** Leave the last step visible beside the error message.

**Why it lost.** A step line that stays on screen after the run stops reads as still-running; that is the exact confusion this change exists to remove, reintroduced at the worst moment. The error card (`ui/components/CommonUi.kt:280-281`) is already the surface for after-the-fact detail and already carries `diagnostic`.

**Alternative also rejected.** Discard the step entirely on failure.

**Why it lost.** "Failed while reading example.com" and "failed before it ever reached the network" are different failures with different responses from the user, and today they are indistinguishable.

### D5 — The reading step carries the full URL; the UI reduces it to a host

**Decision.** `ReadingSource` carries the full normalized URL produced by the existing `normalizeSourceUrl(...)` path already applied at `ai/KoogNutritionAgent.kt:177`. `normalizeSourceUrl()` does not produce a host — it produces a full normalized URL (scheme, host, path, query, fragment). The UI reduces that to a host for display, via the existing `sourceDomainOrUrl()` (`ui/components/CommonUi.kt:266`), so the line still reads "Reading fddb.info…".

**Alternative rejected.** Show the full URL.

**Why it lost.** Food-database URLs are long and query-laden; in a single-line indicator they truncate to noise, and they can carry the search text the user just spoke back at them in encoded form.

### D6 — The Koog event API is verified against the resolved 1.0.0 artifact

**Decision.** Koog's `EventHandler` feature is the source for the searching step. Verified in the resolved artifact: `ai.koog.agents.features.eventHandler.feature.EventHandlerConfig` exists, with suspend, additive (not replacing) setters `onToolCallStarting`, `onToolCallCompleted`, `onToolCallFailed`, `onLLMCallStarting`, `onAgentStarting`, `onAgentCompleted`. It is installed via `handleEvents { }` (import `ai.koog.agents.features.eventHandler.feature.handleEvents`), accepted inside the trailing `installFeatures` lambda of the `AIAgent(...)` overload already used at `ai/KoogNutritionAgent.kt:181-188`, whose receiver is `GraphAIAgent.FeatureContext`. `ToolCallStartingContext` exposes `toolName: String` (non-null), plus `toolCallId`, `runId`, `toolArgs`. No dependency change is needed — the feature ships inside the umbrella artifact already declared, on the compile classpath via koog-agents' api variant.

**Alternative rejected.** Write the plumbing against the documented names and fix it if it fails to compile.

**Why it lost.** Before verification, `openspec/config.yaml`'s design rule required an unverified build-tooling assumption to be named and gated rather than assumed to compile. That verification is now done, so the plumbing proceeds directly against the confirmed names above.

### D7 — The estimating phase emits one step, not several

**Decision.** The second phase (`ai/KoogNutritionAgent.kt:148-152`) emits a single "calculating nutrition" step for its whole duration, including retries.

**Alternative rejected.** Narrate each retry attempt ("attempt 2 of 3").

**Why it lost.** `withBoundedRetry` exists to make transient failures invisible; surfacing its attempts turns a handled condition into an alarming one, and a user who sees "attempt 3" has no action available that waiting does not already cover.

## Risks / Trade-offs

- **A callback invoked off the main thread.** Called out in D1; mitigated by routing every emission through the state holder's existing `update { }` path rather than assigning state directly.
- **Rapid step changes could flicker.** A research loop can fetch several pages quickly, so the line may change faster than it can be read. Accepted: movement is itself the signal this change is buying, and the alternative (a minimum dwell time per step) would make the line lag behind reality.
- **A grounded run is narrated much more richly than an ungrounded one.** With no Brave key, grounding is skipped entirely (`ai/KoogNutritionAgent.kt:101`) and only the preparing and calculating steps ever appear. That is honest — there genuinely are fewer steps — but it means the feature's value is uneven across configurations.

## Open Questions

None. D6's Koog event API is verified against the resolved artifact.
