package com.m3.core.data

import androidx.room.withTransaction
import com.m3.core.data.db.DatabaseOpener
import com.m3.core.data.db.StepReadingEntity
import com.m3.core.model.StepLedger
import com.m3.core.model.StepReading
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Daily step totals (Phase 1c-4). Personal data: in the encrypted DB, never logged. */
@OptIn(ExperimentalCoroutinesApi::class)
class StepRepository(
    private val opener: DatabaseOpener,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
) {
    fun observeDay(date: LocalDate): Flow<Int> = flow { emit(opener.get()) }.flatMapLatest { db ->
        db.stepDao().observeDay(date.toString()).map { it ?: 0 }
    }

    suspend fun between(from: LocalDate, to: LocalDate): Map<LocalDate, Int> =
        opener.get().stepDao().days(from.toString(), to.toString()).associate { LocalDate.parse(it.date) to it.steps }

    /** Adds what's new since the last reading; one transaction, so a step can't be counted twice. */
    suspend fun record(reading: StepReading) {
        val db = opener.get()
        db.withTransaction {
            val dao = db.stepDao()
            val last = dao.reading()?.let { StepReading(it.counter, it.atMillis) }
            StepLedger.stepsToAdd(last, reading, zone()).forEach { (date, steps) -> dao.add(date.toString(), steps) }
            dao.setReading(StepReadingEntity(counter = reading.counter, atMillis = reading.atMillis))
        }
    }
}
