# Workflow guidance

## Safety policy

- Prefer minimal, focused changes.
- Do not create or modify secrets, signing keys, certificates, `.env*` files, or private data artifacts.
- Do not add generated build outputs, APKs, screenshots, databases, or copied personal data.
- Inspect the working tree before editing; this is a Git repository.
- Keep documentation claims aligned with implemented Vocalorie behavior.

## Keeping rules and architecture in step

A change to an architectural rule updates `agentic/guidance/` **and** the matching ADR in `docs/arc42.md` in the same commit. A rule with no ADR, or an ADR the guidance contradicts, is a defect — supersede the older statement explicitly rather than leaving both standing.

Something deliberately not done belongs in the `docs/arc42.md` §11.1 accepted-debt table with a one-line reason, so a later audit does not re-raise it.

## Current project status

Vocalorie is a working Android/Jetpack Compose app. Required behaviour is specified in `openspec/specs/`; architecture and decisions in `docs/arc42.md`. Read the relevant capability spec before changing behaviour it covers.
