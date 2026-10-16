package com.m3.core.designsystem.config

import org.junit.Assert.assertTrue
import org.junit.Test

class UiConfigValidatorTest {
    private val default = UiConfig()

    @Test
    fun defaultConfigIsValid() {
        assertTrue(UiConfigValidator.validate(default).isEmpty())
    }

    @Test
    fun lowContrastAccentIsRejected() {
        val config = default.copy(
            theme = default.theme.copy(light = default.theme.light.copy(accent = "#EEEEEE")),
        )
        val errors = UiConfigValidator.validate(config)
        assertTrue(errors.any { it is UiConfigError.LowContrast })
    }

    @Test
    fun invalidColorIsRejected() {
        val config = default.copy(
            theme = default.theme.copy(dark = default.theme.dark.copy(ink = "white")),
        )
        assertTrue(UiConfigValidator.validate(config).any { it is UiConfigError.InvalidColor })
    }

    @Test
    fun missingCoachTabIsRejected() {
        val config = default.copy(tabs = listOf(TabId.TODAY, TabId.TRAIN, TabId.FUEL, TabId.YOU))
        val errors = UiConfigValidator.validate(config)
        assertTrue(errors.any { it is UiConfigError.MissingRequiredTab })
    }

    @Test
    fun duplicateCardIsRejected() {
        val config = default.copy(todayCards = default.todayCards + CardPlacement(CardType.HABITS))
        assertTrue(UiConfigValidator.validate(config).any { it is UiConfigError.DuplicateCard })
    }

    @Test
    fun allCardsHiddenIsRejected() {
        val config = default.copy(todayCards = default.todayCards.map { it.copy(visible = false) })
        assertTrue(UiConfigValidator.validate(config).any { it is UiConfigError.NoVisibleCard })
    }

    @Test
    fun typeScaleOutOfRangeIsRejected() {
        val config = default.copy(theme = default.theme.copy(typeScale = 2.0f))
        assertTrue(UiConfigValidator.validate(config).any { it is UiConfigError.TypeScaleOutOfRange })
    }
}
