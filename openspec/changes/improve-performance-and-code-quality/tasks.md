## 1. Unblock: stop billing, stop silent test breakage

- [x] 1.1 Add `@Ignore("Live network + billable + machine-specific path; run manually")` to `test/.../ai/KoogNutritionAgentLiveHarnessTest.kt` — it currently runs under the mandated `testDebugUnitTest`, spends real OpenAI credit, and reads a hardcoded `/Users/use/Downloads/pickles.jpg`
- [x] 1.2 Add a shared test helper that resolves a production source file by filename under `app/src/main/java` and fails loudly on 0 or >1 matches
- [x] 1.3 Replace the 11 hardcoded `app/src/main/java/...` path literals across `UiCopyContractTest`, `NutritionPromptContractTest`, `AgentToolsRealOnlyContractTest`, `GalleryImageAttachmentContractTest`, `MealCalorieStyleTest` with the helper
- [x] 1.4 Delete the assertion on the multi-line indentation-sensitive Compose block in `UiCopyContractTest` (breaks on reformatting, guards nothing)
- [x] 1.5 Verify: renaming a source file in a scratch check makes the contract tests fail loudly rather than pass vacuously; `testDebugUnitTest` makes zero network calls and no longer reads `local.properties`
- [x] 1.6 Copy the 20-frame S23 baseline out of the session scratchpad into a durable location **outside the repository** (`AGENTS.md` forbids committing screenshots or copied personal data, and the frames contain real meal history); record the exact navigation path used so later walkthroughs are frame-comparable
- [x] 1.7 Write down the visual allowlist for this change so "the UI changed" is never a judgement call: numeric precision no longer leaking; an attachment progress indication; a grounding-failure warning; the two squeezed labels in 7.11–7.12. Everything else must match the baseline

## 2. Felt performance (no architectural dependency)

- [x] 2.1 Move `toGalleryImageAttachment` off the main thread at `ui/voice/VoiceInputOverlay.kt:328` and add a preparing-attachments progress state; report a per-image failure without dropping the other selections
- [x] 2.2 Hoist `Instant.now()` out of `computeMealStats`'s call at `ui/entries/stats/MealStatsOverview.kt:69` into a parameter and wrap the call in `remember(meals, range, now, zone)`
- [x] 2.3 Precompute a remembered list of (date, score, colour) for the heatmap so `nutritionScore()` is not called per cell inside composition; hoist the per-cell click lambda (`MealStatsOverview.kt:210-252`)
- [x] 2.4 Introduce an `EditableNutrition` value type and replace `MealEditor.kt:404`'s `(String × 8) -> Unit` callback at all 8 call sites, with tests pinning that the fields cannot transpose
- [x] 2.5 Change `EditableFoodItemCard`'s callbacks to `(Int, EditableFoodItem) -> Unit` so item rows skip instead of capturing the changing `draft` (`MealEditor.kt:61-117, 131-139`)
- [x] 2.6 Hoist the four compile-time-constant `portionScaleFactor` calls at `MealEditor.kt:195-199` to `val`s
- [x] 2.7 Wrap `searchSavedMeals` at `ui/MealCaptureScreen.kt:474` in `remember(savedMeals, searchQuery)`
- [x] 2.8 `remember` the four `themeSettingsStore` reads currently passed as unremembered `SettingsScreen(...)` arguments (`MealCaptureScreen.kt:305, 313, 318, 331`)
- [x] 2.9 Move `DateTimeFormatter.ofPattern` at `MealStatsOverview.kt:205` to a file-level constant, following `ui/components/CommonUi.kt:50-67`
- [x] 2.10 Delete the `refreshSignal` listener path at `ui/VocalorieTheme.kt:211-220` after confirming live theme edits still propagate
- [x] 2.11 Verify: `compileDebugKotlin` + `testDebugUnitTest` green with `MealStatsCalculatorTest` (24) and `MealTimeWindowsTest` (24) unmodified; on-device — attaching four photos does not freeze, ten day-nav and ten heatmap taps do not stutter, typing in a 10-item meal is smooth
- [x] 2.12 Re-walk the baseline path on the S23 and diff against the captured frames. Watch specifically: calorie-state row tinting, macro colours, food-type icon placement, heatmap gradient and neutral no-data colour, and the Meals/Activities palette swap. Only the attachment progress indication may differ

