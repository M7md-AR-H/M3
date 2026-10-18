package com.m3.core.designsystem.config

import kotlinx.serialization.Serializable

/**
 * A single, pure edit to a [UiConfig]. These become the agents' `ui.*` tools later (ADR 0007),
 * so every change is plain serializable data.
 */
@Serializable
sealed class UiChange {
    /** The lock that, when set, stops agents from making this change. Null means it can't be locked. */
    abstract val lockKey: LockKey?

    abstract fun applyTo(config: UiConfig): UiConfig

    /** Short past-tense phrase for the "Coach changed X - Undo" notice, e.g. "switched to dark mode". */
    abstract fun describe(): String

    @Serializable
    data class SetThemeMode(val mode: ThemeMode) : UiChange() {
        override val lockKey get() = LockKey.THEME
        override fun applyTo(config: UiConfig) = config.copy(theme = config.theme.copy(mode = mode))
        override fun describe() = when (mode) {
            ThemeMode.LIGHT -> "switched to light mode"
            ThemeMode.DARK -> "switched to dark mode"
            ThemeMode.SYSTEM -> "set the theme to follow your system"
        }
    }

    @Serializable
    data class SetColorToken(
        val scheme: ThemeScheme,
        val token: ColorTokenName,
        val hex: String,
    ) : UiChange() {
        override val lockKey get() = LockKey.THEME
        override fun applyTo(config: UiConfig): UiConfig {
            val theme = config.theme
            return config.copy(
                theme = when (scheme) {
                    ThemeScheme.LIGHT -> theme.copy(light = theme.light.with(token, hex))
                    ThemeScheme.DARK -> theme.copy(dark = theme.dark.with(token, hex))
                },
            )
        }

        override fun describe() = "changed the ${token.name.lowercase().replace('_', ' ')} color"
    }

    @Serializable
    data class SetFontPairing(val pairing: FontPairing) : UiChange() {
        override val lockKey get() = LockKey.THEME
        override fun applyTo(config: UiConfig) = config.copy(theme = config.theme.copy(fontPairing = pairing))
        override fun describe() = when (pairing) {
            FontPairing.EDITORIAL -> "switched to the Editorial fonts"
            FontPairing.CLEAN -> "switched to the Clean font"
        }
    }

    @Serializable
    data class SetTypeScale(val scale: Float) : UiChange() {
        override val lockKey get() = LockKey.THEME
        override fun applyTo(config: UiConfig) = config.copy(theme = config.theme.copy(typeScale = scale))
        override fun describe() = "changed the text size"
    }

    @Serializable
    data class SetDensity(val density: UiDensity) : UiChange() {
        override val lockKey get() = LockKey.THEME
        override fun applyTo(config: UiConfig) = config.copy(theme = config.theme.copy(density = density))
        override fun describe() = "made the layout ${density.name.lowercase()}"
    }

    @Serializable
    data class SetCornerRadius(val dp: Int) : UiChange() {
        override val lockKey get() = LockKey.THEME
        override fun applyTo(config: UiConfig) = config.copy(theme = config.theme.copy(cornerRadiusDp = dp))
        override fun describe() = "changed the corner radius"
    }

    @Serializable
    data class SetTabOrder(val tabs: List<TabId>) : UiChange() {
        override val lockKey get() = LockKey.TABS
        override fun applyTo(config: UiConfig) = config.copy(tabs = tabs)
        override fun describe() = "reordered the tabs"
    }

    /** Cards listed in [types] move to the front in that order; cards not listed keep their relative order after them. */
    @Serializable
    data class SetCardOrder(val types: List<CardType>) : UiChange() {
        override val lockKey get() = LockKey.TODAY_LAYOUT
        override fun applyTo(config: UiConfig): UiConfig {
            val byType = config.todayCards.associateBy { it.type }
            val listed = types.distinct().mapNotNull { byType[it] }
            val rest = config.todayCards.filter { it !in listed }
            return config.copy(todayCards = listed + rest)
        }

        override fun describe() = types.firstOrNull()
            ?.let { "moved ${it.displayName} to the top" }
            ?: "reordered the Today cards"
    }

    @Serializable
    data class SetCardVisible(val type: CardType, val visible: Boolean) : UiChange() {
        override val lockKey get() = LockKey.TODAY_LAYOUT
        override fun applyTo(config: UiConfig) = config.updateCard(type) { it.copy(visible = visible) }
        override fun describe() = "${if (visible) "showed" else "hid"} the ${type.displayName} card"
    }

    @Serializable
    data class SetCardOption(val type: CardType, val key: String, val value: String) : UiChange() {
        override val lockKey get() = LockKey.TODAY_LAYOUT
        override fun applyTo(config: UiConfig) =
            config.updateCard(type) { it.copy(options = it.options + (key to value)) }

        override fun describe() = "changed a setting on the ${type.displayName} card"
    }

    /** Adds the card at the end, or makes it visible again if it is already on Today. */
    @Serializable
    data class AddCard(val type: CardType) : UiChange() {
        override val lockKey get() = LockKey.TODAY_LAYOUT
        override fun applyTo(config: UiConfig): UiConfig =
            if (config.todayCards.any { it.type == type }) {
                config.updateCard(type) { it.copy(visible = true) }
            } else {
                config.copy(todayCards = config.todayCards + CardPlacement(type))
            }

        override fun describe() = "added the ${type.displayName} card"
    }

    /** Resets look and layout. Locks are the owner's permissions, not appearance, so they are kept. */
    @Serializable
    data object ResetToDefault : UiChange() {
        override val lockKey: LockKey? get() = null
        override fun applyTo(config: UiConfig) = UiConfig(locks = config.locks)
        override fun describe() = "reset everything to default"
    }
}

private fun UiConfig.updateCard(type: CardType, transform: (CardPlacement) -> CardPlacement): UiConfig =
    copy(todayCards = todayCards.map { if (it.type == type) transform(it) else it })

val CardType.displayName: String
    get() = when (this) {
        CardType.CHECK_IN -> "Check-in"
        CardType.TODAY_WORKOUT -> "Workout"
        CardType.FUEL_SUMMARY -> "Fuel"
        CardType.COACH_NOTE -> "Coach note"
        CardType.HABITS -> "Habits"
        CardType.WEIGHT_TREND -> "Weight trend"
    }
