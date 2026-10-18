package com.m3.core.model

import kotlinx.serialization.Serializable

/** How equipment is grouped in tick-lists (ADR 0008). */
enum class EquipmentGroup(val displayName: String) {
    FREE_WEIGHTS("Free weights"),
    STATIONS("Stations & machines"),
    CARDIO("Cardio"),
    SMALL_GEAR("Small gear"),
}

/**
 * The fixed equipment catalog (ADR 0008). Stored by [name], so never rename an entry;
 * add new ones instead.
 *
 * Bodyweight is not an entry: every location supports bodyweight exercises, and
 * "Bodyweight only" simply means an empty inventory.
 */
enum class EquipmentKind(
    val group: EquipmentGroup,
    val displayName: String,
    /** True if the owner can list the exact weights available (in kg). */
    val hasWeights: Boolean = false,
) {
    BARBELL(EquipmentGroup.FREE_WEIGHTS, "Barbell"),
    WEIGHT_PLATES(EquipmentGroup.FREE_WEIGHTS, "Weight plates"),
    DUMBBELLS(EquipmentGroup.FREE_WEIGHTS, "Dumbbells", hasWeights = true),
    KETTLEBELLS(EquipmentGroup.FREE_WEIGHTS, "Kettlebells", hasWeights = true),
    EZ_BAR(EquipmentGroup.FREE_WEIGHTS, "EZ curl bar"),
    TRAP_BAR(EquipmentGroup.FREE_WEIGHTS, "Trap bar"),
    MEDICINE_BALL(EquipmentGroup.FREE_WEIGHTS, "Medicine ball"),
    SANDBAG(EquipmentGroup.FREE_WEIGHTS, "Sandbag"),
    WEIGHTED_VEST(EquipmentGroup.FREE_WEIGHTS, "Weighted vest"),

    FLAT_BENCH(EquipmentGroup.STATIONS, "Flat bench"),
    ADJUSTABLE_BENCH(EquipmentGroup.STATIONS, "Adjustable bench"),
    SQUAT_RACK(EquipmentGroup.STATIONS, "Squat rack / power rack"),
    SMITH_MACHINE(EquipmentGroup.STATIONS, "Smith machine"),
    PULL_UP_BAR(EquipmentGroup.STATIONS, "Pull-up bar"),
    DIP_BARS(EquipmentGroup.STATIONS, "Dip bars"),
    CABLE_STATION(EquipmentGroup.STATIONS, "Cable station"),
    LAT_PULLDOWN(EquipmentGroup.STATIONS, "Lat pulldown"),
    SEATED_ROW(EquipmentGroup.STATIONS, "Seated row machine"),
    CHEST_PRESS(EquipmentGroup.STATIONS, "Chest press machine"),
    SHOULDER_PRESS(EquipmentGroup.STATIONS, "Shoulder press machine"),
    PEC_DECK(EquipmentGroup.STATIONS, "Pec deck / fly machine"),
    LEG_PRESS(EquipmentGroup.STATIONS, "Leg press"),
    HACK_SQUAT(EquipmentGroup.STATIONS, "Hack squat"),
    LEG_EXTENSION(EquipmentGroup.STATIONS, "Leg extension"),
    LEG_CURL(EquipmentGroup.STATIONS, "Leg curl"),
    HIP_THRUST(EquipmentGroup.STATIONS, "Hip thrust / glute machine"),
    CALF_RAISE(EquipmentGroup.STATIONS, "Calf raise machine"),
    ASSISTED_PULL_UP(EquipmentGroup.STATIONS, "Assisted pull-up / dip machine"),
    LANDMINE(EquipmentGroup.STATIONS, "Landmine"),

    TREADMILL(EquipmentGroup.CARDIO, "Treadmill"),
    EXERCISE_BIKE(EquipmentGroup.CARDIO, "Exercise bike"),
    AIR_BIKE(EquipmentGroup.CARDIO, "Air bike"),
    ROWING_MACHINE(EquipmentGroup.CARDIO, "Rowing machine"),
    ELLIPTICAL(EquipmentGroup.CARDIO, "Elliptical"),
    STAIR_CLIMBER(EquipmentGroup.CARDIO, "Stair climber"),
    SKI_ERG(EquipmentGroup.CARDIO, "Ski erg"),

    RESISTANCE_BANDS(EquipmentGroup.SMALL_GEAR, "Resistance bands"),
    MINI_BANDS(EquipmentGroup.SMALL_GEAR, "Mini loop bands"),
    SUSPENSION_TRAINER(EquipmentGroup.SMALL_GEAR, "Suspension trainer (TRX)"),
    GYMNASTIC_RINGS(EquipmentGroup.SMALL_GEAR, "Gymnastic rings"),
    PARALLETTES(EquipmentGroup.SMALL_GEAR, "Parallettes"),
    AB_WHEEL(EquipmentGroup.SMALL_GEAR, "Ab wheel"),
    JUMP_ROPE(EquipmentGroup.SMALL_GEAR, "Jump rope"),
    PLYO_BOX(EquipmentGroup.SMALL_GEAR, "Plyo box / step"),
    STABILITY_BALL(EquipmentGroup.SMALL_GEAR, "Stability ball"),
    FOAM_ROLLER(EquipmentGroup.SMALL_GEAR, "Foam roller"),
    EXERCISE_MAT(EquipmentGroup.SMALL_GEAR, "Exercise mat"),
    BATTLE_ROPES(EquipmentGroup.SMALL_GEAR, "Battle ropes"),
    SLED(EquipmentGroup.SMALL_GEAR, "Sled"),
    ;

    companion object {
        fun byGroup(group: EquipmentGroup): List<EquipmentKind> = entries.filter { it.group == group }

        /** Null for names this version doesn't know (e.g. data written by a newer build). */
        fun fromNameOrNull(name: String): EquipmentKind? = entries.firstOrNull { it.name == name }
    }
}

/** Optional details for one item. Only kinds with [EquipmentKind.hasWeights] use [weightsKg]. */
@Serializable
data class EquipmentDetails(
    /** Available weights in kg, ascending, without duplicates. Empty = not specified. */
    val weightsKg: List<Double> = emptyList(),
)

/** One thing available at a location: a catalog [kind], or a custom item the owner named. */
sealed interface EquipmentItem {
    data class Catalog(val kind: EquipmentKind, val details: EquipmentDetails = EquipmentDetails()) : EquipmentItem

    /** Treated as "unknown" by the training engine until mapped to a kind (ADR 0008). */
    data class Custom(val name: String) : EquipmentItem
}