## 3. AI path: client lifecycle, timeouts, fail-loud, fetch safety

- [x] 3.1 Extract a `fun interface HttpTextFetcher` and a `KtorHttpTextFetcher` implementation so the tool layer no longer constructs `HttpClient(Android)` per invocation (`tools/AgentTools.kt:123-129`)
- [x] 3.2 Extract `interface NutritionEstimator`; make `KoogNutritionAgent` a class taking the fetcher, and cache the `MultiLLMPromptExecutor` + `OpenAILLMClient` per API key instead of rebuilding and leaking them per call (`ai/KoogNutritionAgent.kt:119-124`)
- [x] 3.3 Add a request timeout and bounded retry on rate-limit/server errors to the estimate call (`KoogNutritionAgent.kt:166`)
- [x] 3.4 Lower the grounding agent's default `maxIterations` from 64 (`settings/ToolSettings.kt`, used at `KoogNutritionAgent.kt:191`). Confirmed live on the S23: a Brave key is saved and the workflow step limit reads 64, so every parse currently runs grounding at up to 64 LLM turns
- [x] 3.5 Check `response.status` in `realBraveSearch` (`AgentTools.kt:46-49`) and report a non-2xx as a failure; distinguish a genuinely empty result and a malformed body from a transport failure
- [x] 3.6 Surface grounding failure instead of discarding the throwable at `KoogNutritionAgent.kt:127-135`; carry a warning on the result and retain the cause in diagnostics
- [x] 3.7 Bound the fetched body read and reject non-text content types before reading (`AgentTools.kt:88`)
- [x] 3.8 Fail closed on DNS resolution failure in `requireSafeFetchUrl` (the `getOrDefault(emptyList())` at `AgentTools.kt:139` currently makes the guard vacuously pass) and validate each redirect hop
- [x] 3.9 Make `fetchedUrls` collection concurrency-safe (`KoogNutritionAgent.kt:126`), following the `AtomicInteger` pattern already used at `AgentTools.kt:99`
- [x] 3.10 Make `toUserMessage()` classify on the cause chain, reusing the walk already in `toDiagnosticString()` (`KoogNutritionAgent.kt:316-342`)
- [x] 3.11 Move `require(...)` at `KoogNutritionAgent.kt:96-97` inside the conversion so callers always receive `NutritionAgentException`; delete the duplicated `OpenAiModelChoice → LLModel` `when` at `:109-114` in favour of the existing `OpenAiModelChoice.model`
- [x] 3.12 Add behaviour tests with a lambda fetcher: Brave non-2xx is a failure not an empty result, an oversized body is not fully read, a redirect to a link-local address is rejected, an unresolvable host is rejected, a cause-deep rejected key maps to the specific message
- [x] 3.13 Verify: `testDebugUnitTest` green; three consecutive estimates leave engine/thread count flat; `NutritionPromptContractTest` updated deliberately if prompt or DTO wording moved

## 4. Data integrity

