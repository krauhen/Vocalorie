## 1. Gate the tips on the day score (day-score-tips logic)

- [ ] 1.1 In `app/src/main/java/com/example/vocalorie/ui/entries/stats/DayScoreTips.kt`, compute the day's score inside `dayScoreTips` (`:75-110`) from the parameters it already receives, calling `nutritionScore(totals, goals, activityBurnedKcal)` from `ui/entries/stats/MealStatsCalculator.kt` — the same call `MealEntriesScreen.kt:109-111` makes for the header (design D2).
- [ ] 1.2 Add `private fun tipsAllowed(score: Int): Boolean = score < DAY_SCORE_TIP_THRESHOLD` with `internal const val DAY_SCORE_TIP_THRESHOLD = 50`, beside `LATE_DAY_CUTOFF` (`DayScoreTips.kt:45`). Return `emptyList()` when it is false, directly after the existing `if (!totals.hasData()) return emptyList()` guard (`:79`).
- [ ] 1.3 Cap the returned list at the three highest-ranked tips: `.take(MAX_DAY_SCORE_TIPS)` with `internal const val MAX_DAY_SCORE_TIPS = 3`, applied after `sortedByDescending { it.rank }` (`DayScoreTips.kt:104-107`) so the cap follows the existing rank order and the late-day filter (design D4). Leave the ranking weights, the catalogue and `EAT_MORE_KINDS` untouched.
- [ ] 1.4 Extend `app/src/test/java/com/example/vocalorie/ui/entries/stats/DayScoreTipsTest.kt`: a day scoring 50 yields no tips; a day scoring 49 yields its ranked tips; a day scoring in the seventies with one band violation yields none; a low-scoring day with six shortfalls yields exactly three, and they are the three highest-ranked; the late-day suppression and rank order of the existing cases still hold.
- [ ] 1.5 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 2. Retire the UI-side tip cap (day-score-tips presentation)

- [ ] 2.1 In `app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesStatsHeader.kt`, drop `EXPANDED_TIP_COUNT` (`:150`) and the `tips.take(EXPANDED_TIP_COUNT)` in the expanded branch (`:187`), rendering the whole list — it is already capped at three upstream by task 1.3.
- [ ] 2.2 Keep `COLLAPSED_TIP_COUNT = 3` (`:149`) as the rotation width only, or replace its use at `:171` with `tips.size`; either way the rotating count and the list length now coincide. Leave the crossfade, the tap-to-expand toggle and the reword affordance untouched.
- [ ] 2.3 Confirm nothing else consumes the tip list: `DayScoreTipsSection` (`MealEntriesStatsHeader.kt:158`) is the only renderer, reached from `MealEntriesScreen.kt`'s header call. Record that no other call site needed a change.
- [ ] 2.4 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 3. One clock for the entries screen (entry-day-targeting state)

- [ ] 3.1 Add `now: Instant` as a parameter of `MealEntriesScreen` (`app/src/main/java/com/example/vocalorie/ui/entries/MealEntriesScreen.kt:56-77`) and delete the local `var now by remember { mutableStateOf(Instant.now()) }` together with its `LaunchedEffect(meals, activities)` (`:84-85`), so the screen holds no clock of its own (design D5).
- [ ] 3.2 Pass `state.now` (`ui/capture/MealCaptureUiState.kt:43`) at the single call site, `app/src/main/java/com/example/vocalorie/ui/MealCaptureScreen.kt:77`. No other caller exists.
- [ ] 3.3 Replace the pull-to-refresh assignment `now = Instant.now()` (`MealEntriesScreen.kt:127`) with a call to the existing `onRefreshNow` path — expose `MealCaptureViewModel.refreshNow()` (`ui/capture/MealCaptureViewModel.kt:160-162`) to the screen as a callback parameter, so refreshing still advances exactly one clock.
- [ ] 3.4 Confirm every `now`-keyed `remember` in the screen (`:89-91`, `:104-105`, `:189`) now recomputes from the state-holder clock, and that no `Instant.now()` call remains anywhere in `MealEntriesScreen.kt`.
- [ ] 3.5 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 4. Advance the clock on resume and at midnight (entry-day-targeting behaviour)

