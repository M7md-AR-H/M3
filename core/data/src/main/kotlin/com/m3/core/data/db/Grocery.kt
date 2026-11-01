package com.m3.core.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One line of the grocery list. [section] is a GrocerySection name, [source] a GrocerySource name. */
@Entity(tableName = "grocery_item")
data class GroceryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: String,
    val section: String,
    val checked: Boolean,
    val source: String,
    /** A removed suggestion stays in the table so a refresh doesn't bring it back. */
    val removed: Boolean,
)

@Dao
interface GroceryDao {
    @Query("SELECT * FROM grocery_item WHERE removed = 0 ORDER BY id")
    fun observeVisible(): Flow<List<GroceryItemEntity>>

    @Query("SELECT * FROM grocery_item")
    suspend fun all(): List<GroceryItemEntity>

    @Query("SELECT COUNT(*) FROM grocery_item")
    suspend fun count(): Int

    @Insert
    suspend fun insert(entity: GroceryItemEntity): Long

    /** Puts a removed row back with its old id. */
    @Upsert
    suspend fun save(entity: GroceryItemEntity)

    @Query("UPDATE grocery_item SET checked = :checked WHERE id = :id")
    suspend fun setChecked(id: Long, checked: Boolean)

    @Query("SELECT * FROM grocery_item WHERE id = :id")
    suspend fun get(id: Long): GroceryItemEntity?

    @Query("UPDATE grocery_item SET removed = 1 WHERE id = :id")
    suspend fun markRemoved(id: Long)

    @Query("DELETE FROM grocery_item WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM grocery_item WHERE source = 'SUGGESTED' AND checked = 0 AND removed = 0")
    suspend fun deleteOpenSuggestions()

    @Query("DELETE FROM grocery_item WHERE checked = 1 AND removed = 0")
    suspend fun deleteTicked()
}
