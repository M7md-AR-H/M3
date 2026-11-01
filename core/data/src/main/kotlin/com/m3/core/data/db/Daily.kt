package com.m3.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT ml FROM water_day WHERE date = :date")
    fun observeWater(date: String): Flow<Int?>

    /** One statement, so taps in the app and on a notification can't lose each other's amounts. */
    @Query(
        "INSERT INTO water_day (date, ml) VALUES (:date, MAX(0, MIN(:ml, :max))) " +
            "ON CONFLICT(date) DO UPDATE SET ml = MAX(0, MIN(ml + :ml, :max))",
    )
    suspend fun addWater(date: String, ml: Int, max: Int)

    /** Ticks the habit on reaching the goal and unticks it on dropping below; a tick by hand otherwise stays. */
    @Transaction
    suspend fun addWaterAndMarkGoal(date: String, ml: Int, max: Int, goal: Int, habit: String) {
        val before = waterOn(date) ?: 0
        addWater(date, ml, max)
        val after = waterOn(date) ?: 0
        if (before < goal && after >= goal) markDone(HabitDoneEntity(date, habit))
        if (before >= goal && after < goal) markNotDone(date, habit)
    }

    @Query("SELECT ml FROM water_day WHERE date = :date")
    suspend fun waterOn(date: String): Int?
}
