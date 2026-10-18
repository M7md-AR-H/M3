package com.m3.ai.bench

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** How a benchmark answer is scored automatically. Speed numbers come from the runtime. */
enum class Check { NON_BLANK, JSON_WORKOUT, IMAGE_SHAPES }

data class BenchmarkPrompt(
    val id: String,
    val prompt: String,
    val check: Check,
    val systemInstruction: String? = null,
    val withImage: Boolean = false,
    val maxOutputTokens: Int = 256,
)

/**
 * Fixed prompt set used to compare models (ADR 0002). It contains no personal data, so prompts
 * and answers may be shown and logged in debug builds.
 */
object BenchmarkPrompts {
    val all: List<BenchmarkPrompt> = listOf(
        BenchmarkPrompt(
            id = "short",
            prompt = "In one sentence, what is progressive overload?",
            check = Check.NON_BLANK,
            maxOutputTokens = 96,
        ),
        BenchmarkPrompt(
            id = "plan",
            prompt = "Write a 3-day beginner full-body gym plan. For each day list 4 exercises " +
                "with sets x reps. Keep it under 200 words.",
            check = Check.NON_BLANK,
            maxOutputTokens = 400,
        ),
        BenchmarkPrompt(
            id = "json",
            systemInstruction = "You are a strict data extractor. Reply with one JSON object only: " +
                "no prose, no code fences.",
            prompt = "Extract the workout from this note as JSON with keys \"exercise\" (string), " +
                "\"sets\" (integer), \"reps\" (integer) and \"weightKg\" (number): " +
                "\"Bench press today, 3 sets of 8 at 60 kg.\"",
            check = Check.JSON_WORKOUT,
            maxOutputTokens = 96,
        ),
        BenchmarkPrompt(
            id = "image",
            prompt = "What shapes are in this image, and what colour is each? Answer in one short sentence.",
            check = Check.IMAGE_SHAPES,
            withImage = true,
            maxOutputTokens = 64,
        ),
    )
}

enum class CheckOutcome {
    PASS,

    /** Right answer, but only after stripping extra text such as code fences. */
    PASS_AFTER_CLEANUP,
    FAIL,
}

object AnswerChecks {
    private val json = Json { ignoreUnknownKeys = false }

    @Serializable
    private data class Workout(val exercise: String, val sets: Int, val reps: Int, val weightKg: Double)

    fun evaluate(check: Check, answer: String): CheckOutcome = when (check) {
        Check.NON_BLANK -> if (answer.isNotBlank()) CheckOutcome.PASS else CheckOutcome.FAIL
        Check.JSON_WORKOUT -> workout(answer)
        Check.IMAGE_SHAPES -> {
            val lower = answer.lowercase()
            val ok = listOf("red", "blue", "circle", "square").all { it in lower }
            if (ok) CheckOutcome.PASS else CheckOutcome.FAIL
        }
    }

    private fun workout(answer: String): CheckOutcome {
        if (isExpectedWorkout(answer.trim())) return CheckOutcome.PASS
        val start = answer.indexOf('{')
        val end = answer.lastIndexOf('}')
        if (start in 0 until end && isExpectedWorkout(answer.substring(start, end + 1))) {
            return CheckOutcome.PASS_AFTER_CLEANUP
        }
        return CheckOutcome.FAIL
    }

    private fun isExpectedWorkout(text: String): Boolean {
        val workout = runCatching { json.decodeFromString(Workout.serializer(), text) }.getOrNull()
            ?: return false
        return workout.exercise.trim().equals("bench press", ignoreCase = true) &&
            workout.sets == 3 && workout.reps == 8 && workout.weightKg == 60.0
    }
}
