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
        WorkoutSessionEntity::class,
        WorkoutSetEntity::class,
        StepDayEntity::class,
        StepReadingEntity::class,
        FoodEntryEntity::class,
        WeightEntryEntity::class,
        MeasurementEntity::class,
        CheckInEntity::class,
        HabitDoneEntity::class,
        DeloadWeekEntity::class,
        GroceryItemEntity::class,
    ],
    version = 8,
    exportSchema = true,
    // Every migration so far only adds tables (chat, workouts, steps, food, body, daily), so Room does them by itself.
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
    ],
)
abstract class M3Database : RoomDatabase() {
    abstract fun uiConfigDao(): UiConfigDao
    abstract fun profileDao(): ProfileDao
    abstract fun trainingDao(): TrainingDao
    abstract fun appStateDao(): AppStateDao
    abstract fun chatDao(): ChatDao
    abstract fun planOverridesDao(): PlanOverridesDao
    abstract fun workoutDao(): WorkoutDao
    abstract fun stepDao(): StepDao
    abstract fun foodDao(): FoodDao
    abstract fun bodyDao(): BodyDao
    abstract fun dailyDao(): DailyDao
    abstract fun deloadDao(): DeloadDao
    abstract fun groceryDao(): GroceryDao
}
