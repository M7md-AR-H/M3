package com.m3.core.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface UiConfigDao {
    @Query("SELECT * FROM ui_config WHERE id = 1")
    suspend fun get(): UiConfigEntity?

    @Query("SELECT * FROM ui_config_history ORDER BY id")
    suspend fun history(): List<UiConfigHistoryEntity>

    @Upsert
    suspend fun upsert(config: UiConfigEntity)

    @Query("DELETE FROM ui_config_history")
    suspend fun clearHistory()

    @Insert
    suspend fun insertHistory(entries: List<UiConfigHistoryEntity>)

    /** Writes the whole snapshot; history is small (≤ 100 rows), so rewriting it keeps this simple and exact. */
    @Transaction
    suspend fun replace(config: UiConfigEntity, history: List<UiConfigHistoryEntity>) {
        upsert(config)
        clearHistory()
        insertHistory(history)
    }
}

@Dao
interface ProfileDao {
    @Query("SELECT * FROM profile WHERE id = 1")
    fun observe(): Flow<ProfileEntity?>

    @Query("SELECT * FROM profile WHERE id = 1")
    suspend fun get(): ProfileEntity?

    @Upsert
    suspend fun upsert(profile: ProfileEntity)
}

@Dao
interface TrainingDao {
    @Query("SELECT * FROM training_location ORDER BY sortOrder")
    fun observeLocations(): Flow<List<TrainingLocationEntity>>

    @Query("SELECT * FROM location_equipment ORDER BY locationId, sortOrder")
    fun observeEquipment(): Flow<List<LocationEquipmentEntity>>

    @Query("SELECT * FROM schedule_day ORDER BY dayOfWeek")
    fun observeSchedule(): Flow<List<ScheduleDayEntity>>

    @Query("DELETE FROM schedule_day")
    suspend fun clearSchedule()

    @Query("DELETE FROM location_equipment")
    suspend fun clearEquipment()

    @Query("DELETE FROM training_location")
    suspend fun clearLocations()

    @Insert
    suspend fun insertLocations(locations: List<TrainingLocationEntity>)

    @Insert
    suspend fun insertEquipment(equipment: List<LocationEquipmentEntity>)

    @Insert
    suspend fun insertSchedule(days: List<ScheduleDayEntity>)

    /** Replaces the whole training setup. Children are deleted first, so it works whether or not FKs are enforced. */
    @Transaction
    suspend fun replace(
        locations: List<TrainingLocationEntity>,
        equipment: List<LocationEquipmentEntity>,
        schedule: List<ScheduleDayEntity>,
    ) {
        clearSchedule()
        clearEquipment()
        clearLocations()
        insertLocations(locations)
        insertEquipment(equipment)
        insertSchedule(schedule)
    }
}

@Dao
interface AppStateDao {
    @Query("SELECT * FROM app_state WHERE id = 1")
    fun observe(): Flow<AppStateEntity?>

    @Query("SELECT * FROM app_state WHERE id = 1")
    suspend fun get(): AppStateEntity?

    @Upsert
    suspend fun upsert(state: AppStateEntity)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM (SELECT * FROM chat_message ORDER BY id DESC LIMIT :limit) ORDER BY id")
    fun observeRecent(limit: Int): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_message WHERE id = :id")
    suspend fun get(id: Long): ChatMessageEntity?

    @Insert
    suspend fun insert(message: ChatMessageEntity): Long

    @androidx.room.Update
    suspend fun update(message: ChatMessageEntity)

    @Query("SELECT id FROM chat_message WHERE proposalStatus = 'APPLIED' ORDER BY id DESC LIMIT 1")
    suspend fun latestAppliedId(): Long?

    @Query("DELETE FROM chat_message")
    suspend fun clear()
}

@Dao
interface PlanOverridesDao {
    @Query("SELECT * FROM day_override ORDER BY date")
    fun observeDayOverrides(): Flow<List<DayOverrideEntity>>

