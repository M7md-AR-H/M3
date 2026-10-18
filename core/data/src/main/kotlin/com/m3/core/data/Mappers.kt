package com.m3.core.data

import com.m3.core.data.db.CUSTOM_KIND
import com.m3.core.data.db.LocationEquipmentEntity
import com.m3.core.data.db.ProfileEntity
import com.m3.core.data.db.ScheduleDayEntity
import com.m3.core.data.db.TrainingLocationEntity
import com.m3.core.data.db.UiConfigEntity
import com.m3.core.data.db.UiConfigHistoryEntity
import com.m3.core.designsystem.config.Actor
import com.m3.core.designsystem.config.Specialist
import com.m3.core.designsystem.config.UiConfig
import com.m3.core.designsystem.config.UiConfigSnapshot
import com.m3.core.designsystem.config.UiHistoryEntry
import com.m3.core.model.EquipmentDetails
import com.m3.core.model.EquipmentItem
import com.m3.core.model.EquipmentKind
import com.m3.core.model.Experience
import com.m3.core.model.Goal
import com.m3.core.model.Profile
import com.m3.core.model.Sex
import com.m3.core.model.TrainingLocation
import com.m3.core.model.TrainingSetup
import java.time.DayOfWeek
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/** Pure conversions between Room rows and app models. No Android code, so they're JVM-tested. */
internal object Mappers {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ---- UiConfig ----

    fun toEntities(snapshot: UiConfigSnapshot, now: Long): Pair<UiConfigEntity, List<UiConfigHistoryEntity>> {
        val config = UiConfigEntity(
            json = json.encodeToString(UiConfig.serializer(), snapshot.config),
            version = snapshot.config.schemaVersion,
            updatedAt = now,
        )
        val history = snapshot.history.map { entry ->
            UiConfigHistoryEntity(
                json = json.encodeToString(UiConfig.serializer(), entry.previous),
                actor = actorToString(entry.actor),
                description = entry.description,
                createdAt = entry.timestampMillis,
            )
        }
        return config to history
    }

    /** Null if nothing was saved or the saved config can't be read; unreadable history rows are skipped. */
    fun toSnapshot(config: UiConfigEntity?, history: List<UiConfigHistoryEntity>): UiConfigSnapshot? {
        val current = config?.let { decodeConfig(it.json) } ?: return null
        val entries = history.mapNotNull { row ->
            val previous = decodeConfig(row.json) ?: return@mapNotNull null
            val actor = actorFromString(row.actor) ?: return@mapNotNull null
            UiHistoryEntry(actor, row.description, previous, row.createdAt)
        }
        return UiConfigSnapshot(current, entries)
    }

    private fun decodeConfig(text: String): UiConfig? =
        try {
            json.decodeFromString(UiConfig.serializer(), text)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }

    fun actorToString(actor: Actor): String = when (actor) {
        Actor.Owner -> OWNER
        is Actor.Agent -> AGENT_PREFIX + actor.specialist.name
    }

    fun actorFromString(text: String): Actor? = when {
        text == OWNER -> Actor.Owner
        text.startsWith(AGENT_PREFIX) ->
            Specialist.entries.firstOrNull { it.name == text.removePrefix(AGENT_PREFIX) }?.let(Actor::Agent)
        else -> null
    }

    private const val OWNER = "owner"
    private const val AGENT_PREFIX = "agent:"

    // ---- Profile ----

    fun toEntity(profile: Profile, createdAt: Long, now: Long) = ProfileEntity(
        birthYear = profile.birthYear,
        sex = profile.sex.name,
        heightCm = profile.heightCm,
        weightKg = profile.weightKg,
        goal = profile.goal.name,
        experience = profile.experience.name,
        injuriesText = profile.injuriesText,
        createdAt = createdAt,
        updatedAt = now,
    )

    /** Null if a stored enum name is unknown (data from a newer build). */
    fun toProfile(entity: ProfileEntity): Profile? {
        val sex = Sex.entries.firstOrNull { it.name == entity.sex } ?: return null
        val goal = Goal.entries.firstOrNull { it.name == entity.goal } ?: return null
        val experience = Experience.entries.firstOrNull { it.name == entity.experience } ?: return null
        return Profile(
            birthYear = entity.birthYear,
            sex = sex,
            heightCm = entity.heightCm,
            weightKg = entity.weightKg,
            goal = goal,
            experience = experience,
            injuriesText = entity.injuriesText,
        )
    }

    // ---- Training setup ----

    data class SetupRows(
        val locations: List<TrainingLocationEntity>,
        val equipment: List<LocationEquipmentEntity>,
        val schedule: List<ScheduleDayEntity>,
    )

    fun toRows(setup: TrainingSetup): SetupRows {
        val locations = setup.locations.mapIndexed { index, location ->
            TrainingLocationEntity(id = location.id, name = location.name.trim(), sortOrder = index)
        }
        val equipment = setup.locations.flatMap { location ->
            location.equipment.mapIndexed { index, item ->
                when (item) {
                    is EquipmentItem.Catalog -> LocationEquipmentEntity(
                        locationId = location.id,
                        kind = item.kind.name,
                        detailsJson = item.details.takeIf { it.weightsKg.isNotEmpty() }
                            ?.let { json.encodeToString(EquipmentDetails.serializer(), it) },
                        customName = null,
                        sortOrder = index,
                    )
                    is EquipmentItem.Custom -> LocationEquipmentEntity(
                        locationId = location.id,
                        kind = CUSTOM_KIND,
                        detailsJson = null,
                        customName = item.name.trim(),
                        sortOrder = index,
                    )
                }
            }
        }
        val schedule = DayOfWeek.entries.map { day -> ScheduleDayEntity(day.value, setup.availability[day]) }
        return SetupRows(locations, equipment, schedule)
    }

    fun toSetup(rows: SetupRows): TrainingSetup {
        val equipmentByLocation = rows.equipment.groupBy { it.locationId }
        val locations = rows.locations.sortedBy { it.sortOrder }.map { location ->
            TrainingLocation(
                id = location.id,
                name = location.name,
                equipment = equipmentByLocation[location.id].orEmpty()
                    .sortedBy { it.sortOrder }
                    .mapNotNull(::toItem),
            )
        }
        val ids = locations.map { it.id }.toSet()
        val availability = rows.schedule
            .mapNotNull { row ->
                val day = DayOfWeek.entries.firstOrNull { it.value == row.dayOfWeek } ?: return@mapNotNull null
                day to row.locationId?.takeIf { it in ids }
            }
            .toMap()
        return TrainingSetup(locations, availability)
    }

    /** Unknown kinds (from a newer build) are skipped rather than guessed. */
    private fun toItem(row: LocationEquipmentEntity): EquipmentItem? {
        if (row.kind == CUSTOM_KIND) return row.customName?.let(EquipmentItem::Custom)
        val kind = EquipmentKind.fromNameOrNull(row.kind) ?: return null
        val details = row.detailsJson?.let { text ->
            try {
                json.decodeFromString(EquipmentDetails.serializer(), text)
            } catch (_: SerializationException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        } ?: EquipmentDetails()
        return EquipmentItem.Catalog(kind, details)
    }
}
