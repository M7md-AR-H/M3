package com.m3.core.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/** What a reminder is about. [defaultMinute] is minutes after midnight; null = fires on an event, not a clock time. */
enum class ReminderKind(
    val displayName: String,
    val detail: String,
    val defaultMinute: Int?,
    val defaultOn: Boolean = true,
) {
    MORNING("Morning", "Check-in and weigh-in, if they're open", 7 * 60 + 30),
    BREAKFAST("Breakfast", "Unless you just logged food", 9 * 60),
    LUNCH("Lunch", "Unless you just logged food", 13 * 60),
    DINNER("Dinner", "Unless you just logged food", 19 * 60),
    WORKOUT("Workout", "Training days, if you haven't trained yet", 17 * 60 + 30),
    WATER("Water", "Until you reach your goal", 10 * 60 + 30),
    EVENING("Log your day", "Only if something is still open", 21 * 60),
    LIGHTS_OUT("Lights out", "If the habit is still open", 22 * 60 + 30, defaultOn = false),
    WEEK_REVIEW("Weekly review", "Mondays, after a week with logs", 9 * 60 + 30),
    REST("Rest over", "When you left the workout screen", null),
}

data class ReminderSetting(
    val kind: ReminderKind,
    val on: Boolean = kind.defaultOn,
    val minute: Int = kind.defaultMinute ?: 0,
    /** Water only: hours between glasses. */
    val everyHours: Int = Reminders.DEFAULT_WATER_HOURS,
)

data class ReminderSettings(
    val items: Map<ReminderKind, ReminderSetting> = ReminderKind.entries.associateWith { ReminderSetting(it) },
    /** Off: notifications carry no numbers or plan details, since anyone can read them on a phone left unlocked. */
    val showDetails: Boolean = false,
) {
    operator fun get(kind: ReminderKind): ReminderSetting = items[kind] ?: ReminderSetting(kind)
}

/** The next moment any reminder is due; several kinds can share a minute. */
data class ReminderDue(val at: LocalDateTime, val kinds: Set<ReminderKind>)

/** The tab a notification opens. */
enum class ReminderTarget { TODAY, TRAIN, FUEL, YOU }

/** What to show. Every word comes from here, never from the model. */
data class ReminderMessage(
    val kind: ReminderKind,
    val title: String,
    val text: String,
    val target: ReminderTarget,
    /** Show a one-tap "+250 ml". */
    val offersWater: Boolean = false,
)

/** Today's session, as a notification may describe it. */
data class SessionFacts(val title: String, val place: String, val exercises: Int, val minutes: Int)

/** The owner's day as far as the logs know it. */
data class DayFacts(
    val now: LocalDateTime,
    val session: SessionFacts?,
    val workoutDone: Boolean,
    val workoutOpen: Boolean,
    val checkIn: CheckIn?,
    val weighedIn: Boolean,
    val foodEntries: Int,
    val lastFoodAt: LocalDateTime?,
    val eaten: DayTotals,
    val targets: NutritionTargets?,
    val waterMl: Int,
    val habitsDone: Set<Habit>,
    val lastWeekHasLogs: Boolean,
)

/** Water for the day. The goal matches [Habit.WATER]. */
object Hydration {
    const val GOAL_ML = 2_500
    const val GLASS_ML = 250
    const val MAX_ML = 10_000
}

/** When reminders are due. Pure, so it can be tested across midnight and weekdays. */
object Reminders {
    const val DEFAULT_WATER_HOURS = 3
    val WATER_HOURS = 1..6
    const val MINUTE_STEP = 15
    private const val LAST_MINUTE = 24 * 60 - 1
    private const val MINUTES_PER_HOUR = 60

    /** No glass of water is suggested after this, whatever the interval: nobody wants one at midnight. */
    private const val LAST_WATER_MINUTE = 20 * MINUTES_PER_HOUR + 30

    /** Monday's review, or any other kind on every day. */
    private const val DAYS_TO_LOOK_AHEAD = 7L

    fun validMinute(minute: Int): Boolean = minute in 0..LAST_MINUTE

    fun clockText(minute: Int): String = Units.clock(java.time.LocalTime.of(minute / MINUTES_PER_HOUR, minute % MINUTES_PER_HOUR))

    /** A new clock time [steps] steps away, wrapping at midnight. */
    fun stepMinute(minute: Int, steps: Int): Int = Math.floorMod(minute + steps * MINUTE_STEP, LAST_MINUTE + 1)

    /** The minutes a reminder fires on a day, ascending. Water repeats from its first time until the evening. */
    fun times(setting: ReminderSetting): List<Int> {
        if (!setting.on || setting.kind.defaultMinute == null) return emptyList()
        if (setting.kind != ReminderKind.WATER) return listOf(setting.minute)
        val every = setting.everyHours.coerceIn(WATER_HOURS) * MINUTES_PER_HOUR
        return generateSequence(setting.minute) { it + every }.takeWhile { it <= LAST_WATER_MINUTE }.toList()
            .ifEmpty { listOf(setting.minute) }
    }

    private fun firesOn(kind: ReminderKind, date: LocalDate): Boolean =
        kind != ReminderKind.WEEK_REVIEW || date.dayOfWeek == DayOfWeek.MONDAY

    /** The first due moment strictly after [after], or null when nothing is on. */
    fun next(settings: ReminderSettings, after: LocalDateTime): ReminderDue? {
        val startOfMinute = after.truncatedTo(ChronoUnit.MINUTES)
        for (days in 0..DAYS_TO_LOOK_AHEAD) {
            val date = after.toLocalDate().plusDays(days)
            val slots = ReminderKind.entries
                .filter { firesOn(it, date) }
                .flatMap { kind -> times(settings[kind]).map { it to kind } }
                .filter { (minute, _) -> date.atStartOfDay().plusMinutes(minute.toLong()).isAfter(startOfMinute) }
            val first = slots.minOfOrNull { it.first } ?: continue
            return ReminderDue(
                at = date.atStartOfDay().plusMinutes(first.toLong()),
                kinds = slots.filter { it.first == first }.map { it.second }.toSet(),
            )
        }
        return null
    }
}

