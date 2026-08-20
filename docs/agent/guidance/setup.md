---
description: Local setup for Vocalorie — required Android SDK levels, the debug build and install commands, the mandatory database pull before any install, known validation devices, and the rule against committing local configuration.
tags: [setup, gradle, sdk, build, secrets, devices, data-safety]
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

## Pull the database before every install

**Before any `installDebug`, `connectedDebugAndroidTest`, or other install onto a device holding real data, copy the live database off the device first:**

```bash
mkdir -p ~/Documents/vocalorie-db-backups && adb exec-out run-as app.vocalorie.personal cat /data/data/app.vocalorie.personal/databases/vocalorie.db > ~/Documents/vocalorie-db-backups/device-$(date +%Y%m%d-%H%M).vocalorie.db
```

This is not optional bookkeeping. On 2026-08-20 an install recreated the package record on `SM-S911B`, Android restored the app's data directory from its cloud snapshot, and the database silently reverted to a state from two days earlier — 31 meals and 5 activities gone, with no error anywhere. `android:allowBackup="true"` is deliberate and stays (it is the backup the user actually relies on), so the stale-restore window stays open too; the pulled copy is what makes it a thirty-second recovery instead of a loss.

Detecting it after the fact: compare `MAX(id)` and `sqlite_sequence.seq` for `meals` against an earlier copy. A `seq` that went *down* proves a snapshot rollback rather than deletion, because no delete lowers it. Restore by force-stopping the app, copying the newer file back over `databases/vocalorie.db`, and deleting the stale `-wal`/`-shm` beside it.

Validation targets seen in prior sessions: an Android emulator (`emulator-5554`), and previously a physical Samsung Galaxy S23 (`SM-S911B`, adb serial `RFCW20LALNM`).

Do not commit `local.properties` or other machine-local configuration. It may hold `openai.api.key` and a Brave key, which reach `BuildConfig` as a convenience prefill; both are secrets.
