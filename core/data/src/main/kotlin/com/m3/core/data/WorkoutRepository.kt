package com.m3.core.data

import androidx.room.withTransaction
import com.m3.core.data.db.DatabaseOpener
import com.m3.core.data.db.WorkoutSessionEntity
import com.m3.core.data.db.WorkoutSetEntity
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

data class StoredWorkout(
    val id: Long,
    val date: LocalDate,
    val sessionType: String,
    val locationId: String,
    val locationName: String,
    val startedAt: Long,
    val endedAt: Long?,
)

data class StoredSet(
    val sessionId: Long,
    val exerciseId: String,
    val variantKey: String,
    val setNumber: Int,
    val kg: Double?,
    val reps: Int?,
    val seconds: Int?,
    val rpe: Double?,
    val completedAt: Long,
)

/** A finished workout with its sets, newest first in lists. */
data class WorkoutWithSets(val workout: StoredWorkout, val sets: List<StoredSet>)

/**
 * Workouts and their sets (Phase 1c-3). Personal data: in the encrypted DB, never logged.
 * Each set is saved the moment it's ticked, so a workout survives the app being closed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutRepository(
    private val opener: DatabaseOpener,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun observeOpen(): Flow<StoredWorkout?> = flow { emit(opener.get()) }.flatMapLatest { db ->
        db.workoutDao().observeOpen().map { it?.toStored() }
    }

    fun observeSets(sessionId: Long): Flow<List<StoredSet>> = flow { emit(opener.get()) }.flatMapLatest { db ->
        db.workoutDao().observeSets(sessionId).map { rows -> rows.map { it.toStored() } }
    }

    /**
     * The open workout if there is one, else a new one. Only one workout is open at a time: starting
     * another day's session first finishes the open one (or drops it if nothing was logged).
     */
    suspend fun startOrResume(date: LocalDate, sessionType: String, locationId: String, locationName: String): StoredWorkout {
        val db = opener.get()
        return db.withTransaction {
            val dao = db.workoutDao()
            val open = dao.open()
            if (open != null && open.date == date.toString() && open.sessionType == sessionType) return@withTransaction open.toStored()
            // Left open on an earlier day: it ended with its last set, not now.
            if (open != null) closeOrDrop(open.id, endedAt = dao.lastSetAt(open.id))
            val entity = WorkoutSessionEntity(
                date = date.toString(), sessionType = sessionType, locationId = locationId, locationName = locationName, startedAt = clock(),
            )
            entity.copy(id = dao.insertSession(entity)).toStored()
        }
    }

    /**
     * Saves a finished set, replacing an earlier entry for the same set. One transaction, so two saves of the same
     * set can't both insert. An edit keeps the time the set was first done.
     */
    suspend fun logSet(set: StoredSet) {
        val db = opener.get()
        db.withTransaction {
            val dao = db.workoutDao()
            val existing = dao.set(set.sessionId, set.variantKey, set.setNumber)
            dao.upsertSet(
                WorkoutSetEntity(
                    id = existing?.id ?: 0, sessionId = set.sessionId, exerciseId = set.exerciseId, variantKey = set.variantKey,
                    setNumber = set.setNumber, kg = set.kg, reps = set.reps, seconds = set.seconds, rpe = set.rpe,
                    completedAt = existing?.completedAt ?: clock(),
                ),
            )
        }
    }

    suspend fun removeSet(sessionId: Long, variantKey: String, setNumber: Int) =
        opener.get().workoutDao().deleteSet(sessionId, variantKey, setNumber)

    /** Ends the workout. One with no sets logged is dropped instead of kept empty. */
    suspend fun finish(sessionId: Long) {
        val db = opener.get()
        db.withTransaction { closeOrDrop(sessionId) }
    }

    private suspend fun closeOrDrop(sessionId: Long, endedAt: Long? = null) {
        val dao = opener.get().workoutDao()
        if (dao.setCount(sessionId) == 0) dao.deleteSession(sessionId) else dao.finish(sessionId, endedAt ?: clock())
    }

    /** The last [sessions] finished workouts with their sets, newest first. */
    fun observeRecent(sessions: Int = RECENT_SESSIONS): Flow<List<WorkoutWithSets>> = flow { emit(opener.get()) }.flatMapLatest { db ->
        val dao = db.workoutDao()
        dao.observeRecentFinished(sessions).flatMapLatest { workouts ->
            dao.observeRecentFinishedSets(sessions).map { sets ->
                val bySession = sets.groupBy { it.sessionId }
                workouts.map { w -> WorkoutWithSets(w.toStored(), bySession[w.id].orEmpty().map { it.toStored() }) }
            }
        }
    }

    /** The dates of finished workouts between two dates, both included (a day with two workouts appears twice). */
    suspend fun datesBetween(from: LocalDate, to: LocalDate): List<LocalDate> =
        opener.get().workoutDao().finishedDates(from.toString(), to.toString()).map(LocalDate::parse)

    private fun WorkoutSessionEntity.toStored() =
        StoredWorkout(id, LocalDate.parse(date), sessionType, locationId, locationName, startedAt, endedAt)

    private fun WorkoutSetEntity.toStored() =
        StoredSet(sessionId, exerciseId, variantKey, setNumber, kg, reps, seconds, rpe, completedAt)

    companion object {
        /** About three months of training: enough for progression and the Coach's summary. */
        const val RECENT_SESSIONS = 40
    }
}