/**
 * Whether a due reminder is still worth sending, and what it says. A reminder for something already done stays
 * silent, so the count stays low enough to keep them on. With [ReminderSettings.showDetails] off, nothing personal
 * is in the text.
 */
object ReminderRules {
    /** Food logged this recently means the meal already happened. */
    private const val JUST_ATE_MINUTES = 120L

    fun message(kind: ReminderKind, day: DayFacts, showDetails: Boolean): ReminderMessage? = when (kind) {
        ReminderKind.MORNING -> morning(day, showDetails)
        ReminderKind.BREAKFAST -> meal(kind, "Breakfast time", day, showDetails)
        ReminderKind.LUNCH -> meal(kind, "Lunch time", day, showDetails)
        ReminderKind.DINNER -> meal(kind, "Dinner time", day, showDetails)
        ReminderKind.WORKOUT -> workout(day, showDetails)
        ReminderKind.WATER -> water(day, showDetails)
        ReminderKind.EVENING -> evening(day, showDetails)
        ReminderKind.LIGHTS_OUT ->
            if (Habit.LIGHTS_OUT in day.habitsDone) null
            else ReminderMessage(kind, "Lights out soon", "Wind down: screens off, tomorrow starts here.", ReminderTarget.TODAY)
        ReminderKind.WEEK_REVIEW ->
            if (day.lastWeekHasLogs) ReminderMessage(kind, "Your week is in", "Last week's review is ready.", ReminderTarget.YOU) else null
        ReminderKind.REST -> null
    }

    /** Fixed wording, so it's the same on a locked screen. Sent by the rest alarm, not by the schedule. */
    val rest = ReminderMessage(ReminderKind.REST, "Rest over", "Next set when you're ready.", ReminderTarget.TODAY)

    private fun morning(day: DayFacts, showDetails: Boolean): ReminderMessage? {
        val text = when {
            day.checkIn == null && !day.weighedIn -> "Check in and step on the scale: one minute for a better day."
            day.checkIn == null -> "Three taps for today's advice."
            !day.weighedIn -> "Step on the scale before breakfast."
            else -> return null
        }
        val session = day.session?.takeIf { showDetails && !day.workoutDone }
        val today = session?.let { " Today: ${it.title} at ${it.place}." }.orEmpty()
        return ReminderMessage(ReminderKind.MORNING, "Good morning", text + today, ReminderTarget.TODAY)
    }

    private fun meal(kind: ReminderKind, title: String, day: DayFacts, showDetails: Boolean): ReminderMessage? {
        val justAte = day.lastFoodAt?.let { ChronoUnit.MINUTES.between(it, day.now) < JUST_ATE_MINUTES } ?: false
        if (justAte) return null
        val left = day.targets?.let { (it.kcal - day.eaten.kcal) to (it.proteinG - day.eaten.proteinG) }
        val text = when {
            !showDetails -> "Eat well, then log it in Fuel."
            kind == ReminderKind.DINNER && day.foodEntries == 0 -> "Nothing logged yet today. Eat something, then log it."
            left != null && left.first > 0 && left.second > 0 ->
                "${Units.kcal(left.first)} and ${Units.grams(left.second)} protein left today."
            left != null && left.first > 0 -> "${Units.kcal(left.first)} left today, protein done."
            else -> "Log it in Fuel."
        }
        return ReminderMessage(kind, title, text, ReminderTarget.FUEL)
    }

    private fun workout(day: DayFacts, showDetails: Boolean): ReminderMessage? {
        val session = day.session ?: return null
        if (day.workoutDone || day.workoutOpen) return null
        if (!showDetails) return ReminderMessage(ReminderKind.WORKOUT, "Workout today", "Your session is ready when you are.", ReminderTarget.TRAIN)
        val easy = if (day.checkIn?.let(DailyCheck::readiness) == Readiness.TAKE_IT_EASY) " Take it easier today." else ""
        return ReminderMessage(
            ReminderKind.WORKOUT,
            "${session.title} at ${session.place}",
            "${session.exercises} exercises, about ${session.minutes} min.$easy",
            ReminderTarget.TRAIN,
        )
    }

    private fun water(day: DayFacts, showDetails: Boolean): ReminderMessage? {
        if (Habit.WATER in day.habitsDone || day.waterMl >= Hydration.GOAL_ML) return null
        val text = if (showDetails) "${Units.volume(day.waterMl)} of ${Units.volume(Hydration.GOAL_ML)} so far." else "Time for a glass."
        return ReminderMessage(ReminderKind.WATER, "Drink some water", text, ReminderTarget.TODAY, offersWater = true)
    }

    private fun evening(day: DayFacts, showDetails: Boolean): ReminderMessage? {
        val open = buildList {
            if (day.foodEntries == 0) add("Food")
            if (day.session != null && !day.workoutDone) add("Today's session")
            addAll(Habit.entries.filter { it !in day.habitsDone && it != Habit.LIGHTS_OUT }.map { it.displayName })
        }
        if (open.isEmpty()) return null
        val text = if (showDetails) "Still open: ${open.joinToString(", ")}." else "A few things are still open."
        val target = if (day.foodEntries == 0) ReminderTarget.FUEL else ReminderTarget.TODAY
        return ReminderMessage(ReminderKind.EVENING, "Before the day ends", text, target)
    }
}
