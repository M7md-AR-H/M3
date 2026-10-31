package com.m3.core.data

import com.m3.core.data.db.DatabaseOpener
import com.m3.core.data.db.FoodEntryEntity
import com.m3.core.model.FoodEntry
import com.m3.core.model.Nutrition
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** The food log (Phase 2). Personal data: in the encrypted DB, never logged. */
@OptIn(ExperimentalCoroutinesApi::class)
class FoodRepository(private val opener: DatabaseOpener) {
    fun observeDay(date: LocalDate): Flow<List<FoodEntry>> = flow { emit(opener.get()) }.flatMapLatest { db ->
        db.foodDao().observeDay(date.toString()).map { rows -> rows.map { it.toModel() } }
    }

    /** Saves a new entry, or puts a removed one back with its old id. Invalid entries write nothing. */
    suspend fun save(entry: FoodEntry) {
        val errors = Nutrition.validate(entry)
        if (errors.isNotEmpty()) throw InvalidDataException(errors)
        opener.get().foodDao().save(entry.toEntity())
    }

    suspend fun between(from: LocalDate, to: LocalDate): List<FoodEntry> =
        opener.get().foodDao().between(from.toString(), to.toString()).map { it.toModel() }

    suspend fun delete(id: Long) = opener.get().foodDao().delete(id)

    private fun FoodEntryEntity.toModel() =
        FoodEntry(id, LocalDate.parse(date), atMillis, name, kcal, proteinG, carbsG, fatG)

    private fun FoodEntry.toEntity() =
        FoodEntryEntity(id, date.toString(), atMillis, name.trim(), kcal, proteinG, carbsG, fatG)
}
