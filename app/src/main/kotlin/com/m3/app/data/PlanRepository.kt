package com.m3.app.data

import com.m3.core.data.ProfileRepository
import com.m3.core.data.TrainingSetupRepository
import com.m3.core.model.Profile
import com.m3.core.model.TrainingSetup
import com.m3.core.training.ExerciseCatalog
import com.m3.core.training.PlanGenerator
import com.m3.core.training.WeekPlan
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn

/**
 * The week the Coach plans from the owner's profile and availability (ADR 0009). Recomputed whenever
 * either changes; it's a pure function, so nothing extra is stored yet.
 */
@Singleton
class PlanRepository @Inject constructor(
    private val profiles: ProfileRepository,
    private val setups: TrainingSetupRepository,
) {
    private val generator by lazy { PlanGenerator(ExerciseCatalog.bundled()) }

    fun observeWeek(): Flow<WeekPlan> =
        combine(profiles.observe(), setups.observe()) { profile, setup -> generator.generate(profile, setup) }
            .flowOn(Dispatchers.Default)

    /** For previews before anything is saved, e.g. the onboarding summary. Call off the main thread. */
    fun preview(profile: Profile?, setup: TrainingSetup): WeekPlan = generator.generate(profile, setup)
}
