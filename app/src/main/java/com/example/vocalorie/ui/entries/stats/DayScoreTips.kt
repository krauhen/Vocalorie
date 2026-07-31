package com.example.vocalorie.ui.entries.stats

import com.example.vocalorie.model.NutritionGoals
import java.time.LocalTime

/**
 * Actionable tips for today, ranked by how many score points each shortfall costs.
 *
 * Pure: no clock, no Android, no network. Every trigger reads a sub-score [nutritionScore] already
 * computes, so a tip and the number above it can never disagree. Lives in the same package as
 * [MealStatsCalculator]'s `internal` adherence functions specifically so they stay `internal`.
 */

/** The shortfall a tip addresses. Stable across rewording, so it can key a list or an animation. */
enum class DayScoreTipKind {
    CALORIES_UNDER,
    CALORIES_OVER,
    CALORIES_FAR_OVER,
    PROTEIN_UNDER,
    CARBS_UNDER,
    CARBS_OVER,
    FAT_UNDER,
    FAT_OVER,
    SATURATED_FAT_OVER,
    SUGAR_OVER,
    SALT_OVER,
    NO_ACTIVITY_WHILE_OVER,
}

/** One ranked tip: what it is about, and the wording currently shown. */
data class DayScoreTip(
    val kind: DayScoreTipKind,
    val text: String,
)

/** The tips asking the user to eat more — pointless once the day is effectively over (D5). */
private val EAT_MORE_KINDS = setOf(
    DayScoreTipKind.CALORIES_UNDER,
    DayScoreTipKind.PROTEIN_UNDER,
    DayScoreTipKind.CARBS_UNDER,
    DayScoreTipKind.FAT_UNDER,
)

/** At or after this local hour, the eat-more tips are suppressed. */
internal val LATE_DAY_CUTOFF: LocalTime = LocalTime.of(21, 0)

/** The catalogue. One string per shortfall the score can express; each is 5-10 words. */
internal val DAY_SCORE_TIP_TEXTS: Map<DayScoreTipKind, String> = mapOf(
    DayScoreTipKind.CALORIES_UNDER to "You're under budget — eat a bit more today.",
    DayScoreTipKind.CALORIES_OVER to "Ease off — you're over your calorie budget today.",
    DayScoreTipKind.CALORIES_FAR_OVER to "You're well over budget — consider stopping for today.",
    DayScoreTipKind.PROTEIN_UNDER to "Protein is short — add a protein-heavy meal.",
    DayScoreTipKind.CARBS_UNDER to "Carbs are low — add rice, bread or fruit.",
    DayScoreTipKind.CARBS_OVER to "Carbs are high — cut back on starchy sides.",
    DayScoreTipKind.FAT_UNDER to "Fat is low — add nuts, oil or dairy.",
    DayScoreTipKind.FAT_OVER to "Fat is high — trim oils and fatty cuts.",
    DayScoreTipKind.SATURATED_FAT_OVER to "Saturated fat is high — choose leaner options today.",
    DayScoreTipKind.SUGAR_OVER to "Sugar is high — skip sweets and sugary drinks.",
    DayScoreTipKind.SALT_OVER to "Salt is high — go easy on salty foods.",
    DayScoreTipKind.NO_ACTIVITY_WHILE_OVER to "Over budget — log some sport to offset it.",
)

private data class RankedTip(val kind: DayScoreTipKind, val rank: Double)

/**
 * The day's shortfalls, highest leverage first.
 *
 * Leverage is `weight × (100 − subScore)` with the score's own weights, so a 20-point calorie gap
 * outranks a 20-point fat gap exactly as the score weights them. Quality nutrients rank by their
 * own dock (at most 10), below any material macro gap. Empty when nothing was logged.
 */
