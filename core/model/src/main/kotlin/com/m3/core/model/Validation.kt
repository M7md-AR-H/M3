package com.m3.core.model

import java.time.DayOfWeek

/** Ranges for owner-entered data. Checked in code before anything is saved. */
object Limits {
    const val MIN_HEIGHT_CM = 120
    const val MAX_HEIGHT_CM = 230
    const val MIN_WEIGHT_KG = 30.0
    const val MAX_WEIGHT_KG = 300.0
    const val MIN_AGE_YEARS = 13
    const val MAX_AGE_YEARS = 100
    const val MAX_INJURIES_CHARS = 2000
    const val MAX_LOCATIONS = 10
    const val MAX_LOCATION_NAME_CHARS = 40
    const val MAX_CUSTOM_ITEMS = 30
    const val MAX_CUSTOM_NAME_CHARS = 40
    const val MAX_WEIGHTS_PER_ITEM = 60
    const val MIN_ITEM_WEIGHT_KG = 0.5
    const val MAX_ITEM_WEIGHT_KG = 200.0
}

enum class ProfileError {
    HEIGHT_OUT_OF_RANGE,
    WEIGHT_OUT_OF_RANGE,
    BIRTH_YEAR_OUT_OF_RANGE,
    INJURIES_TOO_LONG,
}

sealed interface SetupError {
    data object TooManyLocations : SetupError
    data class BlankLocationName(val locationId: String) : SetupError
    data class LocationNameTooLong(val locationId: String) : SetupError
    data class DuplicateLocationName(val name: String) : SetupError
    data class DuplicateLocationId(val locationId: String) : SetupError
    data class DuplicateItem(val locationId: String, val kind: EquipmentKind) : SetupError
    data class TooManyCustomItems(val locationId: String) : SetupError
    data class BadCustomName(val locationId: String) : SetupError
    data class BadWeights(val locationId: String, val kind: EquipmentKind) : SetupError
    data class UnknownAvailableLocation(val day: DayOfWeek) : SetupError
}

object ProfileValidator {
    fun validate(profile: Profile, currentYear: Int): List<ProfileError> = buildList {
        if (profile.heightCm !in Limits.MIN_HEIGHT_CM..Limits.MAX_HEIGHT_CM) add(ProfileError.HEIGHT_OUT_OF_RANGE)
        if (!profile.weightKg.isFinite() || profile.weightKg !in Limits.MIN_WEIGHT_KG..Limits.MAX_WEIGHT_KG) {
            add(ProfileError.WEIGHT_OUT_OF_RANGE)
        }
        if (profile.birthYear !in birthYearRange(currentYear)) add(ProfileError.BIRTH_YEAR_OUT_OF_RANGE)
        if (profile.injuriesText.length > Limits.MAX_INJURIES_CHARS) add(ProfileError.INJURIES_TOO_LONG)
    }

    fun birthYearRange(currentYear: Int): IntRange =
        (currentYear - Limits.MAX_AGE_YEARS)..(currentYear - Limits.MIN_AGE_YEARS)
}

object TrainingSetupValidator {
    fun validate(setup: TrainingSetup): List<SetupError> = buildList {
        if (setup.locations.size > Limits.MAX_LOCATIONS) add(SetupError.TooManyLocations)
        setup.locations.groupBy { it.id }.filterValues { it.size > 1 }.keys
            .forEach { add(SetupError.DuplicateLocationId(it)) }
        setup.locations.groupBy { it.name.trim().lowercase() }
            .filter { it.key.isNotEmpty() && it.value.size > 1 }
            .forEach { (_, same) -> add(SetupError.DuplicateLocationName(same.first().name.trim())) }
        setup.locations.forEach { validateLocation(it) }
        val ids = setup.locations.map { it.id }.toSet()
        setup.availability.forEach { (day, id) ->
            if (id != null && id !in ids) add(SetupError.UnknownAvailableLocation(day))
        }
    }

    private fun MutableList<SetupError>.validateLocation(location: TrainingLocation) {
        val name = location.name.trim()
        if (name.isEmpty()) add(SetupError.BlankLocationName(location.id))
        if (name.length > Limits.MAX_LOCATION_NAME_CHARS) add(SetupError.LocationNameTooLong(location.id))

        val catalog = location.equipment.filterIsInstance<EquipmentItem.Catalog>()
        catalog.groupBy { it.kind }.filterValues { it.size > 1 }.keys
            .forEach { add(SetupError.DuplicateItem(location.id, it)) }
        catalog.forEach { item ->
            if (!weightsValid(item)) add(SetupError.BadWeights(location.id, item.kind))
        }

        val custom = location.equipment.filterIsInstance<EquipmentItem.Custom>()
        if (custom.size > Limits.MAX_CUSTOM_ITEMS) add(SetupError.TooManyCustomItems(location.id))
        val names = custom.map { it.name.trim() }
        val badName = names.any { it.isEmpty() || it.length > Limits.MAX_CUSTOM_NAME_CHARS }
        val duplicate = names.map { it.lowercase() }.distinct().size != names.size
        if (badName || duplicate) add(SetupError.BadCustomName(location.id))
    }

    private fun weightsValid(item: EquipmentItem.Catalog): Boolean {
        val weights = item.details.weightsKg
        if (weights.isEmpty()) return true
        return item.kind.hasWeights &&
            weights.size <= Limits.MAX_WEIGHTS_PER_ITEM &&
            weights.all { it.isFinite() && it in Limits.MIN_ITEM_WEIGHT_KG..Limits.MAX_ITEM_WEIGHT_KG } &&
            weights.zipWithNext().all { (a, b) -> a < b }
    }
}
