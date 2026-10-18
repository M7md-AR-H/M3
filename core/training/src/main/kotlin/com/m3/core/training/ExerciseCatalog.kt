package com.m3.core.training

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class CatalogFile(val version: Int, val exercises: List<Exercise>)

/** The bundled, curated exercise catalog. Order matters: earlier entries win ties in the plan generator. */
class ExerciseCatalog(val version: Int, val exercises: List<Exercise>) {
    private val byId = exercises.associateBy { it.id }

    operator fun get(id: String): Exercise? = byId[id]

    companion object {
        private const val RESOURCE = "/m3/exercises.json"

        /** Loads and validates the bundled file. Throws if it is malformed, so a bad edit fails tests, not the owner. */
        fun bundled(): ExerciseCatalog {
            val text = ExerciseCatalog::class.java.getResourceAsStream(RESOURCE)
                ?.bufferedReader()?.use { it.readText() }
                ?: error("Exercise catalog missing: $RESOURCE")
            return parse(text)
        }

        fun parse(text: String): ExerciseCatalog {
            val file = Json.decodeFromString(CatalogFile.serializer(), text)
            val errors = validate(file.exercises)
            require(errors.isEmpty()) { "Invalid exercise catalog: $errors" }
            return ExerciseCatalog(file.version, file.exercises)
        }

        fun validate(exercises: List<Exercise>): List<String> = buildList {
            exercises.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { add("duplicate id $it") }
            exercises.forEach { e ->
                if (!ID.matches(e.id)) add("bad id ${e.id}")
                if (e.name.isBlank()) add("${e.id}: blank name")
                if (e.level !in 1..3) add("${e.id}: level ${e.level}")
                if (e.equipment.isEmpty()) add("${e.id}: no equipment alternatives (use [[]] for bodyweight)")
                if (e.primary.isEmpty() && e.pattern != MovementPattern.CONDITIONING) add("${e.id}: no primary muscle")
                if (e.equipment.distinct().size != e.equipment.size) add("${e.id}: repeated alternative")
            }
        }

        private val ID = Regex("[a-z0-9_]{2,40}")
    }
}
