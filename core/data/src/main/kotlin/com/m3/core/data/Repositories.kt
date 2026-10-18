package com.m3.core.data

import androidx.room.withTransaction
import com.m3.core.data.db.AppStateEntity
import com.m3.core.data.db.DatabaseOpener
import com.m3.core.data.db.M3Database
import com.m3.core.designsystem.config.UiConfigSnapshot
import com.m3.core.model.Profile
import com.m3.core.model.ProfileValidator
import com.m3.core.model.TrainingSetup
import com.m3.core.model.TrainingSetupValidator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Thrown when owner data fails validation; nothing is written. */
class InvalidDataException(val errors: List<Any>) : IllegalArgumentException("Invalid data: $errors")

class UiConfigRepository(
    private val opener: DatabaseOpener,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    suspend fun load(): UiConfigSnapshot? {
        val dao = opener.get().uiConfigDao()
        return Mappers.toSnapshot(dao.get(), dao.history())
    }

    suspend fun save(snapshot: UiConfigSnapshot) {
        val (config, history) = Mappers.toEntities(snapshot, clock())
        opener.get().uiConfigDao().replace(config, history)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRepository(
    private val opener: DatabaseOpener,
    private val currentYear: () -> Int,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observe(): Flow<Profile?> = database().flatMapLatest { db ->
        db.profileDao().observe().map { entity -> entity?.let(Mappers::toProfile) }
    }

    fun observeOnboardingCompleted(): Flow<Boolean> = database().flatMapLatest { db ->
        db.appStateDao().observe().map { it?.onboardingCompleted == true }
    }

    suspend fun isOnboardingCompleted(): Boolean =
        opener.get().appStateDao().get()?.onboardingCompleted == true

    suspend fun save(profile: Profile) {
        validate(profile)
        val db = opener.get()
        db.withTransaction { writeProfile(db, profile) }
    }

    /** Saves everything from onboarding and marks it done, all in one transaction. */
    suspend fun completeOnboarding(profile: Profile, setup: TrainingSetup) {
        validate(profile)
        TrainingSetupRepository.validate(setup)
        val db = opener.get()
        db.withTransaction {
            writeProfile(db, profile)
            TrainingSetupRepository.write(db, setup)
            db.appStateDao().upsert(AppStateEntity(onboardingCompleted = true, schemaVersion = APP_DATA_VERSION))
        }
    }

    private suspend fun writeProfile(db: M3Database, profile: Profile) {
        val dao = db.profileDao()
        val now = clock()
        val createdAt = dao.get()?.createdAt ?: now
        dao.upsert(Mappers.toEntity(profile, createdAt = createdAt, now = now))
    }

    private fun validate(profile: Profile) {
        val errors = ProfileValidator.validate(profile, currentYear())
        if (errors.isNotEmpty()) throw InvalidDataException(errors)
    }

    private fun database(): Flow<M3Database> = flow { emit(opener.get()) }

    private companion object {
        const val APP_DATA_VERSION = 1
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class TrainingSetupRepository(private val opener: DatabaseOpener) {
    fun observe(): Flow<TrainingSetup> = flow { emit(opener.get()) }.flatMapLatest { db ->
        val dao = db.trainingDao()
        combine(dao.observeLocations(), dao.observeEquipment(), dao.observeSchedule()) { locations, equipment, days ->
            Mappers.toSetup(Mappers.SetupRows(locations, equipment, days))
        }
    }

    suspend fun replace(setup: TrainingSetup) {
        validate(setup)
        val db = opener.get()
        db.withTransaction { write(db, setup) }
    }

    internal companion object {
        fun validate(setup: TrainingSetup) {
            val errors = TrainingSetupValidator.validate(setup)
            if (errors.isNotEmpty()) throw InvalidDataException(errors)
        }

        suspend fun write(db: M3Database, setup: TrainingSetup) {
            val rows = Mappers.toRows(setup)
            db.trainingDao().replace(rows.locations, rows.equipment, rows.schedule)
        }
    }
}
