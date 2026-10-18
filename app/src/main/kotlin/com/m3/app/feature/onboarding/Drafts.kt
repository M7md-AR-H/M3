package com.m3.app.feature.onboarding

import com.m3.core.model.EquipmentDetails
import com.m3.core.model.EquipmentItem
import com.m3.core.model.EquipmentKind
import com.m3.core.model.Experience
import com.m3.core.model.Goal
import com.m3.core.model.Limits
import com.m3.core.model.Profile
import com.m3.core.model.ProfileValidator
import com.m3.core.model.Sex
import com.m3.core.model.TrainingLocation
import com.m3.core.model.TrainingSetup
import com.m3.core.model.Units
import java.time.DayOfWeek

/** What the owner has typed so far. Text stays text until [toProfile], so half-typed numbers are kept. */
data class ProfileDraft(
    val heightCm: String = "",
    val weightKg: String = "",
    val birthYear: String = "",
    val sex: Sex? = null,
    val goal: Goal? = null,
    val experience: Experience? = null,
    val injuries: String = "",
) {
    data class AboutErrors(
        val heightCm: String? = null,
        val weightKg: String? = null,
        val birthYear: String? = null,
        val sex: String? = null,
    ) {
        val isEmpty get() = heightCm == null && weightKg == null && birthYear == null && sex == null
    }

    fun aboutErrors(currentYear: Int): AboutErrors {
        val height = Units.parseWhole(heightCm)
        val weight = Units.parseDecimal(weightKg)
        val year = Units.parseWhole(birthYear)
        val years = ProfileValidator.birthYearRange(currentYear)
        return AboutErrors(
            heightCm = if (height == null || height !in Limits.MIN_HEIGHT_CM..Limits.MAX_HEIGHT_CM) {
                "Enter your height in ${Units.CM}, ${Limits.MIN_HEIGHT_CM}–${Limits.MAX_HEIGHT_CM}"
            } else {
                null
            },
            weightKg = if (weight == null || weight !in Limits.MIN_WEIGHT_KG..Limits.MAX_WEIGHT_KG) {
                "Enter your weight in ${Units.KG}, ${Units.number(Limits.MIN_WEIGHT_KG)}–${Units.number(Limits.MAX_WEIGHT_KG)}"
            } else {
                null
            },
            birthYear = if (year == null || year !in years) {
                "Enter a year between ${years.first} and ${years.last}"
            } else {
                null
            },
            sex = if (sex == null) "Choose one" else null,
        )
    }

    val injuriesError: String?
        get() = if (injuries.length > Limits.MAX_INJURIES_CHARS) {
            "Keep it under ${Limits.MAX_INJURIES_CHARS} characters"
        } else {
            null
        }

    /** Null until every field is filled in and valid. */
    fun toProfile(currentYear: Int): Profile? {
        if (!aboutErrors(currentYear).isEmpty || injuriesError != null) return null
        return Profile(
            birthYear = Units.parseWhole(birthYear) ?: return null,
            sex = sex ?: return null,
            heightCm = Units.parseWhole(heightCm) ?: return null,
            weightKg = Units.parseDecimal(weightKg) ?: return null,
            goal = goal ?: return null,
            experience = experience ?: return null,
            injuriesText = injuries.trim(),
        )
    }

    companion object {
        fun from(profile: Profile) = ProfileDraft(
            heightCm = profile.heightCm.toString(),
            weightKg = Units.number(profile.weightKg),
            birthYear = profile.birthYear.toString(),
            sex = profile.sex,
            goal = profile.goal,
            experience = profile.experience,
            injuries = profile.injuriesText,
        )
    }
}

data class LocationDraft(
    val id: String,
    val name: String,
    /** Ticked catalog items; the value is the weight list as typed ("" = not specified). */
    val items: Map<EquipmentKind, String> = emptyMap(),
    val custom: List<String> = emptyList(),
) {
    /** Error per ticked item whose weight list can't be read. */
    val weightErrors: Map<EquipmentKind, String>
        get() = items
            .filter { (kind, text) -> kind.hasWeights && Units.parseWeightList(text) == null }
            .mapValues { WEIGHTS_ERROR }

    fun toLocation(): TrainingLocation? {
        if (weightErrors.isNotEmpty()) return null
        val catalog = EquipmentKind.entries.filter { it in items }.map { kind ->
            val weights = if (kind.hasWeights) Units.parseWeightList(items.getValue(kind)).orEmpty() else emptyList()
            EquipmentItem.Catalog(kind, EquipmentDetails(weights))
        }
        return TrainingLocation(id, name.trim(), catalog + custom.map(EquipmentItem::Custom))
    }

    companion object {
        const val WEIGHTS_ERROR = "Use kg numbers, e.g. 2-24 (steps of 2), 5-30/2.5 or 4, 8, 12"

        fun from(location: TrainingLocation) = LocationDraft(
            id = location.id,
            name = location.name,
            items = location.equipment.filterIsInstance<EquipmentItem.Catalog>()
                .associate { it.kind to Units.formatWeightList(it.details.weightsKg) },
            custom = location.equipment.filterIsInstance<EquipmentItem.Custom>().map { it.name },
        )
    }
}

