package com.m3.core.model

import java.time.DayOfWeek

data class TrainingLocation(
    /** Stable id (UUID string); schedules and, later, plans point at it. */
    val id: String,
    val name: String,
    val equipment: List<EquipmentItem>,
)

/** Where the owner trains, with what, and on which days (ADR 0008). */
data class TrainingSetup(
    val locations: List<TrainingLocation>,
    /** Location id per day; a missing day or null means rest. */
    val schedule: Map<DayOfWeek, String?>,
) {
    fun locationFor(day: DayOfWeek): TrainingLocation? =
        schedule[day]?.let { id -> locations.firstOrNull { it.id == id } }

    companion object {
        val Empty = TrainingSetup(emptyList(), emptyMap())
    }
}