- [ ] 4.1 In `MealEntriesScreen.kt`, add a lifecycle-aware effect that calls the refresh callback from 3.3 on `ON_RESUME`, following the pattern already used for voice `ON_STOP` in `ui/voice/VoiceInputOverlay.kt:483-484` rather than introducing a new lifecycle idiom.
- [ ] 4.2 Add a `LaunchedEffect(now, zone)` that computes the duration from `now` to the next local midnight, `delay`s that duration plus a small margin, then calls the refresh callback — the effect re-keys on the new `now`, so it re-arms itself for the following day (design D6). No per-minute ticker.
- [ ] 4.3 Add `fun dayOffsetAfterDayChange(offset: Int, daysPassed: Long): Int` to `app/src/main/java/com/example/vocalorie/ui/entries/MealTimeWindows.kt`, beside `selectedDayWindow` (`:76-86`): offset 0 returns 0; any other offset returns `offset + daysPassed` coerced into `Int` (design D7). Pure Kotlin, no Android types.
- [ ] 4.4 In `MealCaptureViewModel`, apply it when the clock advances across a day boundary: in `advanceNow()` (`ui/capture/MealCaptureViewModel.kt:164-168`), compare `LocalDate.ofInstant` of the previous and the new reading in `zone` and, when they differ, update `selectedDayOffset` (`:185`) through `dayOffsetAfterDayChange` in the same `update { }` block, so the clock and the offset never publish out of step.
- [ ] 4.5 Extend `app/src/test/java/com/example/vocalorie/ui/entries/MealTimeWindowsTest.kt` for `dayOffsetAfterDayChange`: offset 0 over one day stays 0; offset 1 becomes 2; offset -1 (tomorrow) becomes 0; offset 3 over two days becomes 5; zero days passed changes nothing.
- [ ] 4.6 Extend `app/src/test/java/com/example/vocalorie/ui/capture/MealCaptureViewModelTest.kt` with a fake clock crossing midnight (`FakeCaptureEnvironment.kt` already supplies one): advancing the clock past midnight at offset 0 leaves the offset at 0 and moves `now`; at offset 1 it moves the offset to 2 so the same date stays selected; and a save after the crossing stamps the new day (`newEntryTimestampMillis`, `:174-175`).
- [ ] 4.7 Extend `app/src/test/java/com/example/vocalorie/ui/capture/DayScoreTipsStateTest.kt`: crossing midnight recomputes the tips for the new day, so yesterday's tips do not survive the rollover.
- [ ] 4.8 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`

## 5. Specs and backlog (documentation)

- [ ] 5.1 Apply this change's `day-score-tips` delta to `openspec/specs/day-score-tips/spec.md`, replacing the "Tips shown only for today, only with logged meals" requirement (`:91`) and the "Rotating presentation with tap to expand" requirement (`:102`) with the modified text, scenarios included.
- [ ] 5.2 Apply this change's `entry-day-targeting` delta to `openspec/specs/entry-day-targeting/spec.md`, adding "The viewed day tracks the wall clock" after the existing "New entries are dated to the viewed day" requirement (`:9`), which is unchanged.
- [ ] 5.3 Confirm the backlog bookkeeping is already done — the `b3-*` and `b4-*` files are deleted and `docs/agent/backlog/bugs/README.md` lists both under `## Promoted`, pointing at this change. Their investigations live in this change's `proposal.md`, and git holds the original files. No edit expected.
- [ ] 5.4 Verify: `openspec validate 2026-08-20-gate-day-score-tips-and-live-day-rollover --strict` passes, and the main specs still parse (`openspec list --specs`).

## 6. On-device confirmation

- [ ] 6.1 Install: `./gradlew :app:installDebug --no-daemon`
- [ ] 6.2 On a day whose score is above 50, confirm the tips section is absent while the score number is still shown; on a low-scoring day confirm at most three tips, and that expanding shows no fourth.
- [ ] 6.3 Set the device clock to a few minutes before midnight, leave the entries screen open, and confirm the header pill's date and label advance on their own — no save, no pull-to-refresh — and that the day's entries and totals follow.
- [ ] 6.4 Repeat 6.3 while viewing yesterday and confirm the same calendar date stays selected after the rollover; then background the app across midnight and confirm the resume path resolves the new day.
- [ ] 6.5 Verify: `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest --no-daemon`
