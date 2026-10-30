package com.m3.app.feature.fuel

import androidx.compose.runtime.Composable
import com.m3.app.ui.ComingSoonScreen

@Composable
fun FuelScreen() {
    ComingSoonScreen(
        title = "Fuel",
        lede = "Eat for the plan you're on.",
        items = listOf(
            "Daily targets" to "Calories and macros from your body and your weekly trend.",
            "Meal photos" to "Point the camera at a plate for an estimate and an honest critique.",
            "Barcodes" to "Exact numbers for anything packaged.",
            "Fridge mode" to "Meal ideas from what you have that fit what's left today.",
        ),
    )
}
