package com.m3.core.data

import com.m3.core.data.db.CheckInEntity
import com.m3.core.data.db.DatabaseOpener
import com.m3.core.data.db.HabitDoneEntity
import com.m3.core.model.CheckIn
import com.m3.core.model.DailyCheck
import com.m3.core.model.Habit
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Morning check-ins and habits. Personal data: in the encrypted DB, never logged. */
@OptIn(ExperimentalCoroutinesApi::class)
class DailyRepository(private val opener: DatabaseOpener) {
    fun observeCheckIn(date: LocalDate): Flow<CheckIn?> = flow { emit(opener.get()) }.flatMapLatest { db ->
        db.dailyDao().observeCheckIn(date.toString()).map { row ->
            row?.let { CheckIn(date, it.sleep, it.energy, it.soreness) }
        }
    }

    suspend fun checkInsBetween(from: LocalDate, to: LocalDate): List<CheckIn> =
        opener.get().dailyDao().checkInsBetween(from.toString(), to.toString())
            .map { CheckIn(LocalDate.parse(it.date), it.sleep, it.energy, it.soreness) }

    suspend fun saveCheckIn(checkIn: CheckIn) {
        if (!DailyCheck.valid(checkIn)) throw InvalidDataException(listOf(checkIn))
        opener.get().dailyDao().saveCheckIn(
            CheckInEntity(checkIn.date.toString(), checkIn.sleep, checkIn.energy, checkIn.soreness),
        )
    }

    fun observeHabits(date: LocalDate): Flow<Set<Habit>> = flow { emit(opener.get()) }.flatMapLatest { db ->
        db.dailyDao().observeHabits(date.toString()).map { names ->
            Habit.entries.filter { it.name in names }.toSet()
        }
    }

    suspend fun setHabit(date: LocalDate, habit: Habit, done: Boolean) {
        val dao = opener.get().dailyDao()
        if (done) dao.markDone(HabitDoneEntity(date.toString(), habit.name)) else dao.markNotDone(date.toString(), habit.name)
    }
}
