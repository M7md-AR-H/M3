package com.m3.core.designsystem.config

enum class Specialist { COACH, TRAINER, NUTRITIONIST, ANALYST }

/** Who is asking for a UI change. */
sealed class Actor {
    data object Owner : Actor()
    data class Agent(val specialist: Specialist) : Actor()
}

object Permissions {
    fun canApply(actor: Actor, change: UiChange, config: UiConfig): Boolean = when (actor) {
        Actor.Owner -> true
        is Actor.Agent -> when {
            change is UiChange.ResetToDefault -> false
            change.lockKey in config.locks -> false
            else -> specialistMayApply(actor.specialist, change)
        }
    }

    private fun specialistMayApply(specialist: Specialist, change: UiChange): Boolean {
        val ownCard = when (specialist) {
            Specialist.COACH -> return true
            Specialist.TRAINER -> CardType.TODAY_WORKOUT
            Specialist.NUTRITIONIST -> CardType.FUEL_SUMMARY
            Specialist.ANALYST -> CardType.WEIGHT_TREND
        }
        return when (change) {
            is UiChange.SetCardVisible -> change.type == ownCard
            is UiChange.SetCardOption -> change.type == ownCard
            is UiChange.AddCard -> change.type == ownCard
            else -> false
        }
    }
}
