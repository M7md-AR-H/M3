package com.m3.app.data

import com.m3.core.data.PlanOverridesRepository
import com.m3.core.data.ProfileRepository
import com.m3.core.data.StoredOverrides
import com.m3.core.data.TrainingSetupRepository
import com.m3.core.model.Profile
import com.m3.core.model.TrainingSetup
import com.m3.core.training.Deload
import com.m3.core.training.ExerciseCatalog
import com.m3.core.training.PlanGenerator
import com.m3.core.training.PlanOverrides
import com.m3.core.training.WeekPlan
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Everything the week depends on, read together so the plan and the Coach always see the same state. */
data class PlanInputs(
    val profile: Profile?,
    val setup: TrainingSetup,
    val stored: StoredOverrides,
    val plan: WeekPlan,
    /** True when this week is an accepted deload week; [plan] already has the lighter sets. */
    val deloadThisWeek: Boolean = false,
    /** Mondays of every accepted deload week (progression skips workouts done in them). */
    val deloadWeeks: Set<LocalDate> = emptySet(),
)

/**
 * The week the Coach plans from the owner's profile and availability (ADR 0009), with the Coach's applied
 * changes on top (ADR 0010). Recomputed whenever any of them change.
 */
@Singleton
class PlanRepository @Inject constructor(
    private val profiles: ProfileRepository,
    private val setups: TrainingSetupRepository,
    private val overrides: PlanOverridesRepository,
) {
    val catalog: ExerciseCatalog by lazy { ExerciseCatalog.bundled() }
    private val generator by lazy { PlanGenerator(catalog) }

    fun observeInputs(today: () -> LocalDate = LocalDate::now): Flow<PlanInputs> =
        combine(profiles.observe(), setups.observe(), overrides.observe()) { profile, setup, stored ->
            val now = today()
            PlanInputs(
                profile, setup, stored, weekPlan(profile, setup, stored, now),
                deloadThisWeek = Deload.mondayOf(now) in stored.deloadWeeks, deloadWeeks = stored.deloadWeeks,
            )
        }.flowOn(Dispatchers.Default)

    fun observeWeek(): Flow<WeekPlan> = observeInputs().map { it.plan }

    /** A plan for hypothetical inputs (previews and proposal diffs). Call off the main thread. */
    fun preview(profile: Profile?, setup: TrainingSetup, stored: StoredOverrides = StoredOverrides(), today: LocalDate = LocalDate.now()): WeekPlan =
        weekPlan(profile, setup, stored, today)

    /** The generated week, with the lighter sets when that week's Monday is an accepted deload. */
    private fun weekPlan(profile: Profile?, setup: TrainingSetup, stored: StoredOverrides, today: LocalDate): WeekPlan {
        val plan = generator.generate(profile, setup, forThisWeek(stored, today))
        return if (Deload.mondayOf(today) in stored.deloadWeeks) Deload.apply(plan) else plan
    }

    companion object {
        /** Day overrides count only for dates in the current Monday–Sunday week, from today on. */
        fun forThisWeek(stored: StoredOverrides, today: LocalDate): PlanOverrides {
            val sunday = today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
            val days = stored.dayEquipment
                .filterKeys { !it.isBefore(today) && !it.isAfter(sunday) }
                .mapKeys { it.key.dayOfWeek }
            return PlanOverrides(dayInventory = days, excludedExercises = stored.excluded.keys)
        }
    }
}
