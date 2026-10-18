package com.m3.core.model

import java.time.DayOfWeek

data class TrainingLocation(
    /** Stable id (UUID string); availability and plans point at it. */
    val id: String,
    val name: String,
    val equipment: List<EquipmentItem>,
)

/** Where the owner trains, with what, and when they are free (ADR 0008, ADR 0009). */
data class TrainingSetup(
    val locations: List<TrainingLocation>,
    /** Where the owner could train each day (ADR 0009); a missing day or null means not free. The Coach picks the training days. */
    val availability: Map<DayOfWeek, String?>,
) {
    fun locationFor(day: DayOfWeek): TrainingLocation? =
        availability[day]?.let { id -> locations.firstOrNull { it.id == id } }

    companion object {
        val Empty = TrainingSetup(emptyList(), emptyMap())
    }
}