- [x] 4.1 Add `category` to `CachedMealEntity` and additive `MIGRATION_9_10` (`ALTER TABLE cached_meals ADD COLUMN category TEXT NOT NULL DEFAULT 'OTHER'`), registered; schema v9 → v10
- [x] 4.2 Persist and restore `category` in `toCachedMealEntity()` and `CachedMealEntity.toSavedMeal()` (`data/MealMappers.kt:119-152`) so a cache hit stops silently downgrading every meal to `OTHER`
- [x] 4.3 Set `BACKUP_SCHEMA_VERSION = 10` and add a declared `SUPPORTED_BACKUP_SCHEMA_VERSIONS = 8..10` accepted by `parseBackupEnvelope`, in the same commit as 4.1
- [x] 4.4 Wrap `exportBackupJson`'s four table reads in a transaction (`data/VocalorieBackup.kt:70-79`)
- [x] 4.5 Stop returning a silent `emptyList()` for malformed `itemsJson` (`MealMappers.kt:68`) — surface it rather than producing a real-looking 0-kcal meal
- [x] 4.6 Replace the `RUNNING` fallback for an unknown activity type with a neutral value (`data/ActivityMappers.kt:46`)
- [x] 4.7 Extract a shared `KeystoreSecretCodec(alias)` from the byte-identical crypto in `settings/OpenAiApiKeyStore.kt:62-79` and `settings/ToolSettingsStore.kt:113-130`, including the duplicated encrypt/decrypt blocks and constants
- [x] 4.8 Stop clearing the stored key on decrypt failure in both stores; retain the ciphertext and report "saved key could not be read" as distinct from "no key configured"
- [x] 4.9 Merge the duplicated masking helpers (`settings/OpenAiApiKeyLabels.kt`, `settings/ToolSettings.kt:53-59`) into one implementation, keeping both existing label tests passing
- [x] 4.10 Persist `kcalPerStep` as `Double` rather than `Float` behind its `Double` API (`settings/ThemeSettingsStore.kt:105, 109, 172`). Observed on the S23: Settings reads **29.999999329447746** where `30` was entered — this is the defect, and the existing stored value must migrate or re-round so it reads `30` afterwards
- [x] 4.11 Verify: a cache hit shows the correct food-type icon; a real previously-exported backup file still imports; an unreadable key shows the re-enter message instead of vanishing; the step-burn setting reads back exactly as entered (`30` → `30`, not `29.999999329447746`); `MealCacheTest` (13), `MealMappersTest` (14), `VocalorieBackupTest` (6), `ActivityMappersTest` green

## 5. Build foundation and reactive data layer

- [x] 5.1 **Gate:** spike the KSP plugin swap on a throwaway branch — this project uses AGP 9.2.1 built-in Kotlin with no `org.jetbrains.kotlin.android` plugin, so KSP compatibility is unverified. If it fails, take the design's repository-level fallback and skip 5.3–5.4
  - **Result: NEGATIVE.** Spiked in a throwaway worktree with a minimal Kotlin `@Database`/`@Dao`/`@Entity` (including `suspend` and `Flow` signatures) and `ksp("androidx.room:room-compiler")` on KSP `2.2.21-2.0.4`. Gradle configuration fails outright: *"KSP is not compatible with Android Gradle Plugin's built-in Kotlin. Please disable by adding `android.builtInKotlin=false` to gradle.properties and apply `kotlin("android")` plugin."* Taking KSP would therefore mean removing AGP 9.2.1's built-in Kotlin and adopting the standalone Kotlin Gradle Plugin across the whole module — a far larger, riskier build change than this change's scope, and one that could not be validated on-device at the time. **Taking design D3's repository-level fallback:** the DAOs stay Java and blocking; the repository owns dispatching, `Flow` and transactions. D3 records that this loses nothing downstream, because the repository — not the DAO — is the seam the state holder depends on.
