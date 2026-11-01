package com.m3.app.feature.reminders

import com.m3.app.data.PlanRepository
import com.m3.app.feature.train.PlanText
import com.m3.core.data.BodyRepository
import com.m3.core.data.DailyRepository
import com.m3.core.data.FoodRepository
import com.m3.core.data.ReminderRepository
import com.m3.core.data.WorkoutRepository
import com.m3.core.model.DayFacts
import com.m3.core.model.Hydration
import com.m3.core.model.Nutrition
import com.m3.core.model.ReminderKind
import com.m3.core.model.ReminderRules
import com.m3.core.model.ReminderSettings
import com.m3.core.model.Reminders
import com.m3.core.model.SessionFacts
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Set by MainActivity; a rest alarm stays quiet while the timer is on screen. */
@Singleton
class AppVisibility @Inject constructor() {
    @Volatile
    var visible: Boolean = false
}

/**
 * Keeps one alarm set for the next due reminder, and decides at that moment whether it's still worth sending:
 * the logs are read then, not when it was scheduled. Runs without M³ being unlocked; the database key isn't tied
 * to the app lock (see README, known limitations). Personal data: never logged.
 */
@Singleton
class ReminderEngine @Inject constructor(
    private val reminders: ReminderRepository,
    private val plans: PlanRepository,
    private val workouts: WorkoutRepository,
    private val food: FoodRepository,
    private val body: BodyRepository,
    private val daily: DailyRepository,
    private val notifier: ReminderNotifier,
    private val alarms: ReminderAlarms,
    private val visibility: AppVisibility,
) {
    private val running = Mutex()

    /** After a boot, an update, a clock change, a settings change, and on every start: a force-stop clears alarms. */
    suspend fun reschedule() = running.withLock { guarded { scheduleNext(reminders.observe().first()) } }

    suspend fun onDue(due: LocalDateTime) = running.withLock {
        guarded {
            val settings = reminders.observe().first()
            val late = Duration.between(due, LocalDateTime.now())
            if (late <= MAX_LATE) sendDue(due, settings)
            scheduleNext(settings)
        }
    }

    suspend fun onRestOver() {
        if (visibility.visible) return
        val settings = reminders.observe().first()
        if (settings[ReminderKind.REST].on) notifier.post(ReminderRules.rest)
    }

    suspend fun addGlass() {
        daily.addWater(LocalDate.now(), Hydration.GLASS_ML)
        notifier.cancel(ReminderKind.WATER)
    }

    /** A failure (say, storage not ready just after boot) must not end the chain of alarms. */
    private suspend fun guarded(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No logging: the exception text could include file paths or data.
            if (notifier.canPost()) alarms.setNext(LocalDateTime.now().plus(RETRY_AFTER))
        }
    }

    private fun scheduleNext(settings: ReminderSettings) {
        val next = Reminders.next(settings, LocalDateTime.now())
        if (next == null || !notifier.canPost()) alarms.cancelNext() else alarms.setNext(next.at)
    }

    private suspend fun sendDue(due: LocalDateTime, settings: ReminderSettings) {
        // The kinds are worked out again from the settings: a retry alarm matches none and sends nothing.
        val kinds = Reminders.next(settings, due.minusMinutes(1))?.takeIf { it.at == due }?.kinds ?: return
        val day = dayFacts() ?: return
        kinds.forEach { kind -> ReminderRules.message(kind, day, settings.showDetails)?.let(notifier::post) }
    }

    /** Null before onboarding: there's nothing to remind about yet. */
    private suspend fun dayFacts(): DayFacts? {
        val inputs = plans.observeInputs().first()
        val profile = inputs.profile ?: return null
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val entries = food.observeDay(today).first()
        val recent = workouts.observeRecent().first()
        val session = inputs.plan.sessionOn(today.dayOfWeek)?.takeIf { it.exercises.isNotEmpty() }
        val zone = ZoneId.systemDefault()
        return DayFacts(
            now = now,
            session = session?.let { SessionFacts(it.type.displayName, it.locationName, it.exercises.size, PlanText.estimatedMinutes(it)) },
            workoutDone = recent.any { it.workout.date == today },
            workoutOpen = workouts.observeOpen().first()?.date == today,
            checkIn = daily.observeCheckIn(today).first(),
            weighedIn = body.observeWeights().first().any { it.date == today },
            foodEntries = entries.size,
            lastFoodAt = entries.maxOfOrNull { it.atMillis }?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone) },
            eaten = Nutrition.totals(entries),
            targets = Nutrition.targets(profile, today.year, inputs.plan.sessions.size),
            waterMl = daily.observeWater(today).first(),
            habitsDone = daily.observeHabits(today).first(),
            lastWeekHasLogs = lastWeekHasLogs(today, recent.map { it.workout.date }),
        )
    }

    private suspend fun lastWeekHasLogs(today: LocalDate, workoutDates: List<LocalDate>): Boolean {
        val monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(1)
        val sunday = monday.plusDays(6)
        return workoutDates.any { it in monday..sunday } || food.between(monday, sunday).isNotEmpty()
    }

    private companion object {
        /** A lunch reminder at three in the afternoon helps nobody: the phone was off, so skip it. */
        val MAX_LATE: Duration = Duration.ofMinutes(45)
        val RETRY_AFTER: Duration = Duration.ofMinutes(30)
    }
}
