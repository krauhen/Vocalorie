## Context

In Vocalorie, when a meal estimate is requested from text or attached photos, `MealCaptureViewModel.kt:270-341` launches a coroutine (`estimateJob = viewModelScope.launch { ... }`) that invokes `KoogNutritionAgent.estimate()`. During this call, the UI sets `isLoading = true` in `MealCaptureUiState.kt` and displays a `LoadingRow` with the current step from `estimationProgress` in `VoiceInputOverlay.kt:266`.

While this provides basic feedback, two limitations degrade the user experience:
1. **Lack of cancellation (F3)**: If the user notices an error in their meal description right after hitting "Estimate", they cannot stop the process. The button row disables all actions (`enabled = !isLoading && !isSaving`), trapping the user until the network call succeeds, fails, or times out after 30+ seconds.
2. **Lack of historical progress and turn visibility (F4)**: The existing `estimationProgress` replaces the status text on each event (e.g. Preparing -> SearchingSources -> ReadingSource -> CalculatingNutrition). The user cannot see how many agent turns have executed against the max iterations limit, nor what search queries or tool commands were dispatched.

Resolving these requires adding a direct cancellation mechanism that cancels the active `estimateJob`, resets loading, and preserves input fields, alongside an expandable progress history section that displays turn progression and executed tool commands.

## Goals / Non-Goals

**Goals:**
- Provide a Cancel action on the capture overlay whenever `isLoading == true`.
- Cancelling must immediately cancel the running coroutine job (`estimateJob?.cancel()`), dismiss loading indicators, and preserve user input (`query` text and `attachedImages`) intact so typos can be fixed without re-entry.
- Track agent turn count and executed tool commands during estimation and display them in chronological order in a collapsible section beneath the primary step line.
- Keep the collapsible section visible during loading, allow the user to expand or collapse it, and automatically clear and dismiss it when estimation finishes (on success or error).
- Keep the primary step line focused on user-facing descriptions of the active step.
- Ensure all business and state logic is covered by pure-JVM unit tests.

**Non-Goals:**
- No persistent storage of progress logs in Room or disk; progress items are strictly in-memory ephemeral UI state.
- No interactive agent stepping, pausing, or manual prompt adjustments mid-flight.
- No modifications to Room database tables, schemas, or backup files.
- Reasons are detailed in `proposal.md`.

## Decisions

### D1 — Cancel action placement and coroutine cancellation mechanism (F3)

**Decision.** In `VoiceInputOverlay.kt`, when `isLoading == true`, replace the "Estimate" button with a dedicated "Cancel" button (or provide an explicit Cancel action adjacent to the loading indicator). Tapping Cancel invokes `viewModel.cancelEstimate()`, which calls `estimateJob?.cancel()`. The coroutine's `finally` block or `CancellationException` handling resets `isLoading = false`, `estimationProgress = null`, and clears progress history, while leaving `query` and `attachedImages` completely intact in `MealCaptureUiState`.

**Alternative rejected.** Rely on dismissing the modal bottom sheet overlay to cancel in-flight work.

**Why it lost.** Closing the bottom sheet breaks user flow: if the user only wanted to fix a minor typo in their query, closing the sheet introduces unnecessary navigation churn and risks discarding uncommitted draft state. A direct in-place Cancel button keeps the form open and immediately editable.

**Alternative rejected.** Allow the background estimate coroutine to run to completion and ignore the result.

**Why it lost.** Running abandoned network operations wastes device battery and consumes billable OpenAI LLM tokens and Brave search queries for a request the user has explicitly aborted. Immediate coroutine cancellation ensures HTTP sockets and background work terminate promptly.

### D2 — Input preservation upon cancellation (F3)

**Decision.** When an estimate is cancelled, the ViewModel clears only transient loading states (`isLoading`, `estimationProgress`, `progressHistory`, `pendingEstimateRequest`). The user's entered description (`query`) and attached photos (`attachedImages`) remain untouched in `MealCaptureUiState`.

