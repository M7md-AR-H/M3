package com.m3.app.feature.onboarding

import com.m3.core.model.EquipmentDetails
import com.m3.core.model.EquipmentItem
import com.m3.core.model.EquipmentKind
import com.m3.core.model.Experience
import com.m3.core.model.Goal
import com.m3.core.model.Sex
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DraftsTest {
    private val complete = ProfileDraft(
        heightCm = "180",
        weightKg = "80,5",
        birthYear = "1995",
        sex = Sex.MALE,
        goal = Goal.GET_STRONGER,
        experience = Experience.EXPERIENCED,
        injuries = "  shoulder  ",
    )

    @Test
    fun `complete profile draft converts, trimming injuries`() {
        val profile = complete.toProfile(currentYear = 2026)!!
        assertEquals(80.5, profile.weightKg, 0.0)
        assertEquals("shoulder", profile.injuriesText)
    }

    @Test
    fun `about step reports each bad field`() {
        val errors = ProfileDraft(heightCm = "18", weightKg = "abc", birthYear = "2020").aboutErrors(2026)
        assertNotNull(errors.heightCm)
        assertNotNull(errors.weightKg)
        assertNotNull(errors.birthYear)
        assertNotNull(errors.sex)
        assertNull(complete.copy(goal = null).toProfile(2026))
    }

    @Test
    fun `adding and removing locations keeps the schedule consistent`() {
        var setup = SetupDraft().addLocation("Gym", "g").addLocation(" gym ", "dup").addLocation("Park", "p")
        assertEquals(listOf("g", "p"), setup.locations.map { it.id })
        assertNotNull(setup.addLocationError("GYM"))
        setup = setup.setDay(DayOfWeek.MONDAY, "g").setDay(DayOfWeek.TUESDAY, "p").removeLocation("g")
        assertEquals(mapOf(DayOfWeek.TUESDAY to "p"), setup.schedule)
    }

    @Test
    fun `equipment edits convert to catalog order with weights and custom items`() {
        val location = LocationDraft("h", "Home")
            .toggle(EquipmentKind.PULL_UP_BAR, true)
            .toggle(EquipmentKind.DUMBBELLS, true)
            .setWeights(EquipmentKind.DUMBBELLS, "2-6")
            .addCustom("Backpack")
            .addCustom("backpack")
        val built = location.toLocation()!!
        assertEquals(
            listOf(
                EquipmentItem.Catalog(EquipmentKind.DUMBBELLS, EquipmentDetails(listOf(2.0, 4.0, 6.0))),
                EquipmentItem.Catalog(EquipmentKind.PULL_UP_BAR),
                EquipmentItem.Custom("Backpack"),
            ),
            built.equipment,
        )
        val reloaded = LocationDraft.from(built)
        assertEquals("2, 4, 6", reloaded.items[EquipmentKind.DUMBBELLS])
        assertEquals(built, reloaded.toLocation())
    }

    @Test
    fun `bad weight text blocks conversion until fixed or unticked`() {
        val bad = LocationDraft("h", "Home").toggle(EquipmentKind.KETTLEBELLS, true).setWeights(EquipmentKind.KETTLEBELLS, "lots")
        assertTrue(EquipmentKind.KETTLEBELLS in bad.weightErrors)
        assertNull(bad.toLocation())
        assertNull(SetupDraft(listOf(bad)).toSetup())
        assertNotNull(bad.toggle(EquipmentKind.KETTLEBELLS, false).toLocation())
    }

    @Test
    fun `bodyweight only clears everything and select all ticks everything`() {
        val all = LocationDraft("g", "Gym").selectAll(EquipmentKind.entries)
        assertEquals(EquipmentKind.entries.toSet(), all.items.keys)
        val none = all.addCustom("Rope").bodyweightOnly()
        assertTrue(none.items.isEmpty())
        assertFalse(none.custom.isNotEmpty())
    }
}
