package com.m3.app.ui.nav

import androidx.compose.ui.graphics.vector.ImageVector
import com.m3.app.ui.icons.M3Icons
import com.m3.core.designsystem.config.TabId
import kotlinx.serialization.Serializable

@Serializable
data object TodayRoute

@Serializable
data object TrainRoute

@Serializable
data object FuelRoute

@Serializable
data object YouRoute

@Serializable
data object CoachRoute

val TabId.route: Any
    get() = when (this) {
        TabId.TODAY -> TodayRoute
        TabId.TRAIN -> TrainRoute
        TabId.FUEL -> FuelRoute
        TabId.YOU -> YouRoute
        TabId.COACH -> CoachRoute
    }

val TabId.label: String
    get() = when (this) {
        TabId.TODAY -> "TODAY"
        TabId.TRAIN -> "TRAIN"
        TabId.FUEL -> "FUEL"
        TabId.YOU -> "YOU"
        TabId.COACH -> "COACH"
    }

val TabId.icon: ImageVector
    get() = when (this) {
        TabId.TODAY -> M3Icons.Today
        TabId.TRAIN -> M3Icons.Train
        TabId.FUEL -> M3Icons.Fuel
        TabId.YOU -> M3Icons.You
        TabId.COACH -> M3Icons.Coach
    }

/** Not a tab: opened from You → On-device AI. */
@Serializable
data object AiModelRoute

/** Not a tab: opened from You → Your setup. */
@Serializable
data object ProfileEditRoute

/** Not a tab: opened from You → Your setup. */
@Serializable
data object TrainingSetupRoute
