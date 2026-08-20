---
description: Local setup for Vocalorie — required Android SDK levels, the debug build and install commands, known validation devices, and the rule against committing local configuration.
tags: [setup, gradle, sdk, build, secrets, devices]
---

# Setup guidance

Use a local Android SDK compatible with the configured compile SDK (`compileSdk 36`, `minSdk 35`). The app builds with:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Instrumented tests (currently Room migration coverage) need a connected emulator or device:

```bash
./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Install and try the current debug build on a running emulator/device:

```bash
./gradlew :app:installDebug --no-daemon
```

Validation targets seen in prior sessions: an Android emulator (`emulator-5554`), and previously a physical Samsung Galaxy S23 (`SM-S911B`, adb serial `RFCW20LALNM`).

Do not commit `local.properties` or other machine-local configuration. It may hold `openai.api.key` and a Brave key, which reach `BuildConfig` as a convenience prefill; both are secrets.
