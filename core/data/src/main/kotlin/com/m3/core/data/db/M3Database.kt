package com.m3.core.data.db

import androidx.room.AutoMigration
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
        ChatMessageEntity::class,
        ExcludedExerciseEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // v2 only adds tables (chat, excluded exercises), so Room can migrate it by itself.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class M3Database : RoomDatabase() {
    abstract fun uiConfigDao(): UiConfigDao
    abstract fun profileDao(): ProfileDao
    abstract fun trainingDao(): TrainingDao
    abstract fun appStateDao(): AppStateDao
    abstract fun chatDao(): ChatDao
    abstract fun planOverridesDao(): PlanOverridesDao
}
