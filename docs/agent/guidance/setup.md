# Setup guidance

Use a local Android SDK compatible with the configured compile SDK (`compileSdk 36`, `minSdk 35`). The app builds with:

```bash
./gradlew :app:assembleDebug --no-daemon
```

Instrumented tests (currently Room migration coverage) need a connected emulator or device:

```bash
./gradlew :app:connectedDebugAndroidTest --no-daemon
```

Do not commit `local.properties` or other machine-local configuration. It may hold `openai.api.key` and a Brave key, which reach `BuildConfig` as a convenience prefill; both are secrets.
