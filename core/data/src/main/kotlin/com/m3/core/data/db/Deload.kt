package com.m3.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** The owner's answer to a deload suggestion for one week. [monday] is the ISO date; [state] is a DeloadState name. */
@Entity(tableName = "deload_week")
data class DeloadWeekEntity(
    @PrimaryKey val monday: String,
    val state: String,
)

@Dao
interface DeloadDao {
    @Query("SELECT * FROM deload_week")
    fun observeAll(): Flow<List<DeloadWeekEntity>>

    @Upsert
    suspend fun upsert(entity: DeloadWeekEntity)

    @Query("DELETE FROM deload_week WHERE monday = :monday")
    suspend fun delete(monday: String)
}
