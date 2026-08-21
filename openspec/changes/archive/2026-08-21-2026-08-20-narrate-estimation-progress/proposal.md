## Why

Estimating a meal is the app's one slow moment, and it is completely opaque. From the tap on Estimate to the draft appearing, the overlay shows a single static line — `LoadingRow("Estimating…")` (`ui/voice/VoiceInputOverlay.kt:260`) — that never changes. Behind it the app is doing several distinct things: a grounding agent runs a research loop with a web-search tool and a web-fetch tool, bounded by `maxAgentIterations` and `maxResearchToolCalls` (`ai/KoogNutritionAgent.kt:181-194`), and only then does the estimating call itself run under a timeout with retries (`:148-152`).

For the user this reads as one indistinguishable wait. A run that is fetching its fourth source looks exactly like a run that is hung, so the only available response to a slow estimate is to wait it out or kill it — and killing it discards the grounding work that was nearly finished. There is also no after-the-fact signal: when grounding fails, the resulting warning says the estimate is unsourced but not how far the research got before it broke.

The wait itself is not the problem and this change does not try to make it shorter. The problem is that the wait carries no information. Modern LLM interfaces solved this with a small line that names the current step; this change gives the estimate the same treatment, using steps the app already knows about.

## What Changes

- **The estimation seam gains a progress channel.** `NutritionEstimator.estimate(...)` (`ai/KoogNutritionAgent.kt:41-48`) takes an optional progress callback with a no-op default, so every existing call site keeps compiling unchanged; the one fake in `src/test` that implements this interface, `FakeNutritionEstimator` (`ui/capture/FakeCaptureEnvironment.kt`), is updated to redeclare the new parameter, since Kotlin overrides must redeclare every parameter of an interface method.
- **Progress is emitted as a small closed set of semantic steps**, not as raw tool names or free text: preparing, searching for sources, reading a named host, and calculating nutrition. The UI owns the wording; the agent layer owns only which step it is in.
- **One real hook already exists and is already wired.** `onUrlFetched: (String) -> Unit` on `vocalorieToolRegistry` (`tools/AgentTools.kt:208-215`, wired at `ai/KoogNutritionAgent.kt:174-180`) fires exactly once per successfully fetched page, and that alone drives the "Reading <host>" step with no new library surface.
- **Koog's `EventHandler` feature supplies the remaining steps.** Its member names are verified against the resolved 1.0.0 artifact (design D6): `handleEvents { onToolCallStarting { ... } }` installed via the `AIAgent(...)` trailing `installFeatures` lambda.
- **The state holder gains one narrow in-flight field** beside `isLoading` (`ui/capture/MealCaptureUiState.kt:57`), modelled on `tipsRewordingInFlight` (`:74`), set from the callback inside `runEstimate` (`ui/capture/MealCaptureViewModel.kt:276-318`).
- **The static loading line becomes a live one.** `LoadingRow("Estimating…")` shows the current step's text instead of a fixed string.
- **New capability**: `openspec/specs/estimation-progress/spec.md`.
- **Layering**: progress travels agent → repository → state holder → UI only. The callback is passed *down* as a parameter and its values flow *up* as data; nothing below the state holder learns anything about the UI, per `docs/agent/guidance/coding.md`.
- **Backlog**: F1 closes as promoted to this change.

## Non-goals

- **No attempt to make estimation faster.** This change is about legibility of the wait; the timeout, retry and iteration bounds are untouched because they are tuned against real failures.
- **No cancel button.** Being able to abort a visibly-stuck estimate is a reasonable next ask, but it needs a scope and cancellation story of its own and would hide this change's actual behaviour behind a second one.
- **No token-level or "thinking" streaming.** The estimating call uses `executor.execute(...)` (`ai/KoogNutritionAgent.kt:151`), not a streaming API; switching it would change the response-parsing path that `JsonStructure.parse` depends on.
- **No progress for saving, image preparation, or tip rewording.** They have their own loading rows (`ui/voice/VoiceInputOverlay.kt:261,536`) and are short; adding steps there would spend the same plumbing on waits nobody complains about.
- **No accumulated step log or diagnostic panel.** The existing `diagnostic` field already carries after-the-fact detail; a second history surface would duplicate it.
- **No persistence of progress.** It exists only for the duration of one estimate; nothing about it survives the call, so there is no Room change, no schema bump, and no `BACKUP_SCHEMA_VERSION` move.
- **Nothing about F2, F3 or F4.** Separate capabilities, separate proposals.
