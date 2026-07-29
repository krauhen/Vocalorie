## Context

The app works, but a full audit of all 68 source files found ~90 performance and code-quality issues concentrated in three places.

`ui/MealCaptureScreen.kt` is one 746-line composable holding 35 `mutableStateOf` slots while owning database access, settings persistence, backup import/export, AI orchestration, routing and four overlays. Seven repository-shaped functions (`refreshHistory`, `refreshCaches`, `upsertCachesFromReviewedMeal`, `refreshActivities`, `refreshSavedKeyLabel`, `refreshToolSettings`, `refreshThemeState`) are declared *inside* the composable body, closing over both `LocalContext` and the mutable state vars. There is no ViewModel and no `Flow` anywhere in the app. Every write runs on `rememberCoroutineScope()`, so rotation cancels an in-flight OpenAI request and can commit `mealDao.insert` while cancelling the cache write that follows it. Only 6 of the 35 slots are `rememberSaveable`.

`data/MealDao.java`, `ActivityDao.java`, `CacheDao.java` and `VocalorieDatabase.java` are the only Java in the app. This matters because a Java `@Dao` cannot declare `suspend fun`, cannot return `Flow`, and cannot use `withTransaction` — so the two worst data problems are *caused* by the language choice, not merely coincident with it. Reactivity is hand-rolled across nine manual refresh sites; `findCachedMealMatch` linear-scans an in-memory copy of the entire `cached_meals` table to look up its own primary key; and both cache tables are held in Compose state purely to emulate indexed lookups.

`agentic/guidance/CODING.md` is 15 lines and mandates no architecture at all. `TESTING.md` is 9 lines and mandates essentially nothing — not even the verification command, which lives in `AGENTS.md:35`. The code violates no documented standard because there is no documented standard.

A pre-change walkthrough on the physical Samsung Galaxy S23 (`SM-S911B`) captured a 20-frame baseline across every screen and state: entries empty and populated, day navigator, stats header, 7d and 30d heatmaps, meal rows, read-only meal detail, expanded items with source indicators, the meal editor and its item cards, the activities tab and its editor, the add-meal sheet, the live voice-listening state, all of Settings, and the stats-window dropdown. It established three things that shape this design.

First, two audit findings are already visible defects in real data. Settings shows *Calories burned per 1,000 steps* as `29.999999329447746` where `30` was entered, and the meal editor renders `816.6500000000000004` kcal, `5.7000000000000004` g saturates and `3.450000000000004` g salt, with an item card showing `1.7500000000000002` g. These are the `Float`-backed `kcalPerStep` and the `Double.toString()` formatter, observed rather than inferred.

Second, the grounding cost concern is live on this device: a Brave key is saved and the workflow step limit is `64`, so every parse runs grounding at up to 64 LLM turns.

Third — and most constraining — the current visual design is deliberate and worth keeping. Calorie-state tinting, macro colour coding, per-row food-type icons, the score-gradient heatmap and the per-tab palette swap form a coherent system the user is satisfied with. This change is therefore a **visually invisible** refactor apart from a small enumerated allowlist.

Hard constraints: additive Room migrations only, never `fallbackToDestructiveMigration` (ADR-2); no DI framework, navigation library, or CI/CD (ADR-5); `minSdk 35`; single module; one developer, one user, no published release. Dependency and build changes are approved for this change specifically.

## Goals / Non-Goals

**Goals:**
- Remove the main-thread stalls and recomposition storms that are felt during daily use.
- Keep the app looking exactly as it does today, apart from the enumerated allowlist.
- Stop the AI path leaking HTTP engines, hanging without a timeout, and silently absorbing failures the user is paying for.
- Fix the data-integrity defects the audit surfaced, including the silent food-type loss on every cache hit.
- Give the app a state holder and a repository layer so business rules become testable and the database becomes the single source of change notification.
- Record the resulting rules in `agentic/guidance/`, and record what was deliberately *not* done in `docs/arc42.md` §11, so neither recurs.

**Non-Goals:**
- Any visual redesign, restyling, or "while we're in here" UI improvement. Explicitly excluded, and enforced by the `visual-baseline` capability.
- Converting items-as-JSON to relational tables. A rebuild-shaped migration against the one irreplaceable dataset, to speed up decoding a few hundred rows.
- Externalising the ~220 hardcoded UI strings. One user, one language, no translation intent.
- Adding indices on `createdAtEpochMillis`. Unmeasurable at a few hundred rows and each costs a migration.
- Retroactive migration tests for v1–v8. `exportSchema = false` means the historical schema JSONs do not exist.
- Excluding the database from auto-backup. The `data-backup` capability deliberately requires its inclusion as a passive safety net; the privacy tradeoff is recorded as accepted debt instead.
- R8 and release-signing hardening. The app is never published.
- Robolectric, Compose UI tests, coverage, or lint tooling. No CI to run them, and the seams introduced here buy the testability more cheaply.
- A full value-type sweep across all nine nutrition mapper sites, or a MVVM rollout across every screen. Scoped deliberately — see D6 and D5.

