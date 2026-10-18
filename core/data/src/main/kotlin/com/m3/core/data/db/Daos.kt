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
