package com.m3.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One logged food (Phase 2). [date] is the ISO date it counts towards. */
@Entity(tableName = "food_entry", indices = [Index("date")])
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val atMillis: Long,
    val name: String,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

@Dao
interface FoodDao {
    @Query("SELECT * FROM food_entry WHERE date = :date ORDER BY atMillis, id")
    fun observeDay(date: String): Flow<List<FoodEntryEntity>>

    /** Keeps the id when Undo puts a removed entry back. */
    @Upsert
    suspend fun save(entry: FoodEntryEntity)

    @Query("DELETE FROM food_entry WHERE id = :id")
    suspend fun delete(id: Long)
}
