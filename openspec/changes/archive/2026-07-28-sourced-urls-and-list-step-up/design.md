## Context

Vocalorie parses meals via a Koog agent against an OpenAI model, producing a structured `NutritionAgentResult` with per-item `FoodItemEstimate`s. Two weaknesses motivate this change:

- The per-item `source` URL is authored by the model independently of the numbers and validated only for URL shape (`toConcreteSourceUrlOrBlank` in `MealMappers.kt`), so it is usually a bare domain or a 404. Brave Search / WebFetch tools exist (`AgentTools.kt`) but are gated by `ResearchToolCallLimiter` (often 0) and, even when used, their real result URLs are only injected as text — nothing binds a stored `source` to an actually-fetched page.
- The entries list (`MealEntriesScreen.kt`) distinguishes meals vs activities mostly via the `TabRow`; rows share a primary-tinted style, carry no food-type signal, and show macros as plain text.

Constraints: additive Room migrations only (no destructive fallback; currently schema v4). Real network calls must stay off without a stored Brave key. The prompt/DTO contract test (`NutritionPromptContractTest.kt`) asserts exact wording and must be updated deliberately.

## Goals / Non-Goals

**Goals:**
- Bind each item's stored `source` to a URL actually fetched during the run; graceful fallback to a blank-source LLM estimate.
- Default research grounding on when a Brave key exists; silent estimate fallback with no key.
- Surface sourced-vs-estimate per item.
- Add an LLM-classified food-type category (Meal/Snack/Drink/Dessert/Other) persisted via additive migration, mapped to a theme-tinted top-right row icon.
- Semantic macro colors (Protein blue / Carbs yellow / Fat red) in list rows and Stats, retaining text labels, tuned per theme, with Fat-red distinct from over-budget red.

**Non-Goals:**
- Macro colors in the meal editor (deferred).
- Mixing meals and activities into one list (tabs stay).
- Rich per-DB search-URL construction or scraping structured nutrition tables from arbitrary pages beyond what WebFetch already returns.
- A hard requirement that every item be sourced; grounding is best-effort.

## Decisions

**D1 — Provenance binding via a fetched-URL allowlist.** The run tracks the set of URLs actually passed to WebFetch (and successfully fetched). At mapping time, an item's model-emitted `source` is kept only if it exactly matches a fetched URL; otherwise it is blanked. Chosen over (a) trusting the model, and (b) re-fetching at save time to verify — the allowlist is cheaper, deterministic, and impossible for the model to bypass. Reachability is implied by "we already fetched it this run."
- *Alternative considered:* HTTP HEAD verification at save time — adds latency and a network dependency at persistence; rejected as redundant once the allowlist guarantees the URL was fetched.

**D2 — Grounding default keyed on Brave key presence.** Research-tool enablement is derived: key present ⇒ default on; key absent ⇒ hard off (no real calls, silent LLM fallback). Keeps the "no key, no network" safety invariant while removing the per-parse friction.

**D3 — Category as a closed enum on the result + entity.** New `category` field on `NutritionAgentResult` (LLM-chosen from a fixed set) and a persisted column on `MealEntity`. Closed set → total icon mapping, no surprise icons. Legacy rows and any unrecognized value coerce to `Other`.

**D4 — Additive migration v4 → v5.** Add a nullable/defaulted `category` column; `MIGRATION_4_5` with a default of `Other`. No destructive fallback, consistent with existing migration discipline.

**D5 — Macro colors as shared theme tokens.** Define `macroProtein`/`macroCarbs`/`macroFat` color tokens in `VocalorieTheme.kt`, resolved per light/dark. List rows and Stats both consume them, so the two surfaces stay consistent. Fat-red is chosen as a distinct hue/shade from the calorie-state over-budget red used in `CommonUi.kt`.

**D6 — Sourced-vs-estimate indicator co-located with the source affordance.** Extend `SourceUrlRow` (`CommonUi.kt`): non-blank source ⇒ "sourced · ‹domain›" tappable; blank ⇒ subtle "estimate" chip, non-clickable.

## Risks / Trade-offs

- **Grounding latency/cost** → best-effort, capped by the existing call limiter; no per-item hard requirement, so a slow/absent fetch degrades to an estimate rather than blocking.
- **Model returns a normalized/redirected URL that doesn't string-match the fetched URL** → normalize both sides (trim, lowercase host, strip trailing slash) before comparing, so trivial mismatches don't wrongly blank a real source.
- **Yellow (carbs) contrast on light theme** → pick an amber/dark-yellow token for light theme, brighter for dark.
- **Two reds (fat vs over-budget)** → choose distinct hue/saturation and verify side by side; retain text labels so meaning survives even if hues read similar.
- **Contract test churn** → update `NutritionPromptContractTest.kt` in the same increment as each prompt/DTO wording change, deliberately.
- **Color-only encoding a11y** → mitigated by keeping textual macro labels everywhere.

## Migration Plan

1. Bump `VocalorieDatabase` schema v4 → v5 with additive `MIGRATION_4_5` adding `category` defaulting to `Other`.
2. Ship DTO + prompt + mapper changes with the contract test updated together.
3. Rollback: revert code; the v5 column is additive and harmless if unused. Do not add destructive fallback.

## Open Questions

- Exact icon glyphs per category (Material icons) — pick sensible defaults during implementation; not blocking.
- Whether the "estimate" chip should also appear at meal level (aggregate) or only per item — default to per item for now.
