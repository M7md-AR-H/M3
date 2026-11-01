package com.m3.core.model

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroceryTest {
    private fun targets(protein: Int, carbs: Int, fat: Int) = NutritionTargets(0, protein, carbs, fat, 0, 0, false)

    private fun amounts(lines: List<GroceryLine>) = lines.associate { it.name to it.amount }

    @Test
    fun `protein only targets give protein foods rounded to 100 g and whole boxes of eggs`() {
        val lines = GroceryPlanner.week(targets(100, 0, 0), emptyList())
        assertEquals(
            mapOf(
                "Chicken breast" to "1.1 kg",
                "Eggs" to "24 eggs",
                "Greek yogurt" to "1.1 kg",
                "Tinned tuna" to "400 g",
                "Lentils (dry)" to "400 g",
                "Mixed vegetables" to "3.5 kg",
                "Bananas or apples" to "14 pieces",
            ),
            amounts(lines),
        )
    }

    @Test
    fun `sections come in order with protein first and carbs after what protein brings`() {
        val lines = GroceryPlanner.week(targets(170, 280, 75), emptyList())
        assertEquals(lines.map { it.section }, lines.map { it.section }.sortedBy { it.ordinal })
        assertEquals("Chicken breast", lines.first().name)
        assertEquals(GrocerySection.PROTEIN, lines.first().section)
        val a = amounts(lines)
        assertTrue(a.keys.containsAll(listOf("Oats", "Rice (dry)", "Potatoes", "Wholemeal bread", "Olive oil", "Mixed nuts")))
        assertEquals("Mixed vegetables", lines.first { it.section == GrocerySection.FRUIT_VEG }.name)
    }

    @Test
    fun `amounts are shop sized and metric`() {
        val lines = GroceryPlanner.week(targets(170, 280, 75), emptyList())
        lines.forEach { line ->
            assertTrue(line.amount, !line.amount.contains("lb") && !line.amount.contains("oz"))
            when {
                line.name == "Eggs" -> assertEquals(0, line.amount.substringBefore(' ').toInt() % 6)
                line.name == "Olive oil" -> assertTrue(line.amount, line.amount.endsWith(" ml") || line.amount.endsWith(" l"))
                line.amount.endsWith(" kg") -> assertEquals(line.amount, 0, Math.round(line.amount.removeSuffix(" kg").toDouble() * 1_000).toInt() % 50)
                line.amount.endsWith(" g") -> assertEquals(line.amount, 0, line.amount.removeSuffix(" g").toInt() % 50)
            }
        }
    }

    @Test
    fun `very high protein clamps carbs and fats to nothing instead of going negative`() {
        val lines = GroceryPlanner.week(targets(400, 50, 20), emptyList())
        val names = lines.map { it.name }
        assertTrue("Oats" !in names && "Rice (dry)" !in names && "Potatoes" !in names && "Wholemeal bread" !in names)
        assertTrue("Olive oil" !in names && "Mixed nuts" !in names)
        assertTrue(lines.none { it.amount.startsWith("-") })
        assertTrue("Chicken breast" in names)
    }

    @Test
    fun `zero targets leave only the fixed fruit and veg`() {
        assertEquals(listOf("Mixed vegetables", "Bananas or apples"), GroceryPlanner.week(targets(0, 0, 0), emptyList()).map { it.name })
    }

    @Test
    fun `oil rounds to 50 ml and switches to litres from 1000`() {
        // No protein or carbs, so all the fat is bought: 400 g fat a week, 60% as oil = 240 g = 260.9 ml -> 250 ml.
        val oil = GroceryPlanner.week(targets(0, 0, 400 / 7 + 1), emptyList()).first { it.name == "Olive oil" }
        assertEquals("250 ml", oil.amount)
        assertEquals("1 l", Units.shopVolume(1_000))
        assertEquals("1.5 l", Units.shopVolume(1_500))
        assertEquals("50 ml", Units.shopVolume(50))
    }

    @Test
    fun `mass switches to kilograms from 1000 g`() {
        assertEquals("900 g", Units.shopMass(900))
        assertEquals("1 kg", Units.shopMass(1_000))
        assertEquals("1.4 kg", Units.shopMass(1_400))
        assertEquals("3.5 kg", Units.shopMass(3_500))
    }

    @Test
    fun `usual foods go last under your items with no amount`() {
        val lines = GroceryPlanner.week(targets(100, 200, 50), listOf("Skyr", "Dates"))
        assertEquals(listOf("Skyr", "Dates"), lines.takeLast(2).map { it.name })
        assertTrue(lines.takeLast(2).all { it.section == GrocerySection.YOURS && it.amount.isEmpty() })
    }

    private val today = LocalDate.of(2026, 11, 1)

    private fun entry(name: String, daysAgo: Long, at: Long = 0) =
        FoodEntry(date = today.minusDays(daysAgo), atMillis = at, name = name, kcal = 100, proteinG = 1, carbsG = 1, fatG = 1)

    @Test
    fun `frequent foods need 3 different days in the last 14, case-insensitive, latest spelling`() {
        val entries = listOf(
            entry("skyr", 10), entry(" Skyr ", 5), entry("SKYR", 1),
            entry("Banana", 0), entry("banana", 0, at = 5), entry("Banana", 1),
            entry("Dates", 14), entry("Dates", 2), entry("Dates", 1),
            entry("Rice", 3), entry("Rice", 4), entry("Rice", 6), entry("rice", 7),
        )
        // Skyr: 3 days; Banana: 2 days; Dates: the oldest is outside the window (14 days ago); Rice: 4 days.
        assertEquals(listOf("Rice", "SKYR"), Grocery.frequentFoods(entries, today))
        assertEquals(emptyList<String>(), Grocery.frequentFoods(emptyList(), today))
    }

    @Test
    fun `names are validated after trimming`() {
        assertEquals(GroceryNameError.MISSING, Grocery.validateName("   "))
        assertEquals(GroceryNameError.MISSING, Grocery.validateName(""))
        assertNull(Grocery.validateName(" a "))
        assertNull(Grocery.validateName("x".repeat(60)))
        assertEquals(GroceryNameError.TOO_LONG, Grocery.validateName("x".repeat(61)))
        assertNull(Grocery.validateName(" " + "x".repeat(60) + " "))
    }
}