- [x] 5.2 Add to `gradle/libs.versions.toml` and `app/build.gradle.kts`: ~~KSP plugin replacing `annotationProcessor(room-compiler)`~~ (cancelled by 5.1), `room-ktx`, ~~`lifecycle-viewmodel-compose`~~ (deferred to group 6, where it is first used), `testImplementation(coroutines-test)`, ~~`ktor-client-mock`~~ (unnecessary: the `HttpTextFetcher` seam from 3.1 makes the tool layer testable with a plain lambda), the currently non-existent `androidTestImplementation` block (`room-testing`, test runner, `ext:junit`), `testInstrumentationRunner`, `exportSchema = true`, `room.schemaLocation` passed via `javaCompileOptions.annotationProcessorOptions` **instead of** `ksp { arg(...) }` — this works with the Java annotation processor, so the schema JSON and the migration test survive the negative gate; commit `app/schemas/`
- [~] 5.3 ~~Convert `data/MealDao.java`, `ActivityDao.java`, `CacheDao.java`, `VocalorieDatabase.java` to Kotlin~~ — **CANCELLED by the 5.1 gate.** A Kotlin `@Dao`/`@Database` cannot be annotation-processed without KSP, and KSP is unavailable.
- [~] 5.4 ~~Make DAO reads `suspend` and expose `observeAll(): Flow<...>`~~ — **CANCELLED by the 5.1 gate.** A Java `@Dao` cannot declare `suspend` or return `Flow`; both properties are provided at the repository instead (5.9).
- [x] 5.5 Add the keyed cache queries that replace in-memory scans: `findMeal(normalizedKey)` (a primary-key lookup currently done as a linear scan over the whole table) and `findItems(normalizedNames)`
- [x] 5.6 Add a `MealSummary` projection query returning the eight stored total columns without `itemsJson`, plus a test pinning that persisted totals equal item-derived totals
- [x] 5.7 Add transactional writes: insert + `upsertMeal` + `upsertItems` as one unit (currently two separate transactions at `MealCaptureScreen.kt:126-128`)
- [x] 5.8 Delete the four dead DAO methods, including the platform-type `getById` NPE trap
- [x] 5.9 Add `data/repository/`: `MealRepository`, `ActivityRepository`, `MealCacheRepository`, `ThemeSettingsRepository`, `SecretRepository` — each owning its own dispatching and mapping entities off the main thread
- [x] 5.10 Verify: `app/schemas/.../10.json` generated; `compileDebugAndroidTestKotlin` succeeds for the first time; `connectedDebugAndroidTest` covers 9 → 10 including the `category` default; `MealMappersTest`, `MealCacheTest`, `VocalorieBackupTest` green **unmodified**

## 6. State holder

- [ ] 6.1 Add `AppContainer` following the existing `VocalorieDatabase.get(context)` idiom, owning the database, repositories, one `HttpTextFetcher`, one nutrition agent, and the single `ThemeSettingsStore` (replacing three instances at `MainActivity.kt:31`, `VocalorieTheme.kt:210`, `MealCaptureScreen.kt:75`)
- [ ] 6.2 Extract `NutritionGoals.parse(...)` with a `percentRange` companion from the inline percentage math at `MealCaptureScreen.kt:350-358`, with tests
- [ ] 6.3 Extract `EditableActivityDraft.validate(...)` from the inline validation at `MealCaptureScreen.kt:671-684`, with tests
- [ ] 6.4 Extract a sealed `planEstimate(query, images, cachedMatch)` from the prompt-construction and cache-branch logic at `MealCaptureScreen.kt:504-522`, with tests
- [ ] 6.5 Extract `CachedMealApprovalDialog` out of the shared edit dialog (currently four optional params plus no-op callbacks at `MealCaptureScreen.kt:564-601`) and make the cache lookup an explicit `suspend` call, so a cold-start tap cannot miss a warm cache and fire a billable estimate
- [ ] 6.6 Add `MealCaptureViewModel` + `MealCaptureUiState`, wired with `viewModelFactory { initializer { } }`, injecting `clock: () -> Instant` and `zone`
- [ ] 6.7 Move the seven in-composable functions (`refreshHistory`, `refreshCaches`, `upsertCachesFromReviewedMeal`, `refreshActivities`, `refreshSavedKeyLabel`, `refreshToolSettings`, `refreshThemeState`) into the state holder one at a time, keeping the build green after each
- [ ] 6.8 Collapse the 35 `mutableStateOf` slots into the immutable state object; replace `rememberCoroutineScope()` with `viewModelScope` and re-check every overlay dismiss path that relied on composition-scoped cancellation
- [ ] 6.9 Delete the nine manual refresh call sites now that persisted data is observed (`MealCaptureScreen.kt:130, 224-226, 236-238, 463-467, 543, 625, 646, 699, 727`)
- [ ] 6.10 Keep `onRefresh` as a `refreshNow()` that advances `now` only, preserving the behaviour that passed entries stop being crossed out
- [ ] 6.11 Collapse `SettingsScreen`'s 39 parameters and the 40-line 1:1 pass-through to `SettingsContent` into `state` + `onEvent` + `onBack`
- [ ] 6.12 Split `ui/entries/MealEntriesScreen.kt` (927 lines: 15 composables, two `Canvas` charts, three dialogs, formatting extensions, a `Modifier` extension) along its seams
- [ ] 6.13 Add `MealCaptureViewModel` tests with fake repositories under `runTest`
- [ ] 6.14 Verify: rotating mid-estimate preserves the in-flight request, draft, attachments and query; killing the process during a save leaves either both meal and cache rows or neither; `grep -rn refreshHistory app/src` returns 0; `MealCaptureScreen.kt` under ~150 lines with no `withContext` or DAO reference
- [ ] 6.15 Re-walk the baseline path on the S23 and diff against the captured frames. This is the highest-risk stage for silent restyling — a 746-line composable is rewritten and a 927-line one is split. Check every screen, not just the entries list: both overlays, both editors, the add-meal sheet, and all of Settings. Only a grounding-failure warning may differ

