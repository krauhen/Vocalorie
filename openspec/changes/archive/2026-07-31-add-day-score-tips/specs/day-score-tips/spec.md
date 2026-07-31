## ADDED Requirements

### Requirement: Tips derived from the day score's own components
The system SHALL derive the day's tips from the same adherence sub-scores the day nutrition score is built from — the calorie, protein, carbs and fat adherence values and the saturated-fat/sugar/salt quality penalty defined by the `day-nutrition-score` capability — and SHALL NOT introduce independent thresholds or targets. A component whose sub-score is already 100 SHALL produce no tip. Consequently a tip can never contradict the score shown beside it.

Tips SHALL be ranked by the score points the shortfall costs: `weight × (100 − subScore)`, using the score's own weights (calories 0.40, protein 0.30, carbs 0.15, fat 0.15). Each quality nutrient's rank SHALL be its own penalty share (`0.10 × overage × 100`), which places quality tips below any material macro gap. The activity tip defined below SHALL rank at half the calorie tip's value, so it always appears directly after it.

Ranking, wording and time-gating SHALL be pure, JVM-testable logic with no Android or network dependency (`app/src/main/java/com/example/vocalorie/ui/entries/stats/DayScoreTips.kt`), reusing the sub-score functions in `app/src/main/java/com/example/vocalorie/ui/entries/stats/MealStatsCalculator.kt`.

#### Scenario: The costliest shortfall ranks first
- **WHEN** a day's calorie adherence is 60 (weight 0.40, weighted loss 16.0) and its fat adherence is 20 (weight 0.15, weighted loss 12.0)
- **THEN** the calorie tip is ranked above the fat tip, even though the fat sub-score is the lower number

#### Scenario: An on-target component produces no tip
- **WHEN** a day's protein adherence is 100
- **THEN** no protein tip appears in the list

#### Scenario: A quality tip ranks below a macro gap
- **WHEN** a day's sugar is at twice its limit (penalty share 10.0) and its carbs adherence is 20 (weighted loss 12.0)
- **THEN** the carbs tip is ranked above the sugar tip

#### Scenario: A perfect day produces no tips
- **WHEN** a day's totals hit every target with all quality nutrients within their limits, so the score is 100
- **THEN** the tip list is empty and the tips section is not shown

#### Scenario: Tips are goal-relative
- **WHEN** two configurations log identical meals but have different calorie/macro goals
- **THEN** each derives its tips against its own targets, so the same food can yield different tips

### Requirement: Tip catalogue and wording
The system SHALL map each shortfall to exactly one tip, written in blunt second person and **5–10 words**, where a word is a whitespace-separated token containing at least one letter or digit — so a standalone dash or other punctuation token does not consume a word slot. The catalogue SHALL consist of exactly **twelve** entries, one per shortfall the day score can express: calories under target; calories over target; calories far over target; protein under target; carbs under target; carbs over target; fat under target; fat over target; saturated fat over its limit; free sugar over its limit; salt over its limit; and the activity case below. It SHALL NOT contain an entry for protein over target, because the score does not penalize protein overshoot, and SHALL NOT contain entries for anything the score does not measure. Wording SHALL name a concrete action, SHALL NOT instruct the user to fast or skip meals, and SHALL NOT make medical claims or use shame/streak framing.

The **calories far over** tip is the bluntest permitted wording and SHALL be capped at suggesting the user stop eating for the remainder of the day, not at instructing abstinence.

#### Scenario: Far-over-budget tip wording
- **WHEN** a day's calorie adherence has fallen to 0 because intake is at or beyond 1.25× the activity-adjusted calorie target
- **THEN** the tip reads "You're well over budget — consider stopping for today." and no tip anywhere in the catalogue instructs the user to eat nothing

#### Scenario: Every tip fits the length rule
- **WHEN** any tip in the catalogue is rendered
- **THEN** it contains between 5 and 10 words inclusive

#### Scenario: Protein overshoot produces no tip
- **WHEN** a day's protein is 150% of the protein target, which the score treats as fully on target
- **THEN** no tip advises reducing protein, and the catalogue contains no protein-over entry

#### Scenario: Catalogue is exhaustive
- **WHEN** the catalogue is enumerated
- **THEN** it contains exactly twelve entries and no entry addresses a nutrient or behaviour the day score does not measure

### Requirement: Activity tip when over budget with nothing logged
The system SHALL emit one activity tip when the day is over its calorie budget and the day has no logged activity, using that day's logged activities from the `activity-logging` capability. This is the only tip that reads activity data rather than nutrition totals. When the day already has a logged activity, no activity tip SHALL appear regardless of the calorie overshoot.

#### Scenario: Over budget with no activity logged
- **WHEN** a day's intake exceeds its activity-adjusted calorie target and the day has no logged activities
- **THEN** the tip "Over budget — log some sport to offset it." appears directly after the calorie tip

#### Scenario: Activity already logged suppresses the tip
- **WHEN** a day is over its calorie budget and has a logged activity burning 400 kcal
- **THEN** no activity tip appears

#### Scenario: Within budget suppresses the tip
- **WHEN** a day's intake is at or under its activity-adjusted calorie target and no activity is logged
- **THEN** no activity tip appears

### Requirement: Late-day suppression of eat-more tips
The system SHALL suppress tips that ask the user to consume more — calories under, protein under, carbs under and fat under — at or after **21:00 local time**, because they are no longer actionable. Over-budget, quality and activity tips SHALL remain. If suppression leaves the list empty, the tips section SHALL be hidden.

