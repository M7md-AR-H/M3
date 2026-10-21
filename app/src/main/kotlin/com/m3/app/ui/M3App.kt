package com.m3.app.ui

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.m3.app.feature.aimodel.AiModelScreen
import com.m3.app.feature.coach.CoachScreen
import com.m3.app.feature.fuel.FuelScreen
import com.m3.app.feature.today.TodayScreen
import com.m3.app.feature.train.TrainScreen
import com.m3.app.feature.you.YouScreen
import com.m3.app.ui.nav.AiModelRoute
import com.m3.app.ui.nav.CoachRoute
import com.m3.app.ui.nav.FuelRoute
import com.m3.app.ui.nav.M3BottomBar
import com.m3.app.ui.nav.ProfileEditRoute
import com.m3.app.ui.nav.TrainingSetupRoute
import com.m3.app.feature.onboarding.ProfileEditScreen
import com.m3.app.feature.onboarding.TrainingSetupScreen
import com.m3.app.ui.nav.TodayRoute
import com.m3.app.ui.nav.TrainRoute
import com.m3.app.ui.nav.YouRoute
import com.m3.app.ui.nav.route
import com.m3.core.designsystem.config.UiConfig
import com.m3.core.designsystem.theme.M3
import com.m3.core.designsystem.theme.Motion

@Composable
fun M3App(config: UiConfig) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val destination = backStackEntry?.destination

    Scaffold(
        containerColor = M3.colors.ground,
        bottomBar = {
            M3BottomBar(
                tabs = config.tabs,
                isSelected = { tab -> destination?.hasRoute(tab.route::class) == true },
                onSelect = { tab ->
                    navController.navigate(tab.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = TodayRoute,
            modifier = Modifier.padding(innerPadding),
            enterTransition = { fadeIn(Motion.medium()) },
            exitTransition = { fadeOut(Motion.short()) },
            popEnterTransition = { fadeIn(Motion.medium()) },
            popExitTransition = { fadeOut(Motion.short()) },
        ) {
            composable<TodayRoute> { TodayScreen(cards = config.todayCards) }
            composable<TrainRoute> { TrainScreen() }
            composable<FuelRoute> { FuelScreen() }
            composable<YouRoute> {
                YouScreen(
                    onOpenProfile = { navController.navigate(ProfileEditRoute) },
                    onOpenTrainingSetup = { navController.navigate(TrainingSetupRoute) },
                    onOpenAiModel = { navController.navigate(AiModelRoute) },
                )
            }
            composable<AiModelRoute> { AiModelScreen(onBack = { navController.popBackStack() }) }
            composable<ProfileEditRoute> { ProfileEditScreen(onBack = { navController.popBackStack() }) }
            composable<TrainingSetupRoute> { TrainingSetupScreen(onBack = { navController.popBackStack() }) }
            composable<CoachRoute> { CoachScreen() }
        }
    }
}