## 7. Deduplication and remaining fixes

- [x] 7.1 Move `EntryTimestampField` to `ui/components/CommonUi.kt` (byte-identical between `MealEditor.kt:239-269` and `ActivityEditor.kt:232-262`) and fix the shared bug where `LaunchedEffect(epochMillis)` clobbers mid-edit text that transiently fails to parse; pin with a test
- [x] 7.2 Replace the three `toEditText()`/parser implementations with one using `BigDecimal.stripTrailingZeros().toPlainString()` semantics. Pin with tests **before** adopting it, using the values observed on the S23: `816.6500000000000004` → `"816.65"`, `5.7000000000000004` → `"5.7"`, `3.450000000000004` → `"3.45"`, `1.7500000000000002` → `"1.75"`, and `0.0001` → `"0.0001"` not `"1.0E-4"`
- [x] 7.3 Move `Double?.formatEnergy()` to `CommonUi.kt` (duplicated in `MealEditor.kt:548` and `MealEntriesScreen.kt:916`)
- [x] 7.4 Dedupe the "Since 00:00"/"Last 24h"/"Custom" copy triplicated at `MealEntriesScreen.kt:369/373/377` and `585/586/587`
- [x] 7.5 Move the Compose dependencies out of `model/ActivityModels.kt` (`Color`, `ImageVector`, `dp`), cache the vectors instead of rebuilding one per `activityTypeIcon()` call, and drive colour by `tint` rather than the hardcoded dark-navy fill
- [x] 7.6 Add an `ON_STOP` lifecycle observer that releases the microphone when the app is backgrounded, and re-check the guard after the 300 ms delay at `ui/voice/VoiceInputOverlay.kt:444-449` so an explicit stop is not overridden
- [x] 7.7 Remove the identified dead code: `NutritionAgentRequest`, `toShortMealTitle(String)`, `firstConcreteSourceUrlOrBlank`, `hasSavedKey`, the `canGoNewer`/`isFuture`/`toolName` dead parameters, the dead `if (dayOffset == 0)` branch at `MealTimeWindows.kt:67-85`, the unused import at `MealEntriesScreen.kt:93`
- [x] 7.8 Move the five hardcoded heatmap hex colours from `MealStatsOverview.kt:42-46` next to `MacroColors` in `ui/VocalorieTheme.kt`
- [x] 7.9 Adopt the existing `readColor`/`saveColor` helpers in the six `ThemeSettingsStore` setters that still inline `prefs.edit()`, and split the nutrition settings out of the theme store
- [x] 7.10 Verify: typing a partial timestamp is no longer clobbered; backgrounding mid-listen releases the microphone; tapping stop does not re-open it 300 ms later; `TimestampFormattingTest` and both label tests green
- [x] 7.11 *(optional polish, separable)* Fix the portion-scaling quick-select chips so the "All" chip fits its label on one line — it currently renders one character per line because four chips are squeezed into the row (`ui/components/MealEditor.kt`, `PortionScalingControls`)
- [x] 7.12 *(optional polish, separable)* Stop the paired item-card nutrition labels wrapping mid-word — "Carbohydrate g" currently breaks as "Carbohydrat"/"e g" (`ui/components/MealEditor.kt`, `NutritionFields`)
- [ ] 7.13 Re-walk the baseline path on the S23 and diff against the captured frames. Confirm the numeric fields now read cleanly, and that 7.11–7.12 are the only other visual differences from the baseline

## 8. Record the standards

