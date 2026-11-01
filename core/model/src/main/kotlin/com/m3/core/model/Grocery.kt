package com.m3.core.model

import java.time.LocalDate
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class GrocerySection(val displayName: String) {
    PROTEIN("Protein"),
    CARBS("Carbs"),
    FRUIT_VEG("Fruit & veg"),
    FATS("Fats"),
    YOURS("Your items"),
}

/** One suggested line of the shop. [amount] is ready to show ("1.4 kg", "12 eggs"); empty for the owner's usual foods. */
data class GroceryLine(val name: String, val amount: String, val section: GrocerySection)

enum class GroceryNameError { MISSING, TOO_LONG }

object Grocery {
    const val MAX_NAME_CHARS = 60

    /** Names the owner logged on at least this many different days of the last [FREQUENT_DAYS] count as "usual". */
    const val FREQUENT_MIN_DAYS = 3
    const val FREQUENT_DAYS = 14
    const val MAX_FREQUENT = 10

    fun validateName(name: String): GroceryNameError? = when {
        name.isBlank() -> GroceryNameError.MISSING
        name.trim().length > MAX_NAME_CHARS -> GroceryNameError.TOO_LONG
        else -> null
    }

    /**
     * The foods logged on enough different days of the 14 days up to [today]: case-insensitive, trimmed, in the
     * spelling of the latest entry. Most often first, then A–Z. Names that wouldn't make a valid item are left out.
     */
    fun frequentFoods(entries: List<FoodEntry>, today: LocalDate): List<String> {
        val from = today.minusDays(FREQUENT_DAYS - 1L)
        val inWindow = entries.filter { !it.date.isBefore(from) && !it.date.isAfter(today) }
            .filter { validateName(it.name) == null }
        return inWindow.groupBy { it.name.trim().lowercase() }
            .mapNotNull { (_, group) ->
                val days = group.map { it.date }.toSet().size
                if (days < FREQUENT_MIN_DAYS) return@mapNotNull null
                val latest = group.maxWith(compareBy<FoodEntry> { it.date }.thenBy { it.atMillis }.thenBy { it.id })
                latest.name.trim() to days
            }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first.lowercase() })
            .take(MAX_FREQUENT)
            .map { it.first }
    }
}

/**
 * Turns a week of macro targets into a shopping list (Phase 2). Deterministic: a small table of staples, shares
 * of the protein, then carbs, then fat, and every amount rounded to what a shop sells. Nothing here is the model's.
 */
object GroceryPlanner {
    private const val DAYS = 7
    private const val OIL_G_PER_ML = 0.92
    private const val EGG_G = 50.0
    private const val EGGS_PER_BOX = 6
    private const val VEG_G = 3_500
    private const val FRUIT_PIECES = 14

    /** Per 100 g, from standard food tables (raw or dry weights as bought). */
    private class Staple(
        val name: String,
        val section: GrocerySection,
        val proteinG: Double,
        val carbsG: Double,
        val fatG: Double,
        /** Share of the weekly protein, carbs or fat this food covers in its stage. */
        val share: Double,
    )

    private val PROTEIN = listOf(
        Staple("Chicken breast", GrocerySection.PROTEIN, 22.5, 0.0, 2.6, 0.35),
        Staple("Eggs", GrocerySection.PROTEIN, 12.6, 0.7, 9.5, 0.20),
        Staple("Greek yogurt", GrocerySection.PROTEIN, 10.0, 4.0, 2.0, 0.15),
        Staple("Tinned tuna", GrocerySection.PROTEIN, 25.5, 0.0, 1.0, 0.15),
        Staple("Lentils (dry)", GrocerySection.PROTEIN, 24.0, 60.0, 1.5, 0.15),
    )
    private val CARBS = listOf(
        Staple("Oats", GrocerySection.CARBS, 13.0, 66.0, 7.0, 0.25),
        Staple("Rice (dry)", GrocerySection.CARBS, 7.0, 80.0, 0.7, 0.35),
        Staple("Potatoes", GrocerySection.CARBS, 2.0, 17.0, 0.1, 0.25),
        Staple("Wholemeal bread", GrocerySection.CARBS, 10.0, 41.0, 3.5, 0.15),
    )
    private val OIL = Staple("Olive oil", GrocerySection.FATS, 0.0, 0.0, 100.0, 0.60)
    private val NUTS = Staple("Mixed nuts", GrocerySection.FATS, 20.0, 21.0, 54.0, 0.40)

    fun week(targets: NutritionTargets, frequentFoods: List<String>): List<GroceryLine> {
        val protein = (targets.proteinG * DAYS).toDouble()
        var carbsBrought = 0.0
        var fatBrought = 0.0
        val lines = mutableListOf<GroceryLine>()

        // Protein first: fixed shares; the carbs and fat they bring come off what's left to buy.
        PROTEIN.forEach { food ->
            val grams = grams(food, protein * food.share, food.proteinG)
            carbsBrought += grams * food.carbsG / 100
            fatBrought += grams * food.fatG / 100
            if (food.name == "Eggs") eggs(grams)?.let { lines += GroceryLine(food.name, it, food.section) } else shopped(food, grams, 100)?.let { lines += it }
        }

        val carbsLeft = (targets.carbsG * DAYS - carbsBrought).coerceAtLeast(0.0)
        CARBS.forEach { food ->
            val grams = grams(food, carbsLeft * food.share, food.carbsG)
            fatBrought += grams * food.fatG / 100
            shopped(food, grams, 100)?.let { lines += it }
        }

        lines += GroceryLine("Mixed vegetables", Units.shopMass(VEG_G), GrocerySection.FRUIT_VEG)
        lines += GroceryLine("Bananas or apples", "$FRUIT_PIECES pieces", GrocerySection.FRUIT_VEG)

        val fatLeft = (targets.fatG * DAYS - fatBrought).coerceAtLeast(0.0)
        roundTo(fatLeft * OIL.share / OIL_G_PER_ML, 50).takeIf { it > 0 }?.let {
            lines += GroceryLine(OIL.name, Units.shopVolume(it), OIL.section)
        }
        shopped(NUTS, grams(NUTS, fatLeft * NUTS.share, NUTS.fatG), 50)?.let { lines += it }

        frequentFoods.forEach { lines += GroceryLine(it, "", GrocerySection.YOURS) }
        return lines
    }

    private fun grams(food: Staple, nutrientG: Double, per100: Double): Double =
        if (per100 <= 0.0) 0.0 else nutrientG / per100 * 100

    private fun shopped(food: Staple, grams: Double, step: Int): GroceryLine? =
        roundTo(grams, step).takeIf { it > 0 }?.let { GroceryLine(food.name, Units.shopMass(it), food.section) }

    private fun eggs(grams: Double): String? {
        val count = ceil(grams / EGG_G / EGGS_PER_BOX).toInt() * EGGS_PER_BOX
        return if (count > 0) "$count eggs" else null
    }

    private fun roundTo(value: Double, step: Int): Int = (value / step).roundToInt() * step
}
