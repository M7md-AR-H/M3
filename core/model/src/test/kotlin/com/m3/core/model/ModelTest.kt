package com.m3.core.model

import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnitsTest {
    @Test
    fun `formats without trailing zeros`() {
        assertEquals("72.5 kg", Units.kg(72.5))
        assertEquals("20 kg", Units.kg(20.0))
        assertEquals("180 cm", Units.cm(180))
    }

    @Test
    fun `parses decimals with dot or comma`() {
        assertEquals(72.5, Units.parseDecimal("72,5")!!, 0.0)
        assertEquals(72.5, Units.parseDecimal(" 72.5 ")!!, 0.0)
        assertNull(Units.parseDecimal("72.555"))
        assertNull(Units.parseDecimal("-3"))
        assertNull(Units.parseDecimal(""))
        assertNull(Units.parseDecimal("1e3"))
    }

    @Test
    fun `weight list supports ranges, steps and single values`() {
        assertEquals(listOf(2.0, 4.0, 6.0, 8.0, 12.5), Units.parseWeightList("2-8, 12.5"))
        assertEquals(listOf(5.0, 7.5, 10.0), Units.parseWeightList("5-10/2.5"))
        assertEquals(listOf(4.0, 8.0), Units.parseWeightList("8; 4; 8"))
        assertEquals(emptyList<Double>(), Units.parseWeightList("  "))
    }

    @Test
    fun `weight list rejects garbage and huge ranges`() {
        assertNull(Units.parseWeightList("heavy"))
        assertNull(Units.parseWeightList("10-2"))
        assertNull(Units.parseWeightList("1-1000/0.5"))
        assertNull(Units.parseWeightList("5-10/0"))
    }

    @Test
    fun `formatted weight list parses back to the same list`() {
        val weights = listOf(2.5, 5.0, 7.5, 24.0)
        assertEquals(weights, Units.parseWeightList(Units.formatWeightList(weights)))
    }
}

class ProfileValidatorTest {
    private val valid = Profile(
        birthYear = 1995,
        sex = Sex.MALE,
        heightCm = 180,
        weightKg = 80.5,
        goal = Goal.BUILD_MUSCLE,
        experience = Experience.SOME,
        injuriesText = "",
    )

    @Test
    fun `valid profile has no errors`() {
        assertTrue(ProfileValidator.validate(valid, currentYear = 2026).isEmpty())
    }

    @Test
    fun `out of range values are reported`() {
        val errors = ProfileValidator.validate(
            valid.copy(heightCm = 90, weightKg = Double.NaN, birthYear = 2020, injuriesText = "x".repeat(2001)),
            currentYear = 2026,
        )
        assertEquals(ProfileError.entries.toSet(), errors.toSet())
    }
}

class TrainingSetupValidatorTest {
    private val gym = TrainingLocation(
        id = "gym",
        name = "Gym",
        equipment = listOf(
            EquipmentItem.Catalog(EquipmentKind.BARBELL),
            EquipmentItem.Catalog(EquipmentKind.DUMBBELLS, EquipmentDetails(listOf(2.0, 4.0))),
            EquipmentItem.Custom("Viking press"),
        ),
    )

    @Test
    fun `valid setup has no errors`() {
        val setup = TrainingSetup(listOf(gym), mapOf(DayOfWeek.MONDAY to "gym", DayOfWeek.TUESDAY to null))
        assertTrue(TrainingSetupValidator.validate(setup).isEmpty())
    }

    @Test
    fun `catches duplicates, bad weights and unknown scheduled locations`() {
        val bad = gym.copy(
            equipment = listOf(
                EquipmentItem.Catalog(EquipmentKind.BARBELL),
                EquipmentItem.Catalog(EquipmentKind.BARBELL),
                EquipmentItem.Catalog(EquipmentKind.FLAT_BENCH, EquipmentDetails(listOf(10.0))),
                EquipmentItem.Catalog(EquipmentKind.KETTLEBELLS, EquipmentDetails(listOf(16.0, 8.0))),
            ),
        )
        val errors = TrainingSetupValidator.validate(
            TrainingSetup(listOf(bad, bad.copy(id = "gym2", name = " gym ")), mapOf(DayOfWeek.FRIDAY to "nowhere")),
        )
        assertTrue(SetupError.DuplicateItem("gym", EquipmentKind.BARBELL) in errors)
        assertTrue(SetupError.BadWeights("gym", EquipmentKind.FLAT_BENCH) in errors)
        assertTrue(SetupError.BadWeights("gym", EquipmentKind.KETTLEBELLS) in errors)
        assertTrue(SetupError.DuplicateLocationName("Gym") in errors)
        assertTrue(SetupError.UnknownScheduledLocation(DayOfWeek.FRIDAY) in errors)
    }

    @Test
    fun `blank names are rejected`() {
        val errors = TrainingSetupValidator.validate(
            TrainingSetup(listOf(gym.copy(name = " ", equipment = listOf(EquipmentItem.Custom("")))), emptyMap()),
        )
        assertTrue(SetupError.BlankLocationName("gym") in errors)
        assertTrue(SetupError.BadCustomName("gym") in errors)
    }

    @Test
    fun `catalog has every group and only weight-capable kinds take weights`() {
        assertTrue(EquipmentGroup.entries.all { EquipmentKind.byGroup(it).isNotEmpty() })
        assertEquals(
            setOf(EquipmentKind.DUMBBELLS, EquipmentKind.KETTLEBELLS),
            EquipmentKind.entries.filter { it.hasWeights }.toSet(),
        )
        assertTrue(EquipmentKind.entries.size >= 40)
    }
}