## Decisions

### D1 — Perf fixes land before reactivity, not after
Room `Flow` re-emits on every write, and `computeMealStats` is currently unremembered and takes `Instant.now()` inline, so it cannot memoize. Introducing `Flow` first would re-trigger a full-history recomputation on every save — making the app *slower* while ostensibly improving its architecture. So the ordering is: memoize the stats and hoist the clock first, then make the data layer reactive. *Alternative considered:* do the architecture first and fix perf on the clean foundation — rejected because it ships a measurable regression in the interim, and the perf fixes are independently valuable and independently shippable.

### D2 — Retarget the copy-contract tests before touching any file
Five test files assert production **source text** read through `java.io.File`, using 11 hardcoded `app/src/main/java/...` path literals. Today a wrong path silently yields empty content, so the assertion passes vacuously — meaning any file move or rename breaks them *silently*. Since this change splits a 927-line file and adds new packages, a shared helper that resolves by filename and fails loudly on 0 or >1 matches is a precondition for everything else. *Alternative considered:* rewrite them as behaviour tests — rejected as a larger job that mostly depends on string resources we are not adding; they get capped by policy instead.

### D3 — KSP is gated by a spike, with a repository-level fallback
This project uses AGP 9.2.1's **built-in Kotlin**: there is no `org.jetbrains.kotlin.android` plugin in either `build.gradle.kts` or the version catalog. Room's Java `@Database` is annotation-processed today by `annotationProcessor`, and it works only because those four files are Java while the Kotlin entities are read from compiled bytecode on the classpath. Converting any DAO to Kotlin therefore breaks the build with a missing `VocalorieDatabase_Impl` until KSP is added — and KSP's interaction with AGP built-in Kotlin cannot be verified from source.

So: spike the plugin swap on a throwaway branch first. If clean, convert the four files and get compiler-enforced `suspend` DAOs and `Flow` return types. If not, **fall back**: keep the Java files, add only `room-ktx`, and obtain every downstream benefit at the repository instead — `Flow` from `invalidationTracker` (or a repository-owned `MutableStateFlow` bumped on write), transactions via the `withTransaction` extension on the Java-defined `RoomDatabase` subclass (which works unchanged), and `suspend` via `withContext` inside the repository. The fallback costs ~30 lines and loses only the compiler-enforced DAO signature. Crucially it loses **nothing** in the later stages, because the *repository* — not the DAO — is the seam the state holder depends on. *Alternative considered:* also applying `org.jetbrains.kotlin.android` — kept as a second option, but it is another build change and the fallback is less invasive.

### D4 — Manual container, not a DI framework
Wiring is one `AppContainer` object that deliberately copies the existing `VocalorieDatabase.get(context)` double-checked-singleton idiom, so it introduces no new concept. It owns the database, the repositories, one `HttpClient`, one nutrition agent, and — importantly — the single `ThemeSettingsStore` instance, replacing the three that exist today (`MainActivity.kt:31`, `VocalorieTheme.kt:210`, `MealCaptureScreen.kt:75`). The state holder is obtained with `viewModelFactory { initializer { } }`. This extends ADR-5 rather than reversing it: a ViewModel is a lifecycle-scoped state holder, not a dependency-injection container. *Alternative considered:* Hilt or Koin — rejected by ADR-5 and unjustifiable at ~30 lines of wiring.

### D5 — One state holder, for the capture flow only
`MealCaptureScreen` gets a state holder because it is the only screen that owns orchestration. The editors, entries screen and settings screen are already stateless composables receiving state and callbacks — that is the correct shape and they keep it. `SettingsScreen`'s 39 parameters (with a 40-line pure 1:1 pass-through to a 38-parameter `SettingsContent`) collapse to `state` + `onEvent` + `onBack` as a consequence of the capture-flow refactor, not as a separate MVVM rollout. *Alternative considered:* a ViewModel per screen — rejected as ceremony that would add indirection without removing any orchestration, because there is none to remove elsewhere.

### D6 — Extract the business rules as pure functions, not as ViewModel methods
The rules currently inlined in argument lambdas — nutrition-goal percentage math (`:350-358`), activity validation (`:671-684`), and the prompt-construction plus cache-branch decision (`:504-522`) — become pure functions outside the state holder, following the pattern `ui/voice/VoiceListeningSessionPolicy.kt` already establishes. The state holder calls them; it does not contain them. These are the highest-value new tests in the change, because all three are untestable today. Clock and zone are injected for the same reason `MealTimeWindows` and `ActivityModels` already inject them. *Alternative considered:* methods on the ViewModel — rejected because testing them would then require constructing the ViewModel and its repositories.

