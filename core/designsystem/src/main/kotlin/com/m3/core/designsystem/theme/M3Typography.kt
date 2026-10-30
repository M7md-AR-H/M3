package com.m3.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.m3.core.designsystem.R
import com.m3.core.designsystem.config.FontPairing

@Immutable
data class M3Typography(
    val display: TextStyle,
    val headline: TextStyle,
    val title: TextStyle,
    /** Coach answers and editor's notes, in the headline family at reading size. */
    val serifBody: TextStyle,
    val body: TextStyle,
    val bodySmall: TextStyle,
    val label: TextStyle,
    /** Uppercase is applied by the Kicker component. */
    val kicker: TextStyle,
    /** Large numbers that change: timers, totals. Tabular, so they don't shift as they tick. */
    val figure: TextStyle,
    /** Small numbers that change or line up: loads, reps. Tabular. */
    val numeric: TextStyle,
)

private val variableWeights = listOf(400, 500, 600, 700)

private const val TABULAR = "tnum"
private const val TABULAR_LINING = "lnum, tnum"

/**
 * Bodoni Moda draws its hairlines by optical size, and Compose doesn't set that axis by itself: left alone, big
 * headlines use the coarse small-text cut. One family per size keeps them as fine as the prototype's.
 */
private fun bodoniModa(opticalSize: Float) = FontFamily(
    variableWeights.flatMap { weight ->
        val settings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opticalSize))
        listOf(
            Font(resId = R.font.bodoni_moda, weight = FontWeight(weight), style = FontStyle.Normal, variationSettings = settings),
            Font(resId = R.font.bodoni_moda_italic, weight = FontWeight(weight), style = FontStyle.Italic, variationSettings = settings),
        )
    },
)

private val BodoniDisplay = bodoniModa(opticalSize = 46f)
private val BodoniHeadline = bodoniModa(opticalSize = 32f)
private val BodoniTitle = bodoniModa(opticalSize = 22f)
private val BodoniReading = bodoniModa(opticalSize = 18f)

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
    fun serif(bodoni: FontFamily) = when (pairing) {
        FontPairing.EDITORIAL -> bodoni
        FontPairing.CLEAN -> InstrumentSans
    }

    fun style(family: FontFamily, size: Float, weight: FontWeight, lineHeightFactor: Float) = TextStyle(
        fontFamily = family,
        fontWeight = weight,
        fontSize = (size * typeScale).sp,
        lineHeight = (size * typeScale * lineHeightFactor).sp,
    )

    return M3Typography(
        display = style(serif(BodoniDisplay), 46f, FontWeight.Bold, 1.05f)
            .copy(letterSpacing = (-0.02).em, lineBreak = LineBreak.Heading),
        headline = style(serif(BodoniHeadline), 32f, FontWeight.Medium, 1.12f)
            .copy(letterSpacing = (-0.01).em, lineBreak = LineBreak.Heading),
        title = style(serif(BodoniTitle), 22f, FontWeight.SemiBold, 1.25f).copy(lineBreak = LineBreak.Heading),
        serifBody = style(serif(BodoniReading), 18f, FontWeight.Normal, 1.42f).copy(lineBreak = LineBreak.Paragraph),
        body = style(InstrumentSans, 15f, FontWeight.Normal, 1.5f).copy(lineBreak = LineBreak.Paragraph),
        bodySmall = style(InstrumentSans, 13f, FontWeight.Normal, 1.45f).copy(lineBreak = LineBreak.Paragraph),
        label = style(InstrumentSans, 11f, FontWeight.Medium, 1.3f).copy(fontFeatureSettings = TABULAR),
        kicker = style(InstrumentSans, 11f, FontWeight.SemiBold, 1.3f)
            .copy(letterSpacing = 0.16.em, fontFeatureSettings = TABULAR),
        figure = style(serif(BodoniDisplay), 46f, FontWeight.Medium, 1.05f).copy(fontFeatureSettings = TABULAR_LINING),
        numeric = style(InstrumentSans, 17f, FontWeight.SemiBold, 1.3f).copy(fontFeatureSettings = TABULAR),
    )
}
