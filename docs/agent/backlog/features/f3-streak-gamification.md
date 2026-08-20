---
description: Strengthen streaks into a real incentive rather than a passive counter.
tags: [backlog, features, gamification, streaks]
---

# F3: Better streak gamification and incentives

**Status:** deferred — blocked on F2 (see `## Deferred` below)
**Source:** personal note, 2026-08-20
**Likely capability:** a new capability spec; adjacent to `openspec/specs/day-nutrition-score/spec.md`, `openspec/specs/meal-stats-overview/spec.md` (guess, not a commitment)

## Raw note (verbatim)
> F3: Better streak gamification/incentives

## What it means
Streaks exist but do not motivate. The ask is for the streak to carry weight — visible progress,
milestones worth reaching, and some resilience so a single bad day does not erase weeks of effort
and with it the reason to keep logging.

## Open questions
- What counts as a streak day — logging anything at all, or hitting the day score / targets?
- Rough scope: this is the vaguest item in the note and needs narrowing before it can be a
  proposal. Milestones? Freeze/grace days? A visual on the overview? Longest-streak history?
- Single-user app with no backend, so no leaderboards or social comparison — incentives must be
  self-referential.
- Streak state needs persisting and surviving backup/restore.

## Investigation (2026-08-20)
Confirmed passive, and confirmed with nothing to build on: streak state is two ints, recomputed on
every read and never persisted.

- `currentStreak` / `longestStreak` on `MealStats` (`ui/entries/stats/MealStats.kt:30-31`), computed
  at `MealStatsCalculator.kt:35-42` and `:104` (longest), `:227-236` (current — walks back from the
  most recent logged date over the set of dates with any meal).
- **A day counts if anything at all was logged** — no score condition, no goal condition. So the
  streak measures logging habit, not nutrition quality.
- Rendered as two plain tiles, `"Streak" "${n}d"` and `"Best"` (`MealStatsOverview.kt:155-156`).
  No milestones, no rewards, no notifications, no persistence.

Device check (2026-08-20): tiles read `52d Streak` and `52d Best` — identical, i.e. the user is at
their all-time record with no visible next target. That is the concrete gap this feature closes.

## Notes for the proposal
- Deciding whether a day should require hitting a goal (not just any entry) changes what the
  existing 52-day streak means — it could reset the user's record. Call that out as a decision.
- Any persisted milestone or achievement state needs a Room migration plus the
  `BACKUP_SCHEMA_VERSION` bump; a purely derived milestone display needs neither.

## Files
`ui/entries/stats/MealStatsCalculator.kt`, `ui/entries/stats/MealStats.kt`,
`ui/entries/stats/MealStatsOverview.kt`, plus new persistence if state must survive.

## Deferred (2026-08-20)

**Status: deferred, not proposed.** Blocked on F2, and deliberately so.

- **The central decision depends on F2's answer.** "What counts as a streak day" is only answerable
  once the goal model is settled. F2's findings ([f2-findings.md](f2-findings.md)) show targets are
  point values with tolerance bands, and that carbs is a fixed percentage while fat is the derived
  remainder — the inverse of the target profile. Two of the gaps F2 names (range-valued targets, and
  carbs as the remainder) would reshape `NutritionGoals` and the adherence curves that read it. A
  qualifying-day rule defined against goals is defined against whichever model survives that.
- **Proposing now can destroy the user's record.** A day currently counts if *anything* was logged
  (`ui/entries/stats/MealStatsCalculator.kt:227-236`). Requiring a score or a goal hit would recompute
  the live `52d Streak / 52d Best` reading down to whatever the stricter rule yields, on first launch,
  with no warning and no way back — the recomputation is derived on every read, so there is no stored
  history to fall back on.
- **The persistence question is settled but not the trigger for it.** A persisted milestone needs a
  Room migration plus a `BACKUP_SCHEMA_VERSION` bump and a widened `SUPPORTED_BACKUP_SCHEMA_VERSIONS`
  in the same commit; a purely derived milestone display needs neither. Which one is right follows
  from the qualifying-day decision, so it stays open too.

**Call for the next session, on evidence.** If the goal model is rebuilt for range-valued targets,
F3 should be reconsidered as part of that change rather than as a standalone one — the qualifying-day
rule and the range definition are then the same decision. If the goal model is left as it is, F3 can
stand alone with a qualifying-day rule stated against the current point targets.
