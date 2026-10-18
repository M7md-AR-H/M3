package com.m3.core.model

/** Used only for energy formulas (e.g. Mifflin-St Jeor). */
enum class Sex(val displayName: String) { FEMALE("Female"), MALE("Male") }

enum class Goal(val displayName: String) {
    LOSE_FAT("Lose fat"),
    BUILD_MUSCLE("Build muscle"),
    GET_STRONGER("Get stronger"),
    GENERAL_FITNESS("General fitness"),
}

enum class Experience(val displayName: String, val description: String) {
    NEW("New", "Little or no regular training yet"),
    SOME("Some", "Trained on and off, knows the main lifts"),
    EXPERIENCED("Experienced", "Trains regularly, for years"),
}

/** The owner's profile. Metric only (ADR 0008). */
data class Profile(
    val birthYear: Int,
    val sex: Sex,
    val heightCm: Int,
    val weightKg: Double,
    val goal: Goal,
    val experience: Experience,
    val injuriesText: String,
)
