package com.m3.core.data

import com.m3.core.data.db.DatabaseOpener
import com.m3.core.data.db.MeasurementEntity
import com.m3.core.data.db.WeightEntryEntity
import com.m3.core.model.BodyLimits
import com.m3.core.model.BodyPart
import com.m3.core.model.Measurement
import com.m3.core.model.WeightEntry
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Weight and measurements (Phase 3). Personal data: in the encrypted DB, never logged. */
@OptIn(ExperimentalCoroutinesApi::class)
class BodyRepository(private val opener: DatabaseOpener) {
    fun observeWeights(): Flow<List<WeightEntry>> = flow { emit(opener.get()) }.flatMapLatest { db ->
        db.bodyDao().observeWeights().map { rows -> rows.map { WeightEntry(LocalDate.parse(it.date), it.kg) } }
    }

    fun observeMeasurements(): Flow<List<Measurement>> = flow { emit(opener.get()) }.flatMapLatest { db ->
        db.bodyDao().observeMeasurements().map { rows ->
            // A part from a newer build is skipped, as with other stored enums.
            rows.mapNotNull { row ->
                BodyPart.entries.firstOrNull { it.name == row.part }?.let { Measurement(LocalDate.parse(row.date), it, row.cm) }
            }
        }
    }

    suspend fun saveWeight(entry: WeightEntry) {
        if (!BodyLimits.validWeight(entry.kg)) throw InvalidDataException(listOf(entry.kg))
        opener.get().bodyDao().saveWeight(WeightEntryEntity(entry.date.toString(), entry.kg))
    }

    suspend fun deleteWeight(date: LocalDate) = opener.get().bodyDao().deleteWeight(date.toString())

    suspend fun deleteMeasurements(date: LocalDate) = opener.get().bodyDao().deleteMeasurements(date.toString())

    suspend fun saveMeasurement(entry: Measurement) {
        if (!BodyLimits.validCm(entry.cm)) throw InvalidDataException(listOf(entry.cm))
        opener.get().bodyDao().saveMeasurement(MeasurementEntity(entry.date.toString(), entry.part.name, entry.cm))
    }
}
