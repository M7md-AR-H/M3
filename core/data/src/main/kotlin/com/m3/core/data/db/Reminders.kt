package com.m3.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One row per reminder the owner changed; no row means the defaults. [kind] is a ReminderKind name. */
@Entity(tableName = "reminder_setting")
data class ReminderSettingEntity(
    @PrimaryKey val kind: String,
    val isOn: Boolean,
    val minute: Int,
    val everyHours: Int,
)

/** Settings for all reminders together. No row means the defaults. */
@Entity(tableName = "reminder_prefs")
data class ReminderPrefsEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val showDetails: Boolean,
)

/** Water drunk on a day. */
@Entity(tableName = "water_day")
data class WaterDayEntity(
    @PrimaryKey val date: String,
    val ml: Int,
)

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminder_setting")
    fun observeSettings(): Flow<List<ReminderSettingEntity>>

    @Upsert
    suspend fun saveSetting(setting: ReminderSettingEntity)

    @Query("SELECT * FROM reminder_prefs WHERE id = 1")
    fun observePrefs(): Flow<ReminderPrefsEntity?>

    @Upsert
    suspend fun savePrefs(prefs: ReminderPrefsEntity)
}
