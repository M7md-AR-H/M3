package com.m3.core.model

import java.time.LocalDate
import kotlin.math.roundToInt

/** One finished week of logs, Monday to Sunday. */
data class WeekLogs(
    val monday: LocalDate,
    val plannedSessions: Int,
    val workoutsDone: Int,
    val foodByDay: Map<LocalDate, DayTotals>,
    val targets: NutritionTargets?,
    val stepsByDay: Map<LocalDate, Int>,
    val weightKg: Double?,
    val weeklyChangeKg: Double?,
    val goal: Goal?,
    val checkIns: List<CheckIn>,
)

/** The week in a headline, up to four facts, and the one thing to change. */
data class WeekReview(val headline: String, val facts: List<String>, val advice: String)

/** Every number and every piece of advice here is plain Kotlin, from the logs; the model isn't involved (ADR 0003). */
object WeeklyReview {
    private const val MIN_DAYS_TO_JUDGE = 4
    private const val LOW_DAY_SHARE = 0.6
    private const val LOW_DAYS_TO_WORRY = 3
    private const val PROTEIN_SHORT_SHARE = 0.85
    private const val POOR_SLEEP = 3.0
    private const val FAST_LOSS_SHARE = 0.01
    private const val FLAT_KG = 0.1

    fun review(week: WeekLogs): WeekReview? {
        val anything = week.workoutsDone > 0 || week.foodByDay.isNotEmpty() || week.stepsByDay.isNotEmpty() ||
            week.weeklyChangeKg != null || week.checkIns.isNotEmpty()
        if (!anything) return null
        return WeekReview(headline(week), facts(week), advice(week))
    }

    private fun headline(week: WeekLogs): String = when {
        week.plannedSessions == 0 -> "Your week in numbers."
        week.workoutsDone >= week.plannedSessions -> "Every session done."
        week.workoutsDone == 0 -> "No sessions logged."
        else -> "${week.workoutsDone} of ${week.plannedSessions} sessions done."
    }

    private fun facts(week: WeekLogs): List<String> = buildList {
        if (week.foodByDay.isNotEmpty()) {
            val kcal = week.foodByDay.values.map { it.kcal }.average().roundToInt()
            val protein = week.foodByDay.values.map { it.proteinG }.average().roundToInt()
            val target = week.targets?.let { " of ${Units.count(it.kcal)}" } ?: ""
            add("Food: ${Units.count(kcal)}$target kcal and ${Units.grams(protein)} protein a day, over ${days(week.foodByDay.size)} logged")
        }
        val walked = week.stepsByDay.values.filter { it > 0 }
        if (walked.isNotEmpty()) add("Steps: ${Units.count(walked.average().roundToInt())} a day")
        week.weeklyChangeKg?.let { add("Weight trend: ${Units.signed(it)} ${Units.KG} a week") }
        if (week.checkIns.isNotEmpty()) {
            add("Sleep: ${Units.oneDecimal(week.checkIns.map { it.sleep }.average())} of 5 on average")
        }
    }

    /** The first rule that applies, most urgent first: only one thing to fix each week. */
    private fun advice(week: WeekLogs): String {
        val target = week.targets
        val logged = week.foodByDay.values
        val judgeFood = logged.size >= MIN_DAYS_TO_JUDGE
        val change = week.weeklyChangeKg
        val weight = week.weightKg

        if (target != null && logged.count { it.kcal < target.kcal * LOW_DAY_SHARE } >= LOW_DAYS_TO_WORRY) {
            return "Several days came in well under your target. If you logged everything, eat more next week. " +
                "If eating enough is hard, talk to a doctor or dietitian."
        }
        if (change != null && weight != null && change < -weight * FAST_LOSS_SHARE) {
            return "You're losing weight faster than is safe, more than 1% of your body weight a week. Eat more next week."
        }
        if (week.plannedSessions > 0 && week.workoutsDone == 0) {
            return "Get one session in this week. One is enough to start again."
        }
        if (judgeFood && target != null && logged.map { it.proteinG }.average() < target.proteinG * PROTEIN_SHORT_SHARE) {
            return "Protein is the one fix: aim for ${Units.grams(target.proteinG)} a day."
        }
        if (week.checkIns.isNotEmpty() && week.checkIns.map { it.sleep }.average() < POOR_SLEEP) {
            return "Sleep is the one fix: go to bed 30 minutes earlier this week."
        }
        if (judgeFood && change != null) {
            when {
                week.goal == Goal.LOSE_FAT && change > -FLAT_KG ->
                    return "Your weight is flat. Log every bite for a week before changing anything."
                week.goal == Goal.BUILD_MUSCLE && change < FLAT_KG ->
                    return "Your weight isn't moving up. Eat to your target every day this week."
                else -> Unit
            }
        }
        if (week.workoutsDone < week.plannedSessions) return "Protect your sessions this week: put them in your calendar."
        return "Keep going. Nothing needs changing."
    }

    private fun days(count: Int) = if (count == 1) "1 day" else "$count days"
}