### D7 — Nutrition value type only where mis-ordering is silent
The same 8-field nutrition tuple is spelled out six times, and every mapper is an 8-line transliteration across ~9 sites. A full sweep is tempting but dangerous: the four suites that are the only real safety net (`MealMappersTest`, `MealCacheTest`, `VocalorieBackupTest`, `MealDraftTotalsTest`) construct these DTOs **positionally**, so a wrapper type would force test edits in the same commit as production changes — exactly the situation in which a test gets "fixed" to match a bug. So the DTO field lists stay untouched, and the value type is introduced at the one place where a mistake is both silent and identically typed: `MealEditor.kt:404`'s `(String × 8) -> Unit` callback, re-listed at 8 call sites, where transposing two arguments swaps carbs with sugar and still compiles. That single change also unblocks the editor skippability fix. The remaining duplication is recorded as accepted debt. *Alternative considered:* the full three-sub-step phased sweep behind deprecated compat accessors — technically sound, but the payoff does not justify churning the safety net in this change.

### D8 — Read the persisted totals rather than dropping them
Each meal already stores eight total columns that `toSavedMeal()` never reads — it re-derives totals from decoded item JSON on every row of every reload, making the columns write-only dead data. Two options existed: drop them (a table rebuild) or start reading them. Reading them removes the per-row JSON decode from the stats path via a projection query, with no migration at all. The caveat: they equal the item-derived totals *by construction* today, because `toEntity` always calls `withTotalsSummedFromItems()` — but that cannot be proven for rows written by every past build. So the invariant is pinned forward with a test, and the equality is asserted in the spec, accepting a bounded risk that a legacy row's stored totals are stale (which would skew statistics slightly while leaving meal data itself correct). *Alternative considered:* keep full-entity decode on a background dispatcher — loses about half the win for no reduction in risk.

### D9 — Backup version becomes a range, in the same commit as the schema bump
`BACKUP_SCHEMA_VERSION` is already out of sync (8 versus database version 9) despite a comment instructing that they be kept aligned — which means the export requirement "a `schemaVersion` equal to the Room database version" is currently violated. Adding `category` to `cached_meals` takes the schema to v10, so the constant must move to 10. Against the current equality check that would **reject every backup file already exported**. So the fix is a declared accepted-version set (8–10) checked on import, landing in the same commit as the migration, and verified against a real exported file rather than a synthetic one. *Alternative considered:* upcasting older envelopes on import — unnecessary while every change in the range is additive with defaults.

### D10 — Fail loud, and classify on the cause chain
Three failure paths currently manufacture plausible data: a decrypt failure deletes the user's stored key and reports "no key configured"; malformed item JSON becomes a real-looking 0-kcal meal; an unknown activity type becomes `RUNNING` with the running icon. Each is replaced by a reported failure or a neutral value. Separately, `toUserMessage()` substring-matches only the outermost `Throwable.message` while `toDiagnosticString()` already walks the full cause chain correctly — and since Koog and Ktor wrap HTTP errors, a rejected key usually sits in a cause and is misclassified. The classifier reuses the existing cause walk.

### D11 — Targeted SSRF fixes, not a full hardening pass
`requireSafeFetchUrl` has three gaps: it resolves the host then lets Ktor resolve independently (TOCTOU); redirects are unguarded, so a permitted public URL can 302 to a link-local address; and on DNS failure `getOrDefault(emptyList())` makes `addresses.none { … }` **vacuously true, so the guard passes**. The vacuous-pass and the redirect gap are fixed. The TOCTOU re-resolution is not: on a phone there is no cloud metadata service, and the party choosing URLs is the app's own LLM, not an attacker. `docs/arc42.md:243` currently mitigates this risk as "off by default", which stopped being true when grounding became key-gated — that claim is corrected as part of this change.

### D12 — A device screenshot baseline is the acceptance gate, not an automated visual test
The user is satisfied with the current UI, so the largest risk in this change is not a crash — it is quietly restyling something while restructuring the code that draws it. Stage 6 alone rewrites a 746-line composable and splits a 927-line one. The mitigation is the 20-frame device baseline: each stage is re-walked on the S23 with the same navigation path and compared frame to frame, and any difference outside the allowlist is a defect in this change.

Deliberately **not** an automated screenshot-diff harness. That would need Compose UI test infrastructure (not in the approved dependency set), a CI to run it (ADR-5 rejected CI), and device-specific golden images that churn on every OS update — and it would still not catch what a two-minute manual walkthrough catches on a single-user app. The baseline lives outside the repository: `AGENTS.md` forbids committing screenshots or copied personal data, and the frames contain real meal history. *Alternative considered:* asserting UI copy via the existing source-text contract tests — rejected as already the weakest tests in the suite, and blind to layout, colour and spacing, which is precisely what is at risk.

