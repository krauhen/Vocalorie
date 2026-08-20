---
description: Always-read rules for Vocalorie: the mandatory context-loading sequence before any task, the plan-handoff rule, and the universal safety boundaries that apply to every change.
tags: [safety, context-loading, boundaries, secrets, planning, always-read]
---

# Hard rules

Read this file before any task. Everything in it applies to 100% of work in this repository.

## Mandatory context loading

Before starting any task, follow this sequence:

1. Identify the task type.
2. Read `docs/agent/README.md`.
3. Read the matching files under `docs/agent/guidance/`.
4. If the task relates to a captured feature ask, audit, or known defect, read the relevant files under `docs/agent/backlog/`.
5. Only then start planning or execution.

If the correct files are unclear, read these defaults first:

- `docs/agent/guidance/workflow.md`
- `docs/agent/guidance/coding.md`
- `docs/agent/guidance/testing.md`

`docs/agent/guidance/coding.md` and `docs/agent/guidance/testing.md` carry the binding architecture and testing rules — read them before any code or test change. For behaviour detail beyond those rules, prefer `openspec/specs/` and the actual source under `app/src/main/java/com/example/vocalorie/` with its tests under `app/src/test/java/com/example/vocalorie/`.

Detailed per-task-type reading lists live in `docs/agent/guidance/task-routing.md`.

## Plan handoff

Plans are saved and handed over, never executed by the session that wrote them.

- Write the plan to a file under `~/.claude/plans/`. Do not create a plan directory inside this repository.
- End the turn with one line naming what the plan covers, then the absolute path alone in a fenced code block.
- Do not offer to implement, and do not implement. The next session picks the plan up cold.
- Make the plan self-contained for that cold start: name the concrete files under `app/src/main/java/com/example/vocalorie/`, the guidance shards the implementer must read (`docs/agent/guidance/coding.md`, `docs/agent/guidance/testing.md`), the relevant capability under `openspec/specs/`, the decisions already settled with their rejected alternatives, and the verification commands from `docs/agent/guidance/testing.md`.

## Universal safety rules

These rules always apply.

- Do not touch unrelated modified files.
- Do not commit secrets, including `local.properties`, `.env*`, OpenAI/Brave API keys, `key.pem`, or `cert.pem`.
- Do not drop untracked or uncommitted changes.
- Do not delete, move, or rename files without explicit approval.
- Do not change dependencies without explicit approval.
- Do not change build, CI/CD, or infrastructure files without explicit approval.
- Do not add data artifacts, generated build outputs, APKs, screenshots, databases, or copied personal data.
- Do not include personal details, names, contact information, addresses, or copied CV/project details in generated docs.
- Preserve existing project conventions (including app identity: namespace, applicationId, app label) unless the task explicitly asks to change them.
- Prefer minimal, focused changes.
- Ask before non-trivial or ambiguous changes.
- Do not create or modify signing keys, certificates, or private data artifacts.
- Do not begin implementing a plan in the session that produced it.
- Inspect the working tree before editing; this is a Git repository.
- Keep documentation claims aligned with implemented Vocalorie behavior.
