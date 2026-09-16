## Why

When estimating nutrition for a meal via the capture overlay (`VoiceInputOverlay.kt`), the user experiences two significant points of friction during long-running LLM and search tool calls:

1. **Inability to cancel an in-flight estimate (F3)**:
   Once the user initiates an estimate (`onEstimate()`), the UI enters an un-cancellable loading state (`isLoading = true`). If the user immediately realizes they made a typo in the meal description (e.g. typing "5000g" instead of "500g", or forgetting a key ingredient), or if the remote LLM / tool call hangs, there is no way to cancel the running job. The user is forced to wait out network timeouts (up to 30–40 seconds) and waste billable OpenAI and Brave search API tokens. Furthermore, closing or resetting the sheet discards entered inputs. The user needs an immediate Cancel action that aborts the background request and restores the form with their query text and attached photos intact for immediate correction.

2. **No visibility into turn progression or executed tool commands (F4)**:
   Currently, `estimationProgress` displays only a single, replacing text line (e.g., "Looking for sources…", "Reading fddb.info…", "Computing nutrition values…"). It provides no record of how many turns the agent has completed versus its budget (e.g. Turn 2 of 5), nor what specific tool commands or search queries have been executed so far. Because each step overwrites the previous one, the user has no way of knowing whether the agent is looping unproductively or progressing through distinct sources. Providing a stacked, chronological history of turns and executed tool commands in a collapsible section gives full transparency into the estimation process without cluttering the main capture surface.

## What Changes

- **In-flight estimation cancellation (F3)**: When estimation is running (`isLoading = true`), the capture overlay displays an active "Cancel" button. Tapping Cancel immediately cancels the coroutine job (`estimateJob?.cancel()`), clears the loading and progress states, and leaves the entered query text and attached images completely intact in the input form so the user can fix mistakes immediately.
- **Stacked turn count and executed tool commands (F4)**: As the LLM agent advances through iterations, the system records agent turn numbers and executed tool commands (e.g., search queries, fetched source domains). These events are accumulated chronologically in the UI state.
- **Collapsible progress history section (F4)**: Beneath the active step line, a collapsible/expandable section presents the stacked history of turns and tool commands. The section is visible and toggleable during the loading state, and is automatically dismissed and cleared once the estimate finishes (on success or error).
- **Delta spec update**: Amends `openspec/specs/estimation-progress/spec.md` to update "Scenario: Only the current step is shown" to allow historical steps to accumulate within the collapsible details section, and introduces explicit requirements for cancellation and stacked execution progress.

## Capabilities

### Modified Capabilities

- `estimation-progress`: Update requirement "An in-flight estimate names its current step" to clarify that the primary step line shows the active step while past steps accumulate in the collapsible detail section; add requirement for in-flight cancellation retaining inputs; add requirement for stacked turn counts and executed tool commands in a collapsible section.

## Impact

- **UI presentation**: `ui/voice/VoiceInputOverlay.kt` renders a Cancel button when `isLoading = true` and embeds a collapsible section displaying stacked progress history under the active step line.
- **UI state & ViewModel**: `ui/capture/MealCaptureViewModel.kt` adds `cancelEstimate()` to abort `estimateJob` and reset loading while preserving `query` and `attachedImages`; `MealCaptureUiState.kt` adds fields for stacked progress history.
- **AI Agent integration**: `ai/KoogNutritionAgent.kt` and `ai/EstimationProgress.kt` emit turn counts and executed tool details through the progress callback mechanism.
- **Tests**: Pure-JVM unit tests in `MealCaptureViewModelTest.kt` and agent tests verifying cancellation, input preservation, and progress stacking.
- **No persistence or schema changes**: Zero Room database migrations, no DAO updates, and no changes to backup schemas.

## Non-goals

- **No server-side token rollback**: Aborting a request drops local coroutines and sockets; upstream OpenAI API token charges already processed cannot be revoked.
- **No persistence of progress history**: The stacked turn and command log is ephemeral UI state that clears when loading ends and is not saved to Room.
- **No manual stepping or pausing controls**: The agent executes continuously until completion, failure, or cancellation, without interactive pause-and-resume.
- **No changes to cached meal matching or draft saving**: Cancellation and progress logs apply exclusively to the in-flight estimation pipeline.
- **No alterations to image attachment or speech input**: Text input and image attachments remain intact on cancellation; their capture mechanisms are untouched.