    @Query("SELECT * FROM day_override WHERE date = :date")
    suspend fun dayOverride(date: String): DayOverrideEntity?

    @Query("DELETE FROM day_override WHERE date = :date")
    suspend fun deleteDayOverride(date: String)

    @Query("DELETE FROM day_override WHERE date < :date")
    suspend fun deleteDayOverridesBefore(date: String)

    @Insert
    suspend fun insertDayOverride(entity: DayOverrideEntity)

    @Transaction
    suspend fun replaceDayOverride(entity: DayOverrideEntity) {
        deleteDayOverride(entity.date)
        insertDayOverride(entity)
    }

    @Query("SELECT * FROM excluded_exercise ORDER BY createdAt")
    fun observeExcluded(): Flow<List<ExcludedExerciseEntity>>

    @Query("SELECT * FROM excluded_exercise WHERE exerciseId = :id")
    suspend fun excluded(id: String): ExcludedExerciseEntity?

    @Upsert
    suspend fun upsertExcluded(entity: ExcludedExerciseEntity)

    @Query("DELETE FROM excluded_exercise WHERE exerciseId = :id")
    suspend fun deleteExcluded(id: String)
}

@Dao
interface WorkoutDao {
    @Insert
    suspend fun insertSession(session: WorkoutSessionEntity): Long

    @Query("SELECT * FROM workout_session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    fun observeOpen(): Flow<WorkoutSessionEntity?>

    @Query("SELECT * FROM workout_session WHERE endedAt IS NULL ORDER BY startedAt DESC LIMIT 1")
    suspend fun open(): WorkoutSessionEntity?

    @Query("SELECT * FROM workout_session WHERE id = :id")
    suspend fun session(id: Long): WorkoutSessionEntity?

    @Query("UPDATE workout_session SET endedAt = :endedAt WHERE id = :id")
    suspend fun finish(id: Long, endedAt: Long)

    @Query("DELETE FROM workout_session WHERE id = :id")
    suspend fun deleteSession(id: Long)

    @Query("SELECT * FROM workout_set WHERE sessionId = :sessionId ORDER BY completedAt")
    fun observeSets(sessionId: Long): Flow<List<WorkoutSetEntity>>

    @Query("SELECT COUNT(*) FROM workout_set WHERE sessionId = :sessionId")
    suspend fun setCount(sessionId: Long): Int

    @Query("SELECT MAX(completedAt) FROM workout_set WHERE sessionId = :sessionId")
    suspend fun lastSetAt(sessionId: Long): Long?

    @Upsert
    suspend fun upsertSet(set: WorkoutSetEntity)

    @Query("SELECT * FROM workout_set WHERE sessionId = :sessionId AND variantKey = :variantKey AND setNumber = :setNumber")
    suspend fun set(sessionId: Long, variantKey: String, setNumber: Int): WorkoutSetEntity?

    @Query("DELETE FROM workout_set WHERE sessionId = :sessionId AND variantKey = :variantKey AND setNumber = :setNumber")
    suspend fun deleteSet(sessionId: Long, variantKey: String, setNumber: Int)

    /** Sets of finished workouts only, newest workout first: what progression and the Coach read. */
    @Query(
        "SELECT s.* FROM workout_set s JOIN workout_session w ON w.id = s.sessionId " +
            "WHERE w.endedAt IS NOT NULL AND w.id IN (SELECT id FROM workout_session WHERE endedAt IS NOT NULL ORDER BY startedAt DESC LIMIT :sessions) " +
            "ORDER BY w.startedAt DESC, s.setNumber",
    )
    fun observeRecentFinishedSets(sessions: Int): Flow<List<WorkoutSetEntity>>

    @Query("SELECT * FROM workout_session WHERE endedAt IS NOT NULL ORDER BY startedAt DESC LIMIT :limit")
    fun observeRecentFinished(limit: Int): Flow<List<WorkoutSessionEntity>>
}