**Alternative rejected.** Call `onReset()` or clear the entire screen state when cancelling.

**Why it lost.** The primary motivation for cancelling an estimate is to correct typos, tweak amounts, or swap an attached image. Wiping the input fields forces the user to retype lengthy descriptions and re-select photos, frustrating the user.

### D3 — Stacked progress data model and agent event reporting (F4)

**Decision.** Define an immutable model for progress events (e.g. `EstimationStep(val turn: Int, val maxTurns: Int, val description: String, val command: String?)`) and accumulate them in `MealCaptureUiState.progressHistory: List<EstimationStep>`. `KoogNutritionAgent` will report turn index updates and tool execution events through the progress callback so both the active step and historical steps are captured.

**Alternative rejected.** Emit raw logcat messages or pipe text directly from the agent into a single concatenated string.

**Why it lost.** A structured list of strongly typed progress items allows Compose to render turns and commands with clean, semantic styling (badges, monospace command chips, turn counters) and keeps the data layer pure and testable.

### D4 — Collapsible section UX for progress history (F4)

**Decision.** In `VoiceInputOverlay.kt`, beneath the active step line (`LoadingRow`), provide a collapsible section ("Details" / "Execution steps") displaying the stacked list of executed commands and turn numbers. The section is visible only while `isLoading == true`. It defaults to collapsed to keep the overlay compact, but can be expanded with a single tap. Once estimation finishes (either producing a draft or failing with an error), the entire loading area including the collapsible log is dismissed.

**Alternative rejected.** Show all steps in an un-collapsible, continuously expanding list.

**Why it lost.** Multi-turn research with multiple web searches and page reads would rapidly expand the bottom sheet, pushing input fields and action buttons off-screen and creating visual clutter.

**Alternative rejected.** Keep only the single step line and omit tool commands entirely.

**Why it lost.** Without historical commands and turn counts, the user cannot tell how many tool calls have executed, what queries were searched, or whether the agent is making forward progress during long estimations.

### D5 — Spec amendment strategy for `estimation-progress`

**Decision.** Update the existing `estimation-progress` capability under `openspec/specs/estimation-progress/spec.md`. Specifically, modify "Requirement: An in-flight estimate names its current step" and "Scenario: Only the current step is shown" to specify that while the primary step line remains single-line, historical steps accumulate in the collapsible details log. Add new requirements for in-flight cancellation and stacked progress reporting.

**Alternative rejected.** Create separate capabilities named `estimation-cancellation` and `agent-progress-log`.

**Why it lost.** In-flight estimation feedback, step narration, and cancellation form a single cohesive user experience during the loading state of meal estimation. Fragmenting them across multiple capability folders creates unnecessary documentation overhead.

### D6 — Build-tooling and agent event loop assumptions

**Build/tooling assumption & Spike.** `KoogNutritionAgent` uses Koog's `AIAgent`. We assume `AIAgent` event handlers (`handleEvents`, `onToolCallStarting`, etc.) can reliably provide current iteration / turn index and tool parameters during execution. Gated behind Task 2.1 spike.

## Risks / Trade-offs

- **Immediate coroutine cancellation responsiveness**: If a blocking network fetch is underway in Ktor or an LLM call in OpenAI client, cancellation must be cooperative (`ensureActive()`, cancellable suspending functions).
  *Mitigation*: Both Ktor HTTP clients and coroutine-based prompt executors in Koog respect standard Kotlin coroutine cancellation out of the box.
- **UI jumpiness during rapid progress updates**: Multiple tool calls executing in quick succession could trigger frequent recompositions.
  *Mitigation*: Using immutable list state and keying Compose rows by step index or unique ID ensures smooth rendering without frame drops.

## Open Questions

None. Decisions D1 through D6 settle all aspects of cancellation and progress presentation.
