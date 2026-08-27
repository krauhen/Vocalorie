---
description: Canonical Vocalorie verification commands plus the three binding test rules — pure-JVM unit tests, behaviour over source text, and extracted pure functions ship tested.
tags: [testing, gradle, verification, jvm, room-migrations]
---

# Testing guidance

## Verification commands

This file is the SSOT (Single Source of Truth) for Vocalorie's verification commands: if any other document disagrees with this file, this file wins. `AGENTS.md` is a router and does not restate them.

After any change:

```bash
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon
```

Additionally, for a Room schema, migration, or persisted-data change:

```bash
./gradlew :app:compileDebugAndroidTestKotlin --no-daemon
./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Buildability (`:app:assembleDebug`) is a separate check from product validation evidence; do not report one as the other.

## Rules

1. **`src/test` is pure JVM.** No network call, no real API key, no machine-specific path, no billable request, no `local.properties` read. A test that needs any of those is a live harness: it stays `@Ignore`d with its reason stated in the annotation, and it is run manually (see `KoogNutritionAgentLiveHarnessTest`).
2. **Test behaviour, not source text.** The copy-contract pattern — asserting production source read through `java.io.File` — is **deprecated but tolerated** for existing tests. Do not add more. Any test that greps source MUST resolve the file by filename via `testsupport/ProductionSource.kt` and fail loudly on 0 or more than 1 match, never by a hardcoded path that silently yields empty content and passes vacuously.
3. **A pure function extracted from a composable ships with its tests in the same commit.** Extraction is only worth doing because it makes the rule testable; landing it untested spends the cost and skips the benefit. Inject a Test Seam (Feathers) — `HttpTextFetcher`, `TableChangeSource`, `clock: () -> Instant` — so the test stays on the JVM.
