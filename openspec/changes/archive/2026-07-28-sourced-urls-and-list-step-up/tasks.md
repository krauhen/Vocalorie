## 1. Macro color tokens (F1 foundation, no behavior change)

- [x] 1.1 Add theme-aware `MacroColors` (protein blue, carbs goldenrod, fat brick-red) tokens + `macroColors()` accessor in `ui/VocalorieTheme.kt`, tuned for light and dark, with Fat-red chosen distinct from the error/over-budget red
- [x] 1.2 Build + unit tests green (`:app:compileDebugKotlin :app:testDebugUnitTest`)

## 2. Macro colors in list rows and Stats (F1)

- [x] 2.1 Apply the macro tokens to the meal row macro line in `ui/entries/MealEntriesScreen.kt`, keeping the existing Fat/Carbs/Protein text labels
- [x] 2.2 Apply the same tokens to the "Since 00:00" day/window stats header macro line (`SelectableStatsHeader`), keeping labels (tile/heatmap overview shows no macros — out of scope)
- [x] 2.3 Build green

## 3. Row visual polish (F1)

- [x] 3.1 Restructured meal-row header into a title+icon Row (title takes weight, icon top-right); calories already emphasized (titleMedium/Bold); no field removed
- [x] 3.2 Build green

## 4. Food-type classification data path (F1)

- [x] 4.1 Added closed `MealCategory` enum (MEAL/SNACK/DRINK/DESSERT/OTHER) + `category` field on `NutritionAgentResult` (default OTHER)
- [x] 4.2 Prompt in `ai/KoogNutritionAgent.kt` requires choosing exactly one category; contract test updated in lockstep; samples carry a category
- [x] 4.3 Persisted `category` column on `MealEntity`, threaded through all four mappers in `MealMappers.kt` (legacy/unknown → OTHER); `EditableMealDraft`/`SavedMeal` carry it
- [x] 4.4 Added additive `MIGRATION_8_9` (v8 → v9, actual schema was 8 not 4) defaulting `category` to OTHER, registered; contract test asserts version 9 + migration + column
- [x] 4.5 Build + all unit tests green

## 5. Food-type icon on meal rows (F1)

- [x] 5.1 Total `MealCategory.categoryIcon()` mapping (OTHER → Fastfood default); icon rendered top-right of each meal row, tinted `colorScheme.primary`
- [x] 5.2 Build green; legacy meals resolve to OTHER → default icon

## 6. Source provenance binding (B1 core)

- [x] 6.1 Built a real grounding loop in `ai/KoogNutritionAgent.kt`: a Koog `AIAgent` runs `brave_search`/`web_fetch` under a new `RESEARCH_SYSTEM_PROMPT`; fetched URLs collected via a tool callback
- [x] 6.2 `withVerifiedSources` keeps an item `source` only if its normalized form matches a genuinely fetched URL; else blanks it (`normalizeSourceUrl` lowercases scheme+host, drops trailing slash)
- [x] 6.3 Research notes injected into the structured call; `DEFAULT_SYSTEM_PROMPT` (contract-asserted) left intact — research prompt is a separate string
- [x] 6.4 404-hardening: `web_fetch` now checks HTTP status and records a URL only after a 2xx fetch (via `onFetched` callback in `tools/AgentTools.kt`), so a guessed URL that 404s can never become a source
- [x] 6.5 Build + all unit tests green

## 7. Research grounding default (B1)

- [x] 7.1 `groundingEnabled = hasBraveApiKey && maxResearchToolCalls > 0` (default 8) — on by default when a Brave key is present; bounded by `ResearchToolCallLimiter` + `maxAgentIterations`
- [x] 7.2 No key ⇒ agent never runs, all sources blanked (shown as "Estimate · not sourced"), no error surfaced; grounding failures degrade to estimate via `runCatching`
- [x] 7.3 Build + tests green

## 8. Sourced-vs-estimate indicator (B1)

- [x] 8.1 `SourceUrlRow` now renders "Sourced · ‹domain›" (tappable); added `SourceEstimateRow` "Estimate · not sourced" for blank source
- [x] 8.2 Read-only item card shows Sourced vs Estimate; `sourceDomainOrUrl()` extracts host
- [x] 8.3 Build green

## 9. Verification and install

- [x] 9.1 Full `:app:compileDebugKotlin :app:testDebugUnitTest` green
- [x] 9.2 `:app:assembleDebug` succeeds
- [x] 9.3 Installed debug build to the Samsung Galaxy S23 (SM-S911B) — on-device smoke test of a real sourced parse pending user verification

## Notes / follow-ups
- Grounding adds latency + token cost (a second agentic pass on the same OpenAI key), bounded by `maxResearchToolCalls` (default 8) and `maxAgentIterations` (default 64). Not free.
- Cache-reused meals (`CachedMealEntity`) don't store category → they render the OTHER icon. Minor.
- Macro coloring is applied over strongly primary-tinted rows (high-calorie buckets); contrast is acceptable with labels retained but could be tuned further.
