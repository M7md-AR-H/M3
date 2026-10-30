package com.m3.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** 24x24 stroke icons. The stroke color is a placeholder: Icon() tints it with the current content color. */
object M3Icons {
    val Today = strokeIcon("Today", "M4 5h16v15H4z", "M4 10h16M9 3v4M15 3v4")
    val Train = strokeIcon("Train", "M6 7v10M3 9.5v5M18 7v10M21 9.5v5M6 12h12")
    val Fuel = strokeIcon(
        "Fuel",
        "M7 3v18M4.5 3v5a2.5 2.5 0 0 0 5 0V3M17 21V3c-2.5 1.5-3.5 4-3.5 7.5S15 14 17 14",
    )
    val You = strokeIcon("You", "M8 8a4 4 0 1 0 8 0a4 4 0 1 0-8 0", "M4 21c1-4 4-6 8-6s7 2 8 6")
    val Coach = strokeIcon("Coach", "M4 5h16v11H10l-6 4z")
    val Send = strokeIcon("Send", "M5 12h14M13 6l6 6l-6 6")
    val Stop = strokeIcon("Stop", "M7 7h10v10H7z")
}

private fun strokeIcon(name: String, vararg pathData: String): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        pathData.forEach { data ->
            addPath(
                pathData = addPathNodes(data),
                fill = null,
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()
