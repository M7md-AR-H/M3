package com.m3.core.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UiConfigEntity::class,
        UiConfigHistoryEntity::class,
        ProfileEntity::class,
        TrainingLocationEntity::class,
        LocationEquipmentEntity::class,
        ScheduleDayEntity::class,
        DayOverrideEntity::class,
        AppStateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class M3Database : RoomDatabase() {
    abstract fun uiConfigDao(): UiConfigDao
    abstract fun profileDao(): ProfileDao
    abstract fun trainingDao(): TrainingDao
    abstract fun appStateDao(): AppStateDao
}
