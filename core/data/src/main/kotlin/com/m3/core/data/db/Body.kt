package com.m3.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One weigh-in per day (Phase 3): a second one on the same day replaces it. */
@Entity(tableName = "weight_entry")
data class WeightEntryEntity(
    @PrimaryKey val date: String,
    val kg: Double,
)

/** One value per day and body part. [part] is a BodyPart name. */
@Entity(tableName = "measurement_entry", primaryKeys = ["date", "part"])
data class MeasurementEntity(
    val date: String,
    val part: String,
    val cm: Double,
)

@Dao
interface BodyDao {
    @Query("SELECT * FROM weight_entry ORDER BY date")
    fun observeWeights(): Flow<List<WeightEntryEntity>>

    @Upsert
    suspend fun saveWeight(entry: WeightEntryEntity)

    @Query("DELETE FROM weight_entry WHERE date = :date")
    suspend fun deleteWeight(date: String)

    @Query("DELETE FROM measurement_entry WHERE date = :date")
    suspend fun deleteMeasurements(date: String)

    @Query("SELECT * FROM measurement_entry ORDER BY date")
    fun observeMeasurements(): Flow<List<MeasurementEntity>>

    @Upsert
    suspend fun saveMeasurement(entry: MeasurementEntity)
}
