## Why

Two problems degrade trust and readability of logged nutrition data:

- **B1 — Broken source URLs.** Each food item's `source` URL is authored by the LLM independently of the numbers and validated only for URL *shape*, so it is usually a bare domain or a 404. The numbers are not actually read from the linked page, so the "source" is not real provenance.
- **F1 — Flat, low-signal list.** Meal and activity rows share a near-identical primary-tinted style, distinguished mostly by the tab. There is no at-a-glance sense of *what kind* of food an entry is, and macro values read as undifferentiated text.

## What Changes

**B1 — Grounded source URLs (provenance-first)**
- Prefer real provenance: run Brave Search + WebFetch, derive an item's numbers from a fetched page, and store *only* a URL that was actually fetched during the run. The model may no longer emit an invented URL.
- Graceful fallback hierarchy: sourced page → LLM general-knowledge estimate (no fake URL) → alternate real page.
- Research tools default **on when a Brave API key is present**; with no key, silently fall back to LLM estimate (no error nagging).
- Per-item UI badge distinguishing **"sourced from ‹domain›"** vs a subtle **"estimate"** tag so trust is explicit.
- **BREAKING** (spec-level): reverses the current `food-sources` rule that lets the model construct a confident URL without fetching it.

**F1 — List design step-up**
- Polish meal/activity rows: clearer typographic hierarchy, spacing, and calorie emphasis.
- Top-right, theme-colored **food-type icon** per meal row. Categories: **Meal / Snack / Drink / Dessert / Other**, classified by the LLM during parse (new structured field). Old entries render the **Other** icon.
- **Macro color coding** — Protein = blue, Carbs = yellow, Fat = red — applied in **list rows and Stats** (not the editor for now). Text labels are retained so color is never the sole signal; shades are tuned per light/dark theme, and the Fat-red is chosen to not collide with the over-budget calorie-state red.

## Capabilities

### New Capabilities
- `meal-type-classification`: LLM-produced food-type category (Meal/Snack/Drink/Dessert/Other) on the nutrition result, its DTO field, Room persistence + additive migration, and the category→icon mapping.
- `entries-list-presentation`: visual step-up of the meal/activity entry rows — typographic/spacing polish, the top-right theme-colored food-type icon, and semantic macro color coding within list rows.

### Modified Capabilities
- `food-sources`: source URLs must be genuine fetched provenance with graceful estimate fallback; research tools default on when a Brave key exists; add per-item sourced-vs-estimate UI badge.
- `meal-stats-overview`: macro values shown in the stats overview adopt the same Protein/Carbs/Fat semantic color coding.

## Impact

- **AI/parsing**: `ai/KoogNutritionAgent.kt` (system prompt: fetch-then-cite grounding, category output), `model/NutritionEstimateDtos.kt` (category field), `tools/AgentTools.kt` + research call limiter (default-on when key present, track fetched URLs).
- **Contract test**: `ai/NutritionPromptContractTest.kt` must be updated deliberately alongside prompt/DTO wording changes.
- **Data**: `data/MealEntity.kt`, `data/MealMappers.kt` (accept `source` only if it matches a fetched URL; map new category), `data/VocalorieDatabase.java` (additive migration, schema v4 → v5).
- **UI**: `ui/entries/MealEntriesScreen.kt` (row polish, food-type icon, macro colors, stats colors, sourced/estimate badge), `ui/components/CommonUi.kt` (`SourceUrlRow`, calorie-state styles), macro color tokens in `ui/VocalorieTheme.kt`.
- **Settings/Network**: Brave key presence now drives default research behavior; real network calls remain opt-in-safe (no key → no calls).
- **Dependencies**: none new expected; confirm before adding.
