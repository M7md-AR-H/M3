package com.m3.core.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Enums are stored by name (text), so reordering an enum never changes stored data. */

/** Single row (id = 1): the current UiConfig as JSON (ADR 0007). */
@Entity(tableName = "ui_config")
data class UiConfigEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val json: String,
    /** `UiConfig.schemaVersion` of [json]. */
    val version: Int,
    val updatedAt: Long,
)

/** One undo step: the config before a change. Capped at UiConfigStore.MAX_HISTORY rows. */
@Entity(tableName = "ui_config_history")
data class UiConfigHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** The previous UiConfig as JSON. */
    val json: String,
    /** "owner" or "agent:<SPECIALIST>". */
    val actor: String,
    val description: String,
    val createdAt: Long,
)

/** Single row (id = 1). Metric only. */
@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val birthYear: Int,
    val sex: String,
    val heightCm: Int,
    val weightKg: Double,
    val goal: String,
    val experience: String,
    val injuriesText: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "training_location")
data class TrainingLocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int,
)

@Entity(
    tableName = "location_equipment",
    foreignKeys = [
        ForeignKey(
            entity = TrainingLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("locationId")],
)
data class LocationEquipmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val locationId: String,
    /** An EquipmentKind name, or [CUSTOM_KIND] with [customName] set. */
    val kind: String,
    /** EquipmentDetails as JSON, or null when there are no details. */
    val detailsJson: String?,
    val customName: String?,
    val sortOrder: Int,
)

/** One row per weekday (1 = Monday … 7 = Sunday). A null location means rest. */
@Entity(
    tableName = "schedule_day",
    foreignKeys = [
        ForeignKey(
            entity = TrainingLocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["locationId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("locationId")],
)
data class ScheduleDayEntity(
    @PrimaryKey val dayOfWeek: Int,
    val locationId: String?,
)

/** "Today I only have X and Y" (ADR 0008). Table only for now; the Coach uses it in Phase 1c. */
@Entity(tableName = "day_override", indices = [Index("date")])
data class DayOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO date, e.g. "2026-10-18". */
    val date: String,
    val itemsJson: String,
    val createdAt: Long,
)

/** Single row (id = 1). */
@Entity(tableName = "app_state")
data class AppStateEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val onboardingCompleted: Boolean,
    /** Version of the app-level data layout, for one-off data fixes outside Room migrations. */
    val schemaVersion: Int,
)

const val SINGLE_ROW_ID = 1
const val CUSTOM_KIND = "CUSTOM"
