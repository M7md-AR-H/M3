package com.m3.coach

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** A model's guess at a plate. The owner checks it in the form before anything is saved. */
data class MealEstimate(
    val name: String,
    val kcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    /** One sentence of critique, if the model gave a usable one. */
    val note: String?,
)

sealed interface MealReading {
    data class Food(val estimate: MealEstimate) : MealReading
    data object NotFood : MealReading
    data object Unreadable : MealReading
}

/** The prompt for a meal photo and the checks on its answer. The model proposes, code decides (ADR 0003). */
object MealVision {
    const val MAX_OUTPUT_TOKENS = 200

    private const val MAX_KCAL = 3_000.0
    private const val MAX_MACRO_G = 300.0
    private const val MAX_NAME_CHARS = 60
    private const val MAX_NOTE_CHARS = 200

    /** Calories further than this from what the macros add up to are replaced by the macro sum. */
    private const val KCAL_TOLERANCE = 0.25

    const val PROMPT: String =
        "This photo should show a meal. Estimate everything on the plate. Reply with one line of JSON and nothing " +
            "else: {\"food\":true,\"name\":\"short name\",\"kcal\":500,\"protein_g\":30,\"carbs_g\":50,\"fat_g\":20," +
            "\"note\":\"one honest sentence on how to make it better\"}. Use your own numbers for this meal. " +
            "If there's no food in the photo, reply {\"food\":false}."

    @Serializable
    private data class Reply(
        val food: Boolean = true,
        val name: String? = null,
        val kcal: Double? = null,
        @SerialName("protein_g") val proteinG: Double? = null,
        @SerialName("carbs_g") val carbsG: Double? = null,
        @SerialName("fat_g") val fatG: Double? = null,
        val note: String? = null,
    )

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun parse(raw: String): MealReading {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return MealReading.Unreadable
        val reply = try {
            json.decodeFromString(Reply.serializer(), raw.substring(start, end + 1))
        } catch (_: SerializationException) {
            return MealReading.Unreadable
        } catch (_: IllegalArgumentException) {
            return MealReading.Unreadable
        }
        if (!reply.food) return MealReading.NotFood

        val name = reply.name?.oneLine()?.take(MAX_NAME_CHARS)?.takeIf { it.isNotBlank() } ?: return MealReading.Unreadable
        val kcal = reply.kcal?.takeIf { it.isFinite() && it in 1.0..MAX_KCAL } ?: return MealReading.Unreadable
        val protein = reply.proteinG.grams() ?: return MealReading.Unreadable
        val carbs = reply.carbsG.grams() ?: return MealReading.Unreadable
        val fat = reply.fatG.grams() ?: return MealReading.Unreadable

        val fromMacros = protein * 4 + carbs * 4 + fat * 9
        val consistent = fromMacros > 0 && abs(kcal - fromMacros) / fromMacros <= KCAL_TOLERANCE
        return MealReading.Food(
            MealEstimate(
                name = name,
                kcal = (if (consistent || fromMacros <= 0) kcal else fromMacros).coerceAtMost(MAX_KCAL).roundToInt(),
                proteinG = protein.roundToInt(),
                carbsG = carbs.roundToInt(),
                fatG = fat.roundToInt(),
                note = reply.note?.oneLine()?.take(MAX_NOTE_CHARS)?.takeIf { it.isNotBlank() },
            ),
        )
    }

    private fun Double?.grams(): Double? = this?.takeIf { it.isFinite() && it in 0.0..MAX_MACRO_G }

    private fun String.oneLine(): String = replace(Regex("\\s+"), " ").trim()
}
