package com.m3.core.designsystem.config

import kotlinx.serialization.Serializable

/** The whole customizable-UI document (ADR 0007). Everything the owner or an agent can change lives here. */
@Serializable
data class UiConfig(
    val schemaVersion: Int = 1,
    val theme: ThemeTokens = ThemeTokens(),
    val tabs: List<TabId> = listOf(TabId.TODAY, TabId.TRAIN, TabId.FUEL, TabId.YOU, TabId.COACH),
    val todayCards: List<CardPlacement> = listOf(
        CardPlacement(CardType.CHECK_IN),
        CardPlacement(CardType.TODAY_WORKOUT),
        CardPlacement(CardType.STEPS),
        CardPlacement(CardType.FUEL_SUMMARY),
        CardPlacement(CardType.COACH_NOTE),
        CardPlacement(CardType.HABITS),
    ),
    val locks: Set<LockKey> = emptySet(),
) {
    /**
     * Adds default cards a saved config doesn't have yet, each after the card it follows by default. Cards can be
     * hidden but not removed, so a missing one is always newer than the saved config.
     */
    fun withNewDefaultCards(): UiConfig {
        val cards = todayCards.toMutableList()
        UiConfig().todayCards.forEachIndexed { index, default ->
            if (cards.none { it.type == default.type }) {
                val previous = UiConfig().todayCards.getOrNull(index - 1)?.type
                cards.add(cards.indexOfFirst { it.type == previous } + 1, default)
            }
        }
        return copy(todayCards = cards)
    }
}

@Serializable
data class ThemeTokens(
    val mode: ThemeMode = ThemeMode.LIGHT,
    val light: ColorTokens = ColorTokens.DefaultLight,
    val dark: ColorTokens = ColorTokens.DefaultDark,
    val fontPairing: FontPairing = FontPairing.EDITORIAL,
    val typeScale: Float = 1.0f,
    val density: UiDensity = UiDensity.COMFORTABLE,
    val cornerRadiusDp: Int = 0,
) {
    companion object {
        const val MIN_TYPE_SCALE = 0.85f
        const val MAX_TYPE_SCALE = 1.30f
        const val MIN_CORNER_RADIUS_DP = 0
        const val MAX_CORNER_RADIUS_DP = 24
    }
}

/** Colors as "#RRGGBB" strings so the document stays plain data. */
@Serializable
data class ColorTokens(
    val ground: String,
    val surface: String,
    val ink: String,
    val muted: String,
    val rule: String,
    val accent: String,
    val onAccent: String,
) {
    operator fun get(name: ColorTokenName): String = when (name) {
        ColorTokenName.GROUND -> ground
        ColorTokenName.SURFACE -> surface
        ColorTokenName.INK -> ink
        ColorTokenName.MUTED -> muted
        ColorTokenName.RULE -> rule
        ColorTokenName.ACCENT -> accent
        ColorTokenName.ON_ACCENT -> onAccent
    }

    fun with(name: ColorTokenName, hex: String): ColorTokens = when (name) {
        ColorTokenName.GROUND -> copy(ground = hex)
        ColorTokenName.SURFACE -> copy(surface = hex)
        ColorTokenName.INK -> copy(ink = hex)
        ColorTokenName.MUTED -> copy(muted = hex)
        ColorTokenName.RULE -> copy(rule = hex)
        ColorTokenName.ACCENT -> copy(accent = hex)
        ColorTokenName.ON_ACCENT -> copy(onAccent = hex)
    }

    companion object {
        val DefaultLight = ColorTokens(
            ground = "#F6F4EF",
            surface = "#FFFFFF",
            ink = "#121212",
            muted = "#4F4A43",
            rule = "#DED8CD",
            accent = "#A3122A",
            onAccent = "#FFFFFF",
        )
        val DefaultDark = ColorTokens(
            ground = "#121212",
            surface = "#1C1B19",
            ink = "#F6F4EF",
            muted = "#B8B1A6",
            rule = "#3A3733",
            accent = "#E9A5B0",
            onAccent = "#121212",
        )
    }
}

@Serializable
data class CardPlacement(
    val type: CardType,
    val visible: Boolean = true,
    val options: Map<String, String> = emptyMap(),
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class FontPairing { EDITORIAL, CLEAN }

enum class UiDensity { COMPACT, COMFORTABLE, SPACIOUS }

enum class ThemeScheme { LIGHT, DARK }

enum class ColorTokenName { GROUND, SURFACE, INK, MUTED, RULE, ACCENT, ON_ACCENT }

enum class CardType { CHECK_IN, TODAY_WORKOUT, FUEL_SUMMARY, COACH_NOTE, HABITS, WEIGHT_TREND, STEPS }

enum class TabId { TODAY, TRAIN, FUEL, YOU, COACH }

/** Owner locks. Agents can't change a locked area; the owner always can. Training keys are checked by the Coach tools (ADR 0010). */
enum class LockKey { THEME, TABS, TODAY_LAYOUT, TRAINING_PLAN, AVAILABILITY }
