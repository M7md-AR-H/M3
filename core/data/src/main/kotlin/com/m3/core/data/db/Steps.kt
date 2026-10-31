package com.m3.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Steps of one day (Phase 1c-4). [date] is the ISO date. */
@Entity(tableName = "step_day")
data class StepDayEntity(
    @PrimaryKey val date: String,
    val steps: Int,
)

/** The latest value of the phone's step counter; one row, so the next reading knows what's new. */
@Entity(tableName = "step_reading")
data class StepReadingEntity(
    @PrimaryKey val id: Int = SINGLE_ROW,
    val counter: Long,
    val atMillis: Long,
) {
    companion object {
        const val SINGLE_ROW = 1
    }
}

@Dao
interface StepDao {
    @Query("SELECT * FROM step_day WHERE date BETWEEN :from AND :to")
    suspend fun days(from: String, to: String): List<StepDayEntity>

    @Query("SELECT steps FROM step_day WHERE date = :date")
    fun observeDay(date: String): Flow<Int?>

    @Query("INSERT INTO step_day (date, steps) VALUES (:date, :steps) ON CONFLICT(date) DO UPDATE SET steps = steps + :steps")
    suspend fun add(date: String, steps: Int)

    @Query("SELECT * FROM step_reading WHERE id = 1")
    suspend fun reading(): StepReadingEntity?

    @Upsert
    suspend fun setReading(reading: StepReadingEntity)
}
