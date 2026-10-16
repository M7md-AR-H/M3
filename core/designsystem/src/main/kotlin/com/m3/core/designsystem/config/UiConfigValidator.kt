package com.m3.core.designsystem.config

sealed class UiConfigError(val message: String) {
    class InvalidColor(scheme: ThemeScheme, token: ColorTokenName, value: String) :
        UiConfigError("${scheme.label} ${token.label} color \"$value\" is not a valid #RRGGBB value")

    class LowContrast(
        scheme: ThemeScheme,
        foreground: ColorTokenName,
        background: ColorTokenName,
        ratio: Double,
    ) : UiConfigError(
        "${scheme.label} ${foreground.label} on ${background.label} has contrast " +
            "${String.format("%.1f", ratio)}:1, needs at least ${UiConfigValidator.MIN_CONTRAST}:1",
    )

    class TypeScaleOutOfRange(value: Float) :
        UiConfigError("Text size $value is outside ${ThemeTokens.MIN_TYPE_SCALE}..${ThemeTokens.MAX_TYPE_SCALE}")

    class CornerRadiusOutOfRange(value: Int) :
        UiConfigError(
            "Corner radius $value is outside " +
                "${ThemeTokens.MIN_CORNER_RADIUS_DP}..${ThemeTokens.MAX_CORNER_RADIUS_DP}",
        )

    class TabCountOutOfRange(count: Int) :
        UiConfigError("Tabs must number ${UiConfigValidator.MIN_TABS}..${UiConfigValidator.MAX_TABS}, got $count")

    class DuplicateTab(tab: TabId) : UiConfigError("Tab ${tab.name} appears more than once")

    class MissingRequiredTab(tab: TabId) : UiConfigError("Tab ${tab.name} is required")

    class DuplicateCard(type: CardType) : UiConfigError("Card ${type.name} appears more than once")

    class NoVisibleCard : UiConfigError("At least one Today card must be visible")
}

object UiConfigValidator {
    const val MIN_CONTRAST = 4.5
    const val MIN_TABS = 3
    const val MAX_TABS = 5

    private val requiredTabs = listOf(TabId.TODAY, TabId.COACH)

    private val contrastPairs = listOf(
        ColorTokenName.INK to ColorTokenName.GROUND,
        ColorTokenName.MUTED to ColorTokenName.GROUND,
        ColorTokenName.INK to ColorTokenName.SURFACE,
        ColorTokenName.ON_ACCENT to ColorTokenName.ACCENT,
    )

    /** Returns every problem found; an empty list means the config is valid. */
    fun validate(config: UiConfig): List<UiConfigError> = buildList {
        addAll(validateColors(ThemeScheme.LIGHT, config.theme.light))
        addAll(validateColors(ThemeScheme.DARK, config.theme.dark))
        if (config.theme.typeScale !in ThemeTokens.MIN_TYPE_SCALE..ThemeTokens.MAX_TYPE_SCALE) {
            add(UiConfigError.TypeScaleOutOfRange(config.theme.typeScale))
        }
        if (config.theme.cornerRadiusDp !in ThemeTokens.MIN_CORNER_RADIUS_DP..ThemeTokens.MAX_CORNER_RADIUS_DP) {
            add(UiConfigError.CornerRadiusOutOfRange(config.theme.cornerRadiusDp))
        }
        addAll(validateTabs(config.tabs))
        addAll(validateCards(config.todayCards))
    }

    private fun validateColors(scheme: ThemeScheme, colors: ColorTokens): List<UiConfigError> = buildList {
        for (token in ColorTokenName.entries) {
            if (Contrast.parseHex(colors[token]) == null) add(UiConfigError.InvalidColor(scheme, token, colors[token]))
        }
        for ((foreground, background) in contrastPairs) {
            val ratio = Contrast.ratio(colors[foreground], colors[background]) ?: continue
            if (ratio < MIN_CONTRAST) add(UiConfigError.LowContrast(scheme, foreground, background, ratio))
        }
    }

    private fun validateTabs(tabs: List<TabId>): List<UiConfigError> = buildList {
        if (tabs.size !in MIN_TABS..MAX_TABS) add(UiConfigError.TabCountOutOfRange(tabs.size))
        tabs.groupBy { it }.filterValues { it.size > 1 }.keys.forEach { add(UiConfigError.DuplicateTab(it)) }
        requiredTabs.filter { it !in tabs }.forEach { add(UiConfigError.MissingRequiredTab(it)) }
    }

    private fun validateCards(cards: List<CardPlacement>): List<UiConfigError> = buildList {
        cards.groupBy { it.type }.filterValues { it.size > 1 }.keys.forEach { add(UiConfigError.DuplicateCard(it)) }
        if (cards.none { it.visible }) add(UiConfigError.NoVisibleCard())
    }
}

private val ThemeScheme.label: String
    get() = when (this) {
        ThemeScheme.LIGHT -> "Light"
        ThemeScheme.DARK -> "Dark"
    }

private val ColorTokenName.label: String
    get() = when (this) {
        ColorTokenName.GROUND -> "background"
        ColorTokenName.SURFACE -> "surface"
        ColorTokenName.INK -> "text"
        ColorTokenName.MUTED -> "secondary text"
        ColorTokenName.RULE -> "divider"
        ColorTokenName.ACCENT -> "accent"
        ColorTokenName.ON_ACCENT -> "text on accent"
    }
