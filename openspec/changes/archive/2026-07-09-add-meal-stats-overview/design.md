## Context

`MealEntriesScreen.kt` currently renders a flat, reverse-chronological list of `MealEntity` rows sourced from `MealDao.getAll()` (no existing date-range or aggregation queries). The app has no navigation graph — `MainActivity.kt` renders `MealCaptureScreen()` directly, which embeds `MealEntriesScreen` — so "main view" stats live inline at the top of that screen, not on a separate destination. `MealEntity.createdAtEpochMillis` (epoch millis, device-local insert time) is the only timestamp available; `title` holds the meal/food name used for "most-common meal" aggregation.

## Goals / Non-Goals

**Goals:**
- Compute meals-logged count, active-days count, current streak, longest streak, most-common meal title, and a per-day heatmap, all scoped to the selected range (All / 30d / 7d).
- Keep aggregation correct against the local device calendar day (not raw epoch millis) so streaks/active-days match user expectation of "today/yesterday".
- Reuse existing Room/DAO/mapper patterns; no new external dependencies.

**Non-Goals:**
- No new Room schema/migration — no new columns or tables; aggregation reads existing `meals` table.
- No per-nutrient stats (calories/macros) in this change — scope is limited to logging-activity stats per the proposal.
- No backend/analytics/telemetry — purely local computation from existing persisted meals.
- No new navigation destination.

## Decisions

- **Aggregation location: in-memory over `getAll()`, not new SQL queries.** The dataset is a single user's personal meal log (expected to stay small, hundreds-to-low-thousands of rows), and streak/heatmap logic (day-boundary bucketing, consecutive-day walk, mode of `title`) is easier to express and test in Kotlin than in SQL. Alternative considered: dedicated `@Query` aggregations (e.g. `COUNT(*) GROUP BY date`) — rejected for now as premature optimization; revisit if `getAll()` becomes a measured bottleneck.
- **Day bucketing uses the device default `ZoneId`**, converting `createdAtEpochMillis` → `LocalDate` via `Instant.ofEpochMilli(...).atZone(ZoneId.systemDefault()).toLocalDate()`. This matches how a user perceives "today" regardless of stored UTC millis.
- **New pure Kotlin computation module** `ui/entries/stats/MealStatsCalculator.kt` (or similar, final path decided during implementation) takes `List<MealEntity>` + a range filter and returns a `MealStats` domain data class (meals count, active days, current streak, longest streak, top meal title, `Map<LocalDate, Int>` day→count for the heatmap). Kept separate from `MealMappers.kt` since it's activity aggregation, not entity↔domain field mapping, but follows the same "plain function over data classes, no Android framework deps" convention so it's unit-testable without Robolectric.
- **Range toggle state (`All`/`30d`/`7d`) is hoisted in `MealEntriesScreen`** (or lifted to `MealCaptureScreen` only if a future screen needs to share it) as simple in-memory Compose state (`rememberSaveable`) — not persisted, resets to a default (30d) on process restart, consistent with the screen having no other persisted UI state today.
- **Streak definition**: current streak = consecutive calendar days with ≥1 meal ending today or yesterday (a gap today doesn't zero the streak until the day is fully over — determined at render time using "today" from the system clock); longest streak = max consecutive-day run within the selected range's underlying full history needed to compute correctly (see Risks — streak computation is intentionally NOT limited to the visible range, see below).
- **Streak computation scope vs. displayed range**: streak figures are computed over the full meal history (not clipped to the 7d/30d selector), since a "current streak" of 45 days would be misleading/truncated if the toggle is set to 7d. Only the heatmap grid and the meals-logged/active-days tiles are clipped to the selected range. This is called out explicitly since it's a likely point of confusion.

## Risks / Trade-offs

- [In-memory aggregation over full `getAll()` could become slow as meal history grows] → Mitigate by keeping the calculator O(n log n) (single sort + linear scan) and revisit with DB-level aggregation only if profiling shows it's needed; personal-use scale makes this unlikely soon.
- [Streak logic computed over unfiltered full history while other tiles are range-filtered could look inconsistent to a user] → Mitigate with a short in-UI label/caption clarifying streaks aren't limited by the range toggle.
- [Device timezone changes (travel) could shift which calendar day older meals fall into between app runs] → Accepted trade-off; matches how the user experiences "today" at time of viewing, no stored timezone data exists to do otherwise.
- [`title` used as "most-common meal" may not be a clean food name if it contains free-text or timestamps] → Reuse existing normalization already present in `MealMappers.kt` (per AGENTS.md, that file has title/text normalization helpers) rather than inventing new logic.

## Open Questions

- Exact Compose file path/organization for the new stats section and calculator (e.g. `ui/entries/stats/` vs. flat under `ui/entries/`) — left to implementation, no architectural impact.
- Whether "most-common meal/food" should dedupe near-identical titles (e.g. "Chicken Salad" vs "chicken salad") beyond what `MealMappers.kt`'s existing normalization already does — default to reusing existing normalization as-is; revisit only if it looks wrong in testing.
