package com.m3.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.m3.core.designsystem.R
import com.m3.core.designsystem.config.FontPairing

@Immutable
data class M3Typography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    /** Uppercase is applied by the Kicker component. */
    val kicker: TextStyle,
)

private val variableWeights = listOf(400, 500, 600, 700)

private val BodoniModa = FontFamily(
    variableWeights.flatMap { weight ->
        listOf(
            Font(
                resId = R.font.bodoni_moda,
                weight = FontWeight(weight),
                style = FontStyle.Normal,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            ),
            Font(
                resId = R.font.bodoni_moda_italic,
                weight = FontWeight(weight),
                style = FontStyle.Italic,
                variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
            ),
        )
    },
)

private val InstrumentSans = FontFamily(
    variableWeights.map { weight ->
        Font(
            resId = R.font.instrument_sans,
            weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)

fun m3Typography(pairing: FontPairing, typeScale: Float): M3Typography {
    val headlineFamily = when (pairing) {
        FontPairing.EDITORIAL -> BodoniModa
        FontPairing.CLEAN -> InstrumentSans
    }

    fun style(family: FontFamily, size: Float, weight: FontWeight, lineHeightFactor: Float) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = (size * typeScale).sp,
        lineHeight = (size * typeScale * lineHeightFactor).sp,
    )

    return M3Typography(
        display = style(headlineFamily, 46f, FontWeight.Bold, 1.05f),
        headline = style(headlineFamily, 32f, FontWeight.Medium, 1.15f),
        title = style(headlineFamily, 22f, FontWeight.SemiBold, 1.25f),
        body = style(InstrumentSans, 15f, FontWeight.Normal, 1.45f),
        bodySmall = style(InstrumentSans, 13f, FontWeight.Normal, 1.4f),
        label = style(InstrumentSans, 11f, FontWeight.Medium, 1.3f),
        kicker = style(InstrumentSans, 11f, FontWeight.SemiBold, 1.3f).copy(letterSpacing = 0.16.em),
    )
}
