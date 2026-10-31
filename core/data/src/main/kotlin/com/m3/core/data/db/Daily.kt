package com.m3.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One morning check-in per day. */
@Entity(tableName = "check_in")
data class CheckInEntity(
    @PrimaryKey val date: String,
    val sleep: Int,
    val energy: Int,
    val soreness: Int,
)

/** A habit done on a day; no row means not done. [habit] is a Habit name. */
@Entity(tableName = "habit_done", primaryKeys = ["date", "habit"])
data class HabitDoneEntity(
    val date: String,
    val habit: String,
)

@Dao
interface DailyDao {
    @Query("SELECT * FROM check_in WHERE date BETWEEN :from AND :to")
    suspend fun checkInsBetween(from: String, to: String): List<CheckInEntity>

    @Query("SELECT * FROM check_in WHERE date = :date")
    fun observeCheckIn(date: String): Flow<CheckInEntity?>

    @Upsert
    suspend fun saveCheckIn(entry: CheckInEntity)

    @Query("SELECT habit FROM habit_done WHERE date = :date")
    fun observeHabits(date: String): Flow<List<String>>

    @Upsert
    suspend fun markDone(entry: HabitDoneEntity)

    @Query("DELETE FROM habit_done WHERE date = :date AND habit = :habit")
    suspend fun markNotDone(date: String, habit: String)
}