fun dayScoreTips(
    totals: DailyNutritionTotals,
    goals: NutritionGoals = NutritionGoals.DEFAULT,
    activityBurnedKcal: Double = 0.0,
    hasLoggedActivity: Boolean = false,
    localTime: LocalTime,
): List<DayScoreTip> {
    if (!totals.hasData()) return emptyList()

    val targets = goals.macroTargets()
    val calorieTarget = goals.calorieGoalKcal + 0.5 * activityBurnedKcal
    val calorieRank = 0.40 * (100.0 - calorieAdherence(totals.caloriesKcal, calorieTarget))
    val overages = qualityOverages(totals, calorieTarget)

    val ranked = buildList {
        calorieKind(totals.caloriesKcal, calorieTarget)?.let { add(RankedTip(it, calorieRank)) }
        if (totals.caloriesKcal > calorieTarget && !hasLoggedActivity) {
            add(RankedTip(DayScoreTipKind.NO_ACTIVITY_WHILE_OVER, 0.5 * calorieRank))
        }
        if (targets.proteinG > 0.0 && totals.proteinG < targets.proteinG) {
            add(RankedTip(DayScoreTipKind.PROTEIN_UNDER, 0.30 * (100.0 - proteinAdherence(totals.proteinG, targets.proteinG))))
        }
        bandKind(totals.carbsG, targets.carbsG, DayScoreTipKind.CARBS_UNDER, DayScoreTipKind.CARBS_OVER)
            ?.let { add(RankedTip(it, 0.15 * (100.0 - carbsAdherence(totals.carbsG, targets.carbsG)))) }
        bandKind(totals.fatG, targets.fatG, DayScoreTipKind.FAT_UNDER, DayScoreTipKind.FAT_OVER)
            ?.let { add(RankedTip(it, 0.15 * (100.0 - fatAdherence(totals.fatG, targets.fatG)))) }
        addQuality(DayScoreTipKind.SATURATED_FAT_OVER, overages.saturatedFat)
        addQuality(DayScoreTipKind.SUGAR_OVER, overages.sugar)
        addQuality(DayScoreTipKind.SALT_OVER, overages.salt)
    }

    val late = !localTime.isBefore(LATE_DAY_CUTOFF)
    return ranked
        .filterNot { late && it.kind in EAT_MORE_KINDS }
        .sortedByDescending { it.rank }
        .map { DayScoreTip(it.kind, DAY_SCORE_TIP_TEXTS.getValue(it.kind)) }
}

private fun MutableList<RankedTip>.addQuality(kind: DayScoreTipKind, overage: Double) {
    if (overage > 0.0) add(RankedTip(kind, QUALITY_DOCK_PER_NUTRIENT * overage * 100.0))
}

/** Which calorie tip the day earns, mirroring [calorieAdherence]'s bands. Null inside the target band. */
private fun calorieKind(calories: Double, target: Double): DayScoreTipKind? {
    if (target <= 0.0) return null
    val r = calories / target
    return when {
        r >= 1.25 -> DayScoreTipKind.CALORIES_FAR_OVER
        r > 1.05 -> DayScoreTipKind.CALORIES_OVER
        r < 0.95 -> DayScoreTipKind.CALORIES_UNDER
        else -> null
    }
}

/** Carbs and fat share one shape: full credit in [0.8, 1.2], a tip either side. */
private fun bandKind(value: Double, target: Double, under: DayScoreTipKind, over: DayScoreTipKind): DayScoreTipKind? {
    if (target <= 0.0) return null
    val r = value / target
    return when {
        r > 1.2 -> over
        r < 0.8 -> under
        else -> null
    }
}

/** A word for the 5-10 bound: a token carrying at least one letter or digit, so a lone dash is not one. */
private fun wordCount(text: String): Int =
    text.trim().split(Regex("\\s+")).count { token -> token.any(Char::isLetterOrDigit) }

private val ACCEPTED_WORD_RANGE = 5..10

/**
 * The reworded tips, or [ruleTips] unchanged.
 *
 * Wholesale, never per tip (D3): the reply is accepted only if it has the same number of entries
 * and every entry is 5-10 words. A mixed strip would read as an inconsistent voice mid-rotation,
 * and one malformed prompt reply usually means all entries are malformed the same way.
 */
fun validateRewordedTips(ruleTips: List<DayScoreTip>, replyTips: List<String>): List<DayScoreTip> {
    if (replyTips.size != ruleTips.size) return ruleTips
    if (replyTips.any { wordCount(it) !in ACCEPTED_WORD_RANGE }) return ruleTips
    return ruleTips.mapIndexed { index, tip -> tip.copy(text = replyTips[index].trim()) }
}
