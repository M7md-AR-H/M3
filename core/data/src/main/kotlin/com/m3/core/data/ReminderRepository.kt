package com.m3.core.data

import com.m3.core.data.db.DatabaseOpener
import com.m3.core.data.db.ReminderPrefsEntity
import com.m3.core.data.db.ReminderSettingEntity
import com.m3.core.model.ReminderKind
import com.m3.core.model.ReminderSetting
import com.m3.core.model.ReminderSettings
import com.m3.core.model.Reminders
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

/** When reminders fire. Names from a newer build are skipped, like other stored enums. */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderRepository(private val opener: DatabaseOpener) {
    fun observe(): Flow<ReminderSettings> = flow { emit(opener.get()) }.flatMapLatest { db ->
        val dao = db.reminderDao()
        combine(dao.observeSettings(), dao.observePrefs()) { rows, prefs ->
            val saved = rows.mapNotNull { row ->
                ReminderKind.entries.firstOrNull { it.name == row.kind }?.let { kind ->
                    kind to ReminderSetting(kind, row.isOn, row.minute, row.everyHours)
                }
            }.toMap()
            ReminderSettings(
                items = ReminderKind.entries.associateWith { saved[it] ?: ReminderSetting(it) },
                showDetails = prefs?.showDetails ?: false,
            )
        }
    }

    suspend fun save(setting: ReminderSetting) {
        if (!Reminders.validMinute(setting.minute) || setting.everyHours !in Reminders.WATER_HOURS) {
            throw InvalidDataException(listOf(setting))
        }
        opener.get().reminderDao().saveSetting(
            ReminderSettingEntity(setting.kind.name, setting.on, setting.minute, setting.everyHours),
        )
    }

    suspend fun setShowDetails(show: Boolean) = opener.get().reminderDao().savePrefs(ReminderPrefsEntity(showDetails = show))
}