#### Scenario: Eat-more tip suppressed late in the day
- **WHEN** the local time is 23:00 and the day's protein is well short of target
- **THEN** no protein tip appears

#### Scenario: Over-budget tip survives late in the day
- **WHEN** the local time is 23:00 and the day is over its calorie budget
- **THEN** the over-budget tip still appears

#### Scenario: Same day earlier still shows the eat-more tip
- **WHEN** the local time is 18:00 and the day's protein is well short of target
- **THEN** the protein tip appears

#### Scenario: Suppression emptying the list hides the section
- **WHEN** the local time is 23:00 and every shortfall on the day is an eat-more shortfall
- **THEN** the tips section is not shown

### Requirement: Tips shown only for today, only with logged meals
The system SHALL show the tips section only when the selected day is the current day and that day has at least one logged meal. On a day with no logged meals — for which no score exists either — the section SHALL be absent rather than empty, and on any past or future selected day the section SHALL be absent.

#### Scenario: Day with no logged meals hides the section
- **WHEN** today has no logged meals
- **THEN** the tips section is not shown at all, matching the score number's absence

#### Scenario: A past day shows no tips
- **WHEN** the user selects a previous day that has logged meals and a computed score
- **THEN** the score number is shown but the tips section is not

### Requirement: Rotating presentation with tap to expand
The system SHALL show at most one tip at a time in the daily nutrition section, rotating through the top **3** ranked tips with a crossfade at the configured interval. Tapping the tip SHALL stop rotation and expand the full ranked list, capped at **5** tips; collapsing SHALL resume rotation. Rotation SHALL not run when fewer than two tips exist. The section SHALL remain compact enough to sit beneath the score without displacing the existing header content (kcal total, burned, balance, macro line, histogram).

#### Scenario: Rotation cycles the top three
- **WHEN** five tips are ranked and the rotation interval is 5 seconds
- **THEN** the section displays one tip at a time, crossfading between the top three every 5 seconds

#### Scenario: Tap expands the full list and freezes rotation
- **WHEN** the user taps the displayed tip
- **THEN** all ranked tips (up to 5) are listed and rotation stops until the user collapses the list again

#### Scenario: A single tip does not rotate
- **WHEN** exactly one tip is ranked
- **THEN** that tip is shown statically with no crossfade

### Requirement: Configurable rotation interval
The system SHALL provide a persisted "tip rotation interval" setting in seconds, defaulting to **5**, accepting whole values from **2 to 60**, and additionally accepting **0** to mean no rotation — the top tip is shown statically until the user expands the list. The setting SHALL be editable in Settings via a numeric field following the existing numeric-setting pattern, and SHALL persist across app restarts. Input that is not a whole number, or is outside the accepted set, SHALL be rejected with a message and SHALL leave the stored value unchanged.

#### Scenario: Default interval on a fresh install
- **WHEN** the user has never set a rotation interval
- **THEN** tips rotate every 5 seconds

#### Scenario: Changing the interval changes the cadence
- **WHEN** the user saves a rotation interval of 10
- **THEN** tips crossfade every 10 seconds, and the value survives an app restart

#### Scenario: Zero disables rotation
- **WHEN** the user saves a rotation interval of 0
- **THEN** only the top-ranked tip is shown, with no crossfade, until the user taps to expand

#### Scenario: Out-of-range input is rejected
- **WHEN** the user saves a rotation interval of 90, or of 1, or a non-numeric value
- **THEN** the setting reports the problem and the previously stored interval is unchanged

### Requirement: Optional LLM rewording on explicit request only
The system SHALL never call the LLM for tips automatically. It SHALL offer a refresh affordance in the tips section that asks the model to reword the already-derived rule tips — same count, same order, same meaning, each 5–10 words — and SHALL NOT let the model add, drop, reorder or invent tips. The affordance SHALL be absent when no OpenAI API key is stored.

A reworded reply SHALL replace the displayed wording only if it contains exactly as many tips as the rule set and every tip is 5–10 words. Any other outcome — missing key, network failure, model error, wrong tip count, or any tip outside the length rule — SHALL leave the rule wording displayed unchanged, with **no warning, error or other user-visible failure state**. Validation SHALL be wholesale: a reply is accepted in full or discarded in full, never merged per tip. Rule tips SHALL remain the source of truth and SHALL render immediately, before and independently of any rewording.

#### Scenario: Valid rewrite replaces the wording
- **WHEN** the user taps refresh with three rule tips ranked and the model returns three tips of 5–10 words each
- **THEN** the displayed tips show the reworded text in the same order, and the ranking is unchanged

#### Scenario: An over-length reply is discarded entirely
- **WHEN** the model returns three tips and one of them is 12 words long
- **THEN** all three rule tips remain displayed unchanged and no error is shown

#### Scenario: A reply with the wrong tip count is discarded
- **WHEN** three rule tips were sent and the model returns two tips
- **THEN** the three rule tips remain displayed unchanged and no error is shown

#### Scenario: No stored key means no refresh affordance
- **WHEN** no OpenAI API key is stored
- **THEN** the tips section shows the rule tips with no refresh affordance, and no call is attempted

#### Scenario: Offline failure is silent
- **WHEN** the user taps refresh and the request fails with a network error
- **THEN** the rule tips remain displayed and no warning appears anywhere in the header

#### Scenario: Tips render without waiting for the model
- **WHEN** the day's tips are first derived
- **THEN** the rule wording is displayed immediately without any LLM call having been made
