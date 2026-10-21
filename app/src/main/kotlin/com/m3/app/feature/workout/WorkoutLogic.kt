package com.m3.app.feature.workout

import com.m3.app.feature.train.PlanText
import com.m3.core.model.Durations
import com.m3.core.model.Units
import com.m3.core.training.ExerciseUnit
import com.m3.core.training.LoadKind
import com.m3.core.training.LoggedSet
import com.m3.core.training.PlannedExercise
import com.m3.core.training.PlannedSession
import com.m3.core.training.Progression
import com.m3.core.training.WeekPlan
import java.time.LocalDate
import kotlin.math.max

/** Plain-Kotlin rules of the live workout screen: stepping, starting values, progress. No Android, easy to test. */
object WorkoutLogic {
    private const val EPSILON = 1e-6

    /** Step for loads with no plates, stack or list: one kg. */
    const val FALLBACK_STEP_KG = 1.0
    private const val SECONDS_STEP = 5

    /**
     * The next kg after pressing + ([direction] > 0) or − ([direction] < 0).
     * Listed bells move through [listed]; with no list, 1 kg steps. Never below 0; null stays null on −.
     */
    fun stepKg(current: Double?, direction: Int, kind: LoadKind, listed: List<Double>): Double? {
        val up = direction > 0
        if (kind == LoadKind.LISTED && listed.isNotEmpty()) {
            val sorted = listed.sorted()
            return if (up) {
                sorted.firstOrNull { current == null || it > current + EPSILON } ?: current
            } else {
                if (current == null) null else sorted.lastOrNull { it < current - EPSILON } ?: current
            }
        }
        if (current == null && !up) return null
        val step = when (kind) {
            LoadKind.PLATES -> Progression.PLATE_STEP_KG
            LoadKind.STACK -> Progression.STACK_STEP_KG
            LoadKind.LISTED, LoadKind.NONE -> FALLBACK_STEP_KG
        }
        val base = current ?: 0.0
        return max(0.0, if (up) base + step else base - step)
    }

    /** The next reps / seconds / minutes value; never below 0. */
    fun stepAmount(current: Int?, direction: Int, unit: ExerciseUnit): Int {
        val step = if (unit == ExerciseUnit.SECONDS) SECONDS_STEP else 1
        return max(0, (current ?: 0) + if (direction > 0) step else -step)
    }

    /** Starts at the target; without one, at last time's kg; without that, empty. */
    fun initialKg(targetKg: Double?, lastKg: Double?): Double? = targetKg ?: lastKg

    /** Starts at last time's value for that set; without one, at the top of the prescription. */
    fun initialAmount(lastAmount: Int?, max: Int): Int = lastAmount ?: max

    /** The number shown in the reps column: reps, seconds, or minutes (stored as seconds). */
    fun amountOf(reps: Int?, seconds: Int?, unit: ExerciseUnit): Int? = when (unit) {
        ExerciseUnit.REPS -> reps
        ExerciseUnit.SECONDS -> seconds
        ExerciseUnit.MINUTES -> seconds?.div(SECONDS_PER_MINUTE)
    }

    /** The (reps, seconds) pair to store for [amount]. */
    fun toStored(amount: Int?, unit: ExerciseUnit): Pair<Int?, Int?> = when (unit) {
        ExerciseUnit.REPS -> amount to null
        ExerciseUnit.SECONDS -> null to amount
        ExerciseUnit.MINUTES -> null to amount?.times(SECONDS_PER_MINUTE)
    }

    /** "20 × 12", "12", "20 × 45 s", or "—" when there is no such set. */
    fun lastText(set: LoggedSet?, unit: ExerciseUnit): String {
        val amount = set?.let { amountOf(it.reps, it.seconds, unit) } ?: return "—"
        val text = when (unit) {
            ExerciseUnit.REPS -> "$amount"
            ExerciseUnit.SECONDS -> Durations.seconds(amount)
            ExerciseUnit.MINUTES -> Durations.minutes(amount)
        }
        return set.kg?.let { "${Units.number(it)} × $text" } ?: text
    }

    /** "3 × 8–12 · 22.5 kg · rest 90 s"; no kg part when the target has none. */
    fun prescriptionLine(planned: PlannedExercise, targetKg: Double?): String {
        val rest = planned.prescription.restSeconds
        return listOfNotNull(
            PlanText.prescription(planned).substringBefore(" · rest"),
            targetKg?.let { Units.kg(it) },
            if (rest > 0) "rest ${Durations.rest(rest)}" else null,
        ).joinToString(" · ")
    }

    /**
     * Index of the first exercise whose planned sets aren't all logged; the last one when all are.
     * [planned] is (variant key, number of sets) in workout order; [logged] is the set numbers logged per variant.
     */
    fun firstIncomplete(planned: List<Pair<String, Int>>, logged: Map<String, Set<Int>>): Int {
        val index = planned.indexOfFirst { (key, sets) -> (logged[key].orEmpty().count { it in 1..sets }) < sets }
        return if (index >= 0) index else max(0, planned.lastIndex)
    }

    /** The planned session an open workout belongs to, or null if the plan has changed beyond recognition. */
    fun findSession(plan: WeekPlan, date: LocalDate, sessionType: String, locationId: String): PlannedSession? =
        plan.sessionOn(date.dayOfWeek)?.takeIf { it.type.name == sessionType }
            ?: plan.sessions.firstOrNull { it.type.name == sessionType && it.locationId == locationId }

    /** "18:42", or "1:05:09" from an hour on. Digits are built by hand so the locale can't change them. */
    fun clock(millis: Long): String {
        val total = max(0L, millis / MILLIS_PER_SECOND)
        val hours = total / SECONDS_PER_HOUR
        val minutes = (total % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
        val seconds = total % SECONDS_PER_MINUTE
        val mm = minutes.toString().padStart(2, '0')
        val ss = seconds.toString().padStart(2, '0')
        return if (hours > 0) "$hours:$mm:$ss" else "$mm:$ss"
    }

    /** Remaining rest, rounded up to whole seconds so it reads "0:00" only when over. */
    fun restRemainingMillis(endAt: Long, now: Long): Long = max(0L, endAt - now)

    fun restFraction(endAt: Long, totalMillis: Long, now: Long): Float =
        if (totalMillis <= 0) 0f else (restRemainingMillis(endAt, now).toFloat() / totalMillis).coerceIn(0f, 1f)

    /** "1:30" style countdown, rounding up. */
    fun restClock(remainingMillis: Long): String {
        val seconds = (remainingMillis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND
        return "${seconds / SECONDS_PER_MINUTE}:${(seconds % SECONDS_PER_MINUTE).toString().padStart(2, '0')}"
    }

    private const val MILLIS_PER_SECOND = 1000L
    private const val SECONDS_PER_MINUTE = 60
    private const val SECONDS_PER_HOUR = 3600
}
