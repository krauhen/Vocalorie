## 1. In-flight estimation cancellation (F3 ViewModel & UI)

- [ ] 1.1 In `app/src/main/java/com/example/vocalorie/ui/capture/MealCaptureViewModel.kt:112-115`, confirm `estimateJob` lifecycle and expose a public `cancelEstimate()` function that cancels `estimateJob` with `estimateJob?.cancel()`. Ensure that cancellation cleans up `isLoading = false`, `estimationProgress = null`, and `progressHistory = emptyList()`, while strictly preserving `state.query` and `state.attachedImages`.
- [ ] 1.2 In `app/src/main/java/com/example/vocalorie/ui/capture/MealCaptureViewModel.kt:319-341`, adjust the `runEstimate` coroutine cancellation handling so that an explicit cancellation cleanly exits without registering an error in `state.error` or appending a failure diagnostic.
- [ ] 1.3 In `app/src/main/java/com/example/vocalorie/ui/voice/VoiceInputOverlay.kt:241-251`, update the primary action button row so that when `isLoading == true`, a prominent "Cancel" button is displayed (or the Estimate button becomes Cancel with `onClick = onCancelEstimate`). In `app/src/main/java/com/example/vocalorie/ui/MealCaptureScreen.kt:130-145`, wire `onCancelEstimate = viewModel::cancelEstimate`.
- [ ] 1.4 In `app/src/test/java/com/example/vocalorie/ui/capture/MealCaptureViewModelTest.kt`, add pure-JVM unit tests verifying that invoking `cancelEstimate()` cancels the active job, clears `isLoading`, and preserves the existing query string and attached image list.
- [ ] 1.5 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 2. Turn count and executed tool command tracking (F4 Data Model & Agent)

- [ ] 2.1 Spike: Inspect `AIAgent` event handlers in `app/src/main/java/com/example/vocalorie/ai/KoogNutritionAgent.kt:192-213` to confirm the exact API for intercepting agent iteration/turn indices and tool call arguments synchronously during execution without deadlocks.
- [ ] 2.2 In `app/src/main/java/com/example/vocalorie/ai/EstimationProgress.kt:5-25`, introduce models for step history (e.g. `EstimationStep(val turn: Int, val maxTurns: Int, val description: String, val command: String?)`) or extend `EstimationProgress` to convey turn numbers and executed tool command summaries.
- [ ] 2.3 In `app/src/main/java/com/example/vocalorie/ai/KoogNutritionAgent.kt:172-213`, hook into `AIAgent` iteration and tool invocation callbacks in `runGroundingAgent` to emit turn progress and executed tool commands (e.g. search query, fetch URL) to the `onProgress` callback.
- [ ] 2.4 In `app/src/main/java/com/example/vocalorie/ui/capture/MealCaptureUiState.kt:58-65`, add `val progressHistory: List<EstimationStep> = emptyList()` to `MealCaptureUiState`. In `MealCaptureViewModel.kt:300-310`, append incoming steps to `progressHistory` while `isLoading == true`, and reset the list to empty in `finally` and upon cancellation.
- [ ] 2.5 In `app/src/test/java/com/example/vocalorie/ai/KoogNutritionAgentTest.kt` and `app/src/test/java/com/example/vocalorie/ui/capture/MealCaptureViewModelTest.kt`, add pure-JVM unit tests verifying that progress events with turn indices and tool commands append to `progressHistory` in chronological order.
- [ ] 2.6 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 3. Collapsible progress details presentation (F4 UI)

- [ ] 3.1 In `app/src/main/java/com/example/vocalorie/ui/components/CommonUi.kt:220-255`, implement a composable `CollapsibleProgressDetails(steps: List<EstimationStep>, isExpanded: Boolean, onToggleExpanded: () -> Unit)` that renders a toggleable header with step count, and an expandable column listing executed turns and tool commands with distinct styling.
- [ ] 3.2 In `app/src/main/java/com/example/vocalorie/ui/voice/VoiceInputOverlay.kt:265-272`, embed `CollapsibleProgressDetails` directly beneath the active `LoadingRow(estimationProgress?.displayText())` when `isLoading == true` and `progressHistory.isNotEmpty()`. Ensure that completing the estimate (success or error) automatically dismisses the section.
- [ ] 3.3 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 4. Spec validation and on-device confirmation

- [ ] 4.1 Validate OpenSpec change specification: `openspec validate 2026-09-16-estimation-progress-and-cancellation --strict`.
- [ ] 4.2 Verify full test suite: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`.
- [ ] 4.3 On-device: Pull database before installation per `docs/agent/guidance/setup.md`.
- [ ] 4.4 Install debug build to connected device (`SM-S911B`): `./gradlew :app:installDebug --no-daemon`.
- [ ] 4.5 On-device test: Start an estimate with text and photos; verify Cancel button is visible; tap Cancel; verify loading dismisses immediately and input text and attached photos remain intact.
- [ ] 4.6 On-device test: Start a grounded estimate; tap the collapsible details toggle; verify turn counts (e.g. Turn 1, Turn 2) and executed commands (search queries, fetched URLs) appear stacked chronologically; verify the section dismisses automatically when the result draft appears.
- [ ] 4.7 Final verification: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`.
