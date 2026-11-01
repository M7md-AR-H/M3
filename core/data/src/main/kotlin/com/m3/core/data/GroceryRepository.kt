package com.m3.core.data

import androidx.room.withTransaction
import com.m3.core.data.db.DatabaseOpener
import com.m3.core.data.db.GroceryItemEntity
import com.m3.core.model.Grocery
import com.m3.core.model.GroceryLine
import com.m3.core.model.GrocerySection
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** SUGGESTED: made from the owner's targets. OWNER: typed in by the owner. Stored by name. */
enum class GrocerySource { SUGGESTED, OWNER }

data class StoredGroceryItem(
    val id: Long,
    val name: String,
    val amount: String,
    val section: GrocerySection,
    val checked: Boolean,
    val source: GrocerySource,
)

/** The grocery list (Phase 2). Personal data: in the encrypted DB, never logged. Every write is one transaction. */
@OptIn(ExperimentalCoroutinesApi::class)
class GroceryRepository(private val opener: DatabaseOpener) {
    /** Items on the list (removed suggestions are kept out of sight), in the order they were added. */
    fun observe(): Flow<List<StoredGroceryItem>> = flow { emit(opener.get()) }.flatMapLatest { db ->
        db.groceryDao().observeVisible().map { rows -> rows.map { it.toModel() } }
    }

    /** True when nothing has ever been on the list, not even a removed suggestion. */
    suspend fun isEmpty(): Boolean = opener.get().groceryDao().count() == 0

    /**
     * Replaces the suggestions that are still open (not ticked, not removed) by [lines]. Ticked suggestions and the
     * owner's own items stay. A line the owner removed before, or one already on the list, isn't added again.
     */
    suspend fun refresh(lines: List<GroceryLine>) {
        val db = opener.get()
        db.withTransaction {
            val dao = db.groceryDao()
            dao.deleteOpenSuggestions()
            val taken = dao.all().map { key(it.name) }.toMutableSet()
            lines.forEach { line ->
                if (!taken.add(key(line.name))) return@forEach
                dao.insert(
                    GroceryItemEntity(
                        name = line.name.trim(), amount = line.amount, section = line.section.name, checked = false,
                        source = GrocerySource.SUGGESTED.name, removed = false,
                    ),
                )
            }
        }
    }

    /** Adds an item of the owner's. Returns false (and writes nothing) for an invalid name or one already on the list. */
    suspend fun add(name: String, section: GrocerySection = GrocerySection.YOURS): Boolean {
        if (Grocery.validateName(name) != null) return false
        val db = opener.get()
        return db.withTransaction {
            val dao = db.groceryDao()
            if (dao.all().any { !it.removed && key(it.name) == key(name) }) return@withTransaction false
            // An item the owner removed earlier and now types again is theirs from here on.
            dao.all().filter { it.removed && key(it.name) == key(name) }.forEach { dao.delete(it.id) }
            dao.insert(
                GroceryItemEntity(
                    name = name.trim(), amount = "", section = section.name, checked = false,
                    source = GrocerySource.OWNER.name, removed = false,
                ),
            )
            true
        }
    }

    suspend fun setChecked(id: Long, checked: Boolean) = opener.get().groceryDao().setChecked(id, checked)

    /** Hides a suggestion (it stays gone after a refresh) or deletes the owner's item. Returns what to pass to [restore]. */
    suspend fun remove(id: Long): StoredGroceryItem? {
        val db = opener.get()
        return db.withTransaction {
            val dao = db.groceryDao()
            val row = dao.get(id) ?: return@withTransaction null
            if (row.source == GrocerySource.SUGGESTED.name) dao.markRemoved(id) else dao.delete(id)
            row.toModel()
        }
    }

    /** Undo of [remove]: the item comes back under its old id, ticked or not as it was. */
    suspend fun restore(item: StoredGroceryItem) {
        opener.get().groceryDao().save(
            GroceryItemEntity(item.id, item.name, item.amount, item.section.name, item.checked, item.source.name, removed = false),
        )
    }

    /** Deletes every ticked item, suggestions included (a refresh may suggest them again). */
    suspend fun clearTicked() {
        val db = opener.get()
        db.withTransaction { db.groceryDao().deleteTicked() }
    }

    private fun key(name: String) = name.trim().lowercase()

    private fun GroceryItemEntity.toModel() = StoredGroceryItem(
        id = id,
        name = name,
        amount = amount,
        section = GrocerySection.entries.firstOrNull { it.name == section } ?: GrocerySection.YOURS,
        checked = checked,
        source = GrocerySource.entries.firstOrNull { it.name == source } ?: GrocerySource.OWNER,
    )
}
