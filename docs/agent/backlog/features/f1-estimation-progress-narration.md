---
description: Show what the estimation is doing — tool calls and steps — as a small live-updating line, like modern LLM thinking indicators.
tags: [backlog, features, estimation, ux]
---

# F1: Narrate estimation progress

**Status:** investigated
**Source:** personal note, 2026-08-20
**Likely capability:** a new capability spec; touches `openspec/specs/voice-input/spec.md`, `openspec/specs/image-attachment/spec.md`, `openspec/specs/food-sources/spec.md` (guess, not a commitment)

## Raw note (verbatim)
> F1: Wenn estimating show what tool calls etc. Are done with a small twxt that upsates similar to the thinking of modern LLMs

## What it means
While a meal estimate runs, replace the opaque wait with a small single line of text that updates
as the agent works — naming the current step, e.g. transcribing, looking up a food source, or
computing macros. Same idea as the streaming "thinking" line in modern LLM chat UIs: it makes a
multi-second wait legible and shows the estimate is progressing rather than hung.

## Open questions
- Which steps are actually observable? The Koog agent would need to emit progress events upward
  through repository → state holder → UI without breaking the one-directional layering rule.
- Raw tool names, or friendly labels mapped per tool? Raw names would leak internals.
- Does the line show only the current step, or accumulate a short history?
- What happens on failure — does the last step stay visible as context for the error?

## Investigation (2026-08-20)
The seam is one-shot, so this needs a plumbing change before any UI work.

- `NutritionEstimator.estimate(...)` (`ai/KoogNutritionAgent.kt:41-48`) is a plain `suspend`
  returning `NutritionEstimateOutcome` — no Flow, no callback. Called at
  `ui/capture/MealCaptureViewModel.kt:286`, bracketed by `isLoading = true/false` (`:277`, `:316`).
  The UI shows a static `LoadingRow("Estimating…")` (`ui/capture/VoiceInputOverlay.kt:260`).
- Two phases already exist internally and could be narrated cheaply: the grounding agent
  `AIAgent.run(...)` (`KoogNutritionAgent.kt:181-194`), then `executor.execute(...)` (`:151`).
- **A real per-tool-call hook already exists and is wired:** `onUrlFetched: (String) -> Unit` on
  `vocalorieToolRegistry` (`tools/AgentTools.kt:208-215`, wired at `KoogNutritionAgent.kt:174-180`).
  That alone can drive "Reading <host>…" lines with no new library surface.
- The in-progress flag to sit beside is `MealCaptureUiState.isLoading` (`:57`, derived `isBusy` at
  `:116`); `tipsRewordingInFlight` (`:74`) is the precedent for a second narrower in-flight field.

**UNVERIFIED:** whether Koog `1.0.0` (`gradle/libs.versions.toml:6`) exposes per-step or tool-call
events (e.g. an EventHandler feature on `AIAgent`). Nothing in this repo references such an API —
only `AIAgent(...)` + `.run(...)`. Needs a docs check, and per the config's design rule it should be
gated behind a spike task.

## Files
`ai/KoogNutritionAgent.kt` (progress callback or Flow on the `NutritionEstimator` seam),
`ui/capture/MealCaptureViewModel.kt:277-316`, `ui/capture/MealCaptureUiState.kt`,
`ui/capture/VoiceInputOverlay.kt:260`, `tools/AgentTools.kt` if tool-name events are added.
