# Vocalorie

Vocalorie currently contains a small Koog nutrition spike in a minimal Android/Jetpack Compose app.

## What this spike proves

- Runtime BYOK OpenAI input works from the Compose UI.
- Koog can request a structured nutrition estimate and map it into kotlinx.serialization DTOs.
- The app displays parsed items, totals, assumptions, warnings, confidence, and visible errors.
- Reviewed meals can be saved, edited, and deleted from local Room history.
- App-owned Brave Search and WebFetch tools default to deterministic mock fallbacks, with optional real network tools enabled from Settings.
- Meal input supports typing or native Android speech recognition when a device speech service is available.

## Setup

You can paste an OpenAI API key into the runtime field in the app. For local spike convenience, you may also prefill it by adding this optional entry to `local.properties`:

```properties
openai.api.key=sk-...
```

`local.properties` must stay local and must not be committed. Treat API keys as secrets.

Optional Brave Search support is configured at runtime in Settings. The app remains usable without Brave credentials; the Koog tool registry falls back to deterministic mock snippets unless real Brave Search is enabled and a Brave API key is saved locally.

## Caveats

- This is a spike, not production architecture.
- Tool calls are mocked by default so behavior is deterministic enough to evaluate the Koog flow; real Brave/WebFetch are lightweight local-device HTTP calls and fall back to mock content on failure.
- Koog dependency requirements may constrain Android compatibility; verify `minSdk` before turning this into production code.
- Speech input uses Android `SpeechRecognizer`; availability and behavior depend on the installed device speech service.

## Build

```bash
./gradlew :app:assembleDebug --no-daemon
```

## Run

Open the project in Android Studio and run the `app` configuration, or install a debug build on a connected device/emulator.