- [ ] 8.1 Add eight rules to `agentic/guidance/CODING.md`: one-directional UI → state holder → repository → DAO layering with no `Context` below and no DAO above; composables render state and emit events with no business logic in argument lambdas; nothing expensive in a composable body without `remember`, clocks hoisted to parameters; no bitmap, crypto, preference or JSON work on the main thread; long-lived clients created once, injected, closed; no failure path that converts an error into plausible data, and classification on the cause chain; value types over three-or-more same-typed positional parameters; additive migrations only with the backup version bumped in the same commit. Keep the app-identity and dependency-approval rules verbatim; drop the "this starter contains no GPS" line
- [ ] 8.2 Add four rules to `agentic/guidance/TESTING.md`: the verification command lives here, not only in `AGENTS.md:35`; `src/test` is pure JVM with no network, real keys, machine paths or billable calls, and any live harness is `@Ignore`d with its reason; test behaviour not source text, with the copy-contract pattern marked deprecated-but-tolerated and any source-grep test required to resolve by filename and fail loudly; a pure function extracted from a composable ships with its tests in the same commit
- [ ] 8.3 Update `docs/arc42.md`: supersede ADR-4 (agent tools are real-only, mocks are now forbidden by a test, grounding is key-gated); add ADRs for the state-holder/repository layering and the injected I/O seams; add a `#performant` quality goal; correct "schema version 4" at `:54, :137, :177` to 10 and "single `meals` table" to four entities; remove the personal email at `:40`
- [ ] 8.4 Add the §11 accepted-debt table with a one-line reason each: items stay JSON; ~220 strings stay hardcoded; no indices at current data scale; migrations v1–v8 verified only empirically on-device; the database stays in auto-backup per the `data-backup` requirement, with its privacy tradeoff stated; release uses debug signing with R8 off and plaintext `BuildConfig` keys; TOCTOU re-resolution in `requireSafeFetchUrl` unaddressed; the remaining nutrition-tuple duplication; copy-contract tests
- [ ] 8.5 Correct `AGENTS.md`: `:15` "Not a git repository" (false); `:22` schema version 4 → 10; `:23` "agent tools default to deterministic mocks; real network is opt-in" (inverted — the most misleading claim in the repo); `:156` routing to the non-existent `ai/KoogNutritionSpike.kt`; the "fresh starter" claims. Add `openspec/` and `docs/arc42.md` to the routing table, plus a row for state-holder changes
- [ ] 8.6 Remove the stale "fresh starter" claims from `agentic/README.md:5, :21`, `agentic/guidance/WORKFLOW.md:8, :13`, `agentic/guidance/SETUP.md:3`, and add the WORKFLOW corollary that an architectural rule change updates the guidance and the matching ADR in the same commit
- [ ] 8.7 Fill the commented-out `context:` and `rules:` blocks in `openspec/config.yaml` with the tech stack, the layering rule, the additive-migration rule and the verification command
- [ ] 8.8 Verify: `grep -rn "version 4\|KoogNutritionSpike\|fresh starter\|Not a git repository" AGENTS.md agentic docs` returns nothing; read `CODING.md` and `TESTING.md` cold and confirm each new rule appears once and contradicts no ADR

## 9. Final verification

- [ ] 9.1 `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin --no-daemon` green
- [ ] 9.2 `./gradlew :app:connectedDebugAndroidTest --no-daemon` green on an emulator or device
- [ ] 9.3 `./gradlew :app:assembleDebug --no-daemon` green
- [ ] 9.4 Manual pass on device: attach four photos without a freeze; day and heatmap navigation without stutter; typing in a 10-item meal without lag; rotate mid-estimate and keep the result, draft and photos; save a meal and see the list update with the correct food-type icon on a cache hit; a deliberately wrong Brave key warns instead of silently degrading; settings round-trip; backup export then import on a clean install restores meals, activities and caches
- [ ] 9.5 Final visual sign-off: full re-walk of the baseline path on the S23, frame by frame against the pre-change capture. Every difference must map to an allowlist item (numeric precision, attachment progress, grounding warning, 7.11–7.12). Any unexplained difference blocks the change
- [ ] 9.6 Leave the device as found: original selected day, stats range, and tab; microphone released; no test entries written to the database
