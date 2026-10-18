package com.m3.app.data

import com.m3.core.data.PlanOverridesRepository
import com.m3.core.data.ProfileRepository
import com.m3.core.data.StoredOverrides
import com.m3.core.data.TrainingSetupRepository
import com.m3.core.model.Profile
import com.m3.core.model.TrainingSetup
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
            PlanInputs(profile, setup, stored, generator.generate(profile, setup, forThisWeek(stored, today())))
        }.flowOn(Dispatchers.Default)

    fun observeWeek(): Flow<WeekPlan> = observeInputs().map { it.plan }

    /** A plan for hypothetical inputs (previews and proposal diffs). Call off the main thread. */
    fun preview(profile: Profile?, setup: TrainingSetup, stored: StoredOverrides = StoredOverrides(), today: LocalDate = LocalDate.now()): WeekPlan =
        generator.generate(profile, setup, forThisWeek(stored, today))

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
