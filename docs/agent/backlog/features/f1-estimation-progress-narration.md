---
description: Show what the estimation is doing — tool calls and steps — as a small live-updating line, like modern LLM thinking indicators.
tags: [backlog, features, estimation, ux]
---

# F1: Narrate estimation progress

**Status:** captured
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
