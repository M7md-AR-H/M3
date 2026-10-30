package com.m3.core.model

import java.time.LocalDate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Daily energy and macro targets. Numbers that matter come from here, never from the model (ADR 0003). */
data class NutritionTargets(
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    /** Basal metabolic rate (Mifflin-St Jeor). */
    val bmrKcal: Int,
    /** Energy to hold the current weight, from BMR and activity. */
    val maintenanceKcal: Int,
    /** True when a safety rule raised [kcal] above what the goal alone would give. */
    val heldBySafety: Boolean,
)

/** One logged food. Grams are whole numbers; an unknown macro is 0. */
data class FoodEntry(
    val id: Long = 0,
    val date: LocalDate,
    val atMillis: Long,
    val name: String,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
)

data class DayTotals(val kcal: Int = 0, val proteinG: Int = 0, val carbsG: Int = 0, val fatG: Int = 0)

enum class FoodEntryError { NAME_MISSING, NAME_TOO_LONG, KCAL_OUT_OF_RANGE, MACRO_OUT_OF_RANGE }

object Nutrition {
    const val MAX_FOOD_NAME_CHARS = 80
    const val MAX_ENTRY_KCAL = 5_000
    const val MAX_ENTRY_MACRO_G = 500

    private const val FEMALE_FLOOR_KCAL = 1_200
    private const val MALE_FLOOR_KCAL = 1_500
    private const val ADULT_AGE = 18
    private const val UNDERWEIGHT_BMI = 18.5

    /** At most 1% of body weight per week, at 7,700 kcal per kg. */
    private const val MAX_WEEKLY_LOSS_FRACTION = 0.01
    private const val KCAL_PER_KG = 7_700.0

    private const val FAT_G_PER_KG = 0.8
    private const val MAX_PROTEIN_SHARE = 0.35
    private const val MAX_FAT_SHARE = 0.30
    private const val KCAL_PER_G_PROTEIN = 4
    private const val KCAL_PER_G_CARB = 4
    private const val KCAL_PER_G_FAT = 9

    fun bmr(profile: Profile, age: Int): Double {
        val base = 10.0 * profile.weightKg + 6.25 * profile.heightCm - 5.0 * age
        return when (profile.sex) {
            Sex.MALE -> base + 5.0
            Sex.FEMALE -> base - 161.0
        }
    }

    private fun activityFactor(sessionsPerWeek: Int) = when {
        sessionsPerWeek <= 0 -> 1.2
        sessionsPerWeek <= 3 -> 1.375
        sessionsPerWeek <= 5 -> 1.55
        else -> 1.725
    }

    private fun goalFactor(goal: Goal) = when (goal) {
        Goal.LOSE_FAT -> 0.80
        Goal.BUILD_MUSCLE -> 1.10
        Goal.GET_STRONGER -> 1.05
        Goal.GENERAL_FITNESS -> 1.0
    }

    private fun proteinPerKg(goal: Goal) = when (goal) {
        Goal.LOSE_FAT -> 2.0
        Goal.BUILD_MUSCLE, Goal.GET_STRONGER -> 1.8
        Goal.GENERAL_FITNESS -> 1.6
    }

    /**
     * The floors below hold whatever the goal asks for: never under the calorie floor, never a deficit that loses
     * more than 1% of body weight a week, and no deficit at all for anyone under 18 or underweight. The floor never
     * pushes the target above maintenance, so a small person isn't told to gain.
     */
    fun targets(profile: Profile, currentYear: Int, sessionsPerWeek: Int): NutritionTargets {
        val age = currentYear - profile.birthYear
        val bmr = bmr(profile, age)
        val maintenance = bmr * activityFactor(sessionsPerWeek)
        val wanted = maintenance * goalFactor(profile.goal)

        val floor = min(
            maintenance,
            when (profile.sex) {
                Sex.FEMALE -> FEMALE_FLOOR_KCAL.toDouble()
                Sex.MALE -> MALE_FLOOR_KCAL.toDouble()
            },
        )
        val maxDailyDeficit = profile.weightKg * MAX_WEEKLY_LOSS_FRACTION * KCAL_PER_KG / 7.0
        val heightM = profile.heightCm / 100.0
        val underweight = profile.weightKg / (heightM * heightM) < UNDERWEIGHT_BMI
        val lowest = if (age < ADULT_AGE || underweight) maintenance else max(floor, maintenance - maxDailyDeficit)
        val kcal = roundToTen(max(wanted, lowest))

        val fatG = min(profile.weightKg * FAT_G_PER_KG, kcal * MAX_FAT_SHARE / KCAL_PER_G_FAT).roundToInt()
        val proteinG = min(profile.weightKg * proteinPerKg(profile.goal), kcal * MAX_PROTEIN_SHARE / KCAL_PER_G_PROTEIN).roundToInt()
        val carbsG = ((kcal - proteinG * KCAL_PER_G_PROTEIN - fatG * KCAL_PER_G_FAT).toDouble() / KCAL_PER_G_CARB).roundToInt()

        return NutritionTargets(
            kcal = kcal,
            proteinG = proteinG,
            carbsG = carbsG,
            fatG = fatG,
            bmrKcal = bmr.roundToInt(),
            maintenanceKcal = maintenance.roundToInt(),
            heldBySafety = wanted < lowest,
        )
    }

    fun totals(entries: List<FoodEntry>): DayTotals = DayTotals(
        kcal = entries.sumOf { it.kcal },
        proteinG = entries.sumOf { it.proteinG },
        carbsG = entries.sumOf { it.carbsG },
        fatG = entries.sumOf { it.fatG },
    )

    fun validate(entry: FoodEntry): List<FoodEntryError> = buildList {
        if (entry.name.isBlank()) add(FoodEntryError.NAME_MISSING)
        if (entry.name.length > MAX_FOOD_NAME_CHARS) add(FoodEntryError.NAME_TOO_LONG)
        if (entry.kcal !in 0..MAX_ENTRY_KCAL) add(FoodEntryError.KCAL_OUT_OF_RANGE)
        if (listOf(entry.proteinG, entry.carbsG, entry.fatG).any { it !in 0..MAX_ENTRY_MACRO_G }) {
            add(FoodEntryError.MACRO_OUT_OF_RANGE)
        }
    }

    private fun roundToTen(value: Double): Int = (value / 10.0).roundToInt() * 10
}