The allowlist is deliberately tiny and stated in the spec, so "the UI changed" is never a judgement call: numeric precision no longer leaking, an attachment progress indication, a grounding-failure warning, and the two pre-existing squeezed labels. The squeezed labels are the only *intentional* visual improvement in the change, they were found by this walkthrough rather than the code audit, and they are scoped as separable polish.

### D13 — Guidance files get short rules; arc42 gets the accepted debt
Rules that fit on one screen change behaviour; a 200-line style guide does not. `CODING.md` gains eight rules and `TESTING.md` four. `docs/arc42.md` gains superseding ADRs (ADR-4's mock-by-default model no longer exists and is now *forbidden* by a test), the missing `#performant` quality goal that the perf work actually measures against, and a §11 table of everything in Non-Goals with a one-line reason each. That last table is the highest-value doc output: it is what stops the next audit re-raising all of it. *Alternative considered:* recording standards as OpenSpec specs — rejected because specs describe behaviour, and these are build-time conventions.

## Risks / Trade-offs

- **KSP may not work with AGP 9.2.1's built-in Kotlin** → Spike it in isolation before anything depends on it; the D3 repository-level fallback preserves every downstream benefit, so a negative spike result costs one hour and changes no later stage.
- **`Flow` makes the cache read asynchronous, and the capture flow reads `cachedMeals` state synchronously at `:514` to decide whether to skip the LLM call** → A cold-start tap could miss a warm cache and fire a **billable** estimate. Convert that read to an explicit `suspend` cache lookup, and extract the approval flow out of the shared edit dialog (it is currently bolted on with four optional params and no-op callbacks at `:564-601`) before rewiring.
- **`viewModelScope` changes cancellation semantics** → A save that previously died with the composable now survives it. That is the point, but the overlay dismiss paths currently rely on that cancellation; re-check each one.
- **Legacy rows may hold stale persisted totals (D8)** → Statistics could be slightly off for such rows while meal data stays correct. Pin the invariant with a test going forward; the spec asserts equality; revert to item-derived totals if a discrepancy is observed.
- **Bumping the backup version could orphan existing exports (D9)** → Land the accepted-version set in the same commit as the migration and verify against a real exported file.
- **Deleting the theme `refreshSignal` looks free but is the path that fires on every preference write** → Confirm live theme edits still propagate through the single container-owned store before removing it.
- **Splitting `MealEntriesScreen.kt` (927 lines) could break the source-text tests silently** → Blocked behind D2 by design.
- **Restructuring the code that draws the UI could quietly restyle it** — the single largest risk, given the user is satisfied with the current design → The D12 device baseline is re-walked per stage; any difference outside the four-item allowlist is a defect. Stages 2, 6 and 7 are the ones to watch, since they touch drawing code directly.
- **Fixing the numeric formatter changes what is displayed everywhere at once** → It is on the allowlist deliberately, but the fix must be pinned by tests *before* adoption (`0.0001` → `"0.0001"`, `816.6500000000000004` → `"816.65"`), because three implementations with divergent semantics are being collapsed into one.
- **`onRefresh` doubles as the trigger that advances `now`** so passed entries stop being crossed out → Keep it as a `refreshNow()` that bumps the clock only; the database no longer needs refreshing.
- **The change is large for this repo** (nine capabilities against a previous maximum of two) → Task groups are ordered so each is independently shippable with a green build, and groups 1–4 deliver every user-visible win without touching the architecture, so the change can be stopped after any group.

## Migration Plan

One additive Room migration, `MIGRATION_9_10`, adding `category TEXT NOT NULL DEFAULT 'OTHER'` to `cached_meals`. No table rebuild, no data loss, ADR-2 preserved. `exportSchema` is turned on at the same time, so v10 is the first version with a committed schema JSON and the first that `MigrationTestHelper` can validate; v1–v8 remain verified only empirically on-device, recorded as accepted debt.

`BACKUP_SCHEMA_VERSION` moves 8 → 10 with an accepted-import range of 8–10, landing in the same commit (D9).

Rollback: each task group is a separate commit that leaves the build green, so any group can be reverted independently. The migration commit is deliberately isolated from the refactor commits so a schema revert does not entangle a 746-line restructuring.

## Open Questions

- Does KSP work with AGP 9.2.1 built-in Kotlin? Resolved by the D3 spike, which gates only that task group.
- What should a meal with unreadable item JSON render as — an explicit "could not read items" row, or exclusion from totals with a warning? Small UI decision, resolve when implementing group 4.
- Should the grounding agent's iteration cap default drop from 64 to 8, or become a separate lower-tier model choice? Cap reduction is in scope; a distinct grounding model is deferred.
