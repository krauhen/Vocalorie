# AGENTS.md

**Vocalorie** — a personal Android nutrition-tracking app that turns voice, photo or typed meal input into a structured nutrition estimate via an LLM.

This file is a router, not the operating manual. It carries no rules of its own: read the shards below, starting with the always-read one. Do not plan, edit, test, or answer implementation questions from `AGENTS.md` alone.

# Where to read more

### `docs/agent/hard-rules.md`
**Read ALWAYS, before any task.** The mandatory context-loading sequence, the plan-handoff rule (plans are saved and handed over, never executed by the session that wrote them), and the universal safety boundaries that apply to every change. Tags: `safety, context-loading, boundaries, secrets, planning, always-read`.
**Skip if:** never skip.

### `docs/agent/README.md`
Orientation — what the app is, its stack and current implementation state, and the map of the documentation area with its naming conventions. Tags: `orientation, project, architecture, documentation-map, conventions`.
**Read when:** starting any non-trivial task, or when you need the project's shape before deciding where a change belongs.
**Skip if:** you already hold current orientation from earlier in this session.

### `docs/agent/guidance/task-routing.md`
Per-task-type reading lists — which guidance, spec and source files to open for each kind of change. Tags: `routing, task-types, navigation, source-map`.
**Read when:** you know the task type and want its exact file list.
**Skip if:** the task is a one-line documentation fix in a file you already have open.

### `docs/agent/guidance/coding.md`
Binding coding rules — Android/Kotlin style, app identity, and the eight enforced architecture rules covering layering, composables, threading and migrations. Tags: `coding, architecture, kotlin, compose, layering, migrations`.
**Read when:** changing any Kotlin/Compose source, or any layering or dependency wiring.
**Skip if:** the task touches no application source.

### `docs/agent/guidance/testing.md`
Canonical verification commands plus the three binding test rules — pure-JVM unit tests, behaviour over source text, and extracted pure functions ship tested. Tags: `testing, gradle, verification, jvm, room-migrations`.
**Read when:** writing or running tests, or verifying any code change.
**Skip if:** the task is documentation-only with no code to verify.

### `docs/agent/guidance/setup.md`
Local setup — required SDK levels, the debug build and install commands, known validation devices, and the rule against committing local configuration. Tags: `setup, gradle, sdk, build, secrets, devices`.
**Read when:** building, installing, or running the app on a device or emulator.
**Skip if:** you are not building or installing.

### `docs/agent/guidance/workflow.md`
How work is sequenced — keeping architecture rules and ADRs in step, recording deliberate omissions as accepted debt, and where current project status is specified. Tags: `workflow, adr, arc42, accepted-debt, process`.
**Read when:** changing an architectural rule, an ADR, or project documentation.
**Skip if:** the change alters no rule and no architectural decision.

### `docs/agent/backlog/` — `features/`, `bugs/`
The captured backlog: feature asks and pending audits in `features/`, known defects in `bugs/`. Tags: `backlog, features, bugs`.
**Read when:** the task relates to a captured ask or a known defect, or captures a new one.
**Skip if:** the task is self-contained. Currently holds four feature/audit items, four open defects, and two promoted to an OpenSpec change.

<!-- Adding docs: create docs/agent/<topic>/<section>.md with frontmatter (description, tags), then add
     a signpost entry here. Keep AGENTS.md itself to the header and the index. -->