/** Locations, equipment and the weekly schedule, as edited in onboarding and in You → Training setup. */
data class SetupDraft(
    val locations: List<LocationDraft> = emptyList(),
    /** Location id per day; missing = rest. */
    val schedule: Map<DayOfWeek, String> = emptyMap(),
) {
    fun hasLocationNamed(name: String) = locations.any { it.name.trim().equals(name.trim(), ignoreCase = true) }

    /** Null if the name can be added, otherwise why not. */
    fun addLocationError(name: String): String? = when {
        name.isBlank() -> "Give it a name"
        name.trim().length > Limits.MAX_LOCATION_NAME_CHARS -> "Keep it under ${Limits.MAX_LOCATION_NAME_CHARS} characters"
        hasLocationNamed(name) -> "You already have a location with that name"
        locations.size >= Limits.MAX_LOCATIONS -> "Up to ${Limits.MAX_LOCATIONS} locations"
        else -> null
    }

    fun addLocation(name: String, id: String): SetupDraft =
        if (addLocationError(name) != null) this else copy(locations = locations + LocationDraft(id, name.trim()))

    /** Removing a location turns its days into rest days. */
    fun removeLocation(id: String): SetupDraft = copy(
        locations = locations.filterNot { it.id == id },
        schedule = schedule.filterValues { it != id },
    )

    fun updateLocation(id: String, transform: (LocationDraft) -> LocationDraft): SetupDraft =
        copy(locations = locations.map { if (it.id == id) transform(it) else it })

    fun setDay(day: DayOfWeek, locationId: String?): SetupDraft =
        copy(schedule = if (locationId == null) schedule - day else schedule + (day to locationId))

    fun toSetup(): TrainingSetup? {
        val built = locations.map { it.toLocation() ?: return null }
        val ids = built.map { it.id }.toSet()
        return TrainingSetup(built, schedule.filterValues { it in ids })
    }

    companion object {
        fun from(setup: TrainingSetup) = SetupDraft(
            locations = setup.locations.map(LocationDraft::from),
            schedule = setup.schedule.mapNotNull { (day, id) -> id?.let { day to it } }.toMap(),
        )
    }
}

fun LocationDraft.toggle(kind: EquipmentKind, checked: Boolean): LocationDraft =
    copy(items = if (checked) items + (kind to items[kind].orEmpty()) else items - kind)

fun LocationDraft.setWeights(kind: EquipmentKind, text: String): LocationDraft =
    if (kind in items) copy(items = items + (kind to text)) else this

fun LocationDraft.bodyweightOnly(): LocationDraft = copy(items = emptyMap(), custom = emptyList())

fun LocationDraft.selectAll(kinds: List<EquipmentKind>): LocationDraft =
    copy(items = items + kinds.filterNot { it in items }.associateWith { "" })

/** Null if the custom item can be added, otherwise why not. */
fun LocationDraft.addCustomError(name: String): String? = when {
    name.isBlank() -> "Give it a name"
    name.trim().length > Limits.MAX_CUSTOM_NAME_CHARS -> "Keep it under ${Limits.MAX_CUSTOM_NAME_CHARS} characters"
    custom.any { it.equals(name.trim(), ignoreCase = true) } -> "Already on the list"
    custom.size >= Limits.MAX_CUSTOM_ITEMS -> "Up to ${Limits.MAX_CUSTOM_ITEMS} items of your own"
    else -> null
}

fun LocationDraft.addCustom(name: String): LocationDraft =
    if (addCustomError(name) != null) this else copy(custom = custom + name.trim())

fun LocationDraft.removeCustom(name: String): LocationDraft = copy(custom = custom - name)

/** Suggested locations shown as toggles in onboarding (ADR 0008). */
val PresetLocations = listOf("Gym", "Home")
