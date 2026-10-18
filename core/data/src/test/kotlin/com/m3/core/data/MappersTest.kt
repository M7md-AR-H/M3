package com.m3.core.data

import com.m3.core.data.db.LocationEquipmentEntity
import com.m3.core.data.db.ScheduleDayEntity
import com.m3.core.data.db.TrainingLocationEntity
import com.m3.core.data.db.UiConfigEntity
import com.m3.core.data.db.UiConfigHistoryEntity
import com.m3.core.designsystem.config.Actor
import com.m3.core.designsystem.config.LockKey
import com.m3.core.designsystem.config.Specialist
import com.m3.core.designsystem.config.ThemeMode
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MappersTest {
    @Test
    fun `ui config snapshot round-trips`() {
        val dark = UiConfig(theme = UiConfig().theme.copy(mode = ThemeMode.DARK), locks = setOf(LockKey.TABS))
        val snapshot = UiConfigSnapshot(
            config = dark,
            history = listOf(
                UiHistoryEntry(Actor.Owner, "switched to dark mode", UiConfig(), 10L),
                UiHistoryEntry(Actor.Agent(Specialist.TRAINER), "hid the Workout card", dark, 20L),
            ),
        )
        val (config, history) = Mappers.toEntities(snapshot, now = 99L)
        assertEquals(1, config.version)
        assertEquals(snapshot, Mappers.toSnapshot(config, history))
    }

    @Test
    fun `unreadable rows are skipped, unreadable config means nothing saved`() {
        val good = Mappers.toEntities(UiConfigSnapshot(UiConfig(), emptyList()), 0L).first
        val history = listOf(
            UiConfigHistoryEntity(json = "{not json", actor = "owner", description = "x", createdAt = 1),
            UiConfigHistoryEntity(json = good.json, actor = "robot", description = "x", createdAt = 2),
            UiConfigHistoryEntity(json = good.json, actor = "agent:COACH", description = "ok", createdAt = 3),
        )
        val restored = Mappers.toSnapshot(good, history)!!
        assertEquals(listOf("ok"), restored.history.map { it.description })
        assertNull(Mappers.toSnapshot(UiConfigEntity(json = "[]", version = 1, updatedAt = 0), emptyList()))
        assertNull(Mappers.toSnapshot(null, emptyList()))
    }

    @Test
    fun `unknown json keys from a newer build are ignored`() {
        val json = Mappers.toEntities(UiConfigSnapshot(UiConfig(), emptyList()), 0L).first.json
        val withExtra = json.replaceFirst("{", "{\"futureField\":true,")
        assertEquals(UiConfig(), Mappers.toSnapshot(UiConfigEntity(json = withExtra, version = 2, updatedAt = 0), emptyList())!!.config)
    }

    @Test
    fun `profile round-trips and unknown enum names give null`() {
        val profile = Profile(1990, Sex.FEMALE, 168, 61.5, Goal.LOSE_FAT, Experience.NEW, "knee")
        val entity = Mappers.toEntity(profile, createdAt = 1, now = 2)
        assertEquals(profile, Mappers.toProfile(entity))
        assertNull(Mappers.toProfile(entity.copy(goal = "FLY")))
    }

    @Test
    fun `training setup round-trips with order, weights, custom items and rest days`() {
        val setup = TrainingSetup(
            locations = listOf(
                TrainingLocation(
                    "g",
                    "Gym",
                    listOf(
                        EquipmentItem.Catalog(EquipmentKind.SQUAT_RACK),
                        EquipmentItem.Catalog(EquipmentKind.DUMBBELLS, EquipmentDetails(listOf(2.5, 5.0))),
                        EquipmentItem.Custom("Yoke"),
                    ),
                ),
                TrainingLocation("h", "Home", emptyList()),
            ),
            availability = mapOf(DayOfWeek.MONDAY to "g", DayOfWeek.SATURDAY to "h"),
        )
        val rows = Mappers.toRows(setup)
        assertEquals(7, rows.schedule.size)
        assertNull(rows.equipment.first { it.kind == "SQUAT_RACK" }.detailsJson)
        val back = Mappers.toSetup(rows)
        assertEquals(setup.locations, back.locations)
        assertEquals("g", back.availability[DayOfWeek.MONDAY])
        assertNull(back.availability[DayOfWeek.TUESDAY])
    }

    @Test
    fun `unknown equipment kinds and dangling availability ids are dropped`() {
        val rows = Mappers.SetupRows(
            locations = listOf(TrainingLocationEntity("g", "Gym", 0)),
            equipment = listOf(
                LocationEquipmentEntity(locationId = "g", kind = "HOVERBOARD", detailsJson = null, customName = null, sortOrder = 0),
                LocationEquipmentEntity(locationId = "g", kind = "BARBELL", detailsJson = "{broken", customName = null, sortOrder = 1),
            ),
            schedule = listOf(ScheduleDayEntity(1, "gone"), ScheduleDayEntity(9, "g")),
        )
        val setup = Mappers.toSetup(rows)
        assertEquals(listOf(EquipmentItem.Catalog(EquipmentKind.BARBELL)), setup.locations.single().equipment)
        assertEquals(mapOf(DayOfWeek.MONDAY to null), setup.availability)
    }
}
