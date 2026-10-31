package com.m3.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MealVisionTest {
    private fun food(raw: String) = (MealVision.parse(raw) as MealReading.Food).estimate

    private val good = """{"food":true,"name":"Chicken rice bowl","kcal":720,"protein_g":50,"carbs_g":80,"fat_g":20,"note":"Add more vegetables."}"""

    @Test
    fun `a good reply becomes an estimate`() {
        val e = food(good)
        assertEquals("Chicken rice bowl", e.name)
        assertEquals(720, e.kcal)
        assertEquals(50, e.proteinG)
        assertEquals(80, e.carbsG)
        assertEquals(20, e.fatG)
        assertEquals("Add more vegetables.", e.note)
    }

    @Test
    fun `text and code fences around the JSON are ignored`() {
        assertEquals(720, food("Here you go:\n```json\n$good\n```").kcal)
    }

    @Test
    fun `calories that contradict the macros are replaced by the macro sum`() {
        val e = food("""{"name":"Salad","kcal":2000,"protein_g":10,"carbs_g":20,"fat_g":10}""")
        assertEquals(210, e.kcal)
    }

    @Test
    fun `a photo without food says so`() {
        assertEquals(MealReading.NotFood, MealVision.parse("""{"food":false}"""))
    }

    @Test
    fun `broken or nonsense replies are refused`() {
        assertEquals(MealReading.Unreadable, MealVision.parse("I think that's pasta."))
        assertEquals(MealReading.Unreadable, MealVision.parse("""{"name":"Pasta""""))
        assertEquals(MealReading.Unreadable, MealVision.parse("""{"name":"Pasta","kcal":90000,"protein_g":10,"carbs_g":10,"fat_g":10}"""))
        assertEquals(MealReading.Unreadable, MealVision.parse("""{"name":"Pasta","kcal":500,"protein_g":-5,"carbs_g":10,"fat_g":10}"""))
        assertEquals(MealReading.Unreadable, MealVision.parse("""{"name":"  ","kcal":500,"protein_g":10,"carbs_g":10,"fat_g":10}"""))
        assertEquals(MealReading.Unreadable, MealVision.parse("""{"name":"Pasta","kcal":500}"""))
    }

    @Test
    fun `text is kept to one short line`() {
        val long = "x".repeat(300)
        val e = food("""{"name":"Soup\n\tbowl","kcal":300,"protein_g":20,"carbs_g":30,"fat_g":10,"note":"$long"}""")
        assertEquals("Soup bowl", e.name)
        assertEquals(200, e.note?.length)
    }

    @Test
    fun `a missing note is fine`() {
        assertNull(food("""{"name":"Soup","kcal":300,"protein_g":20,"carbs_g":30,"fat_g":10}""").note)
    }
}
