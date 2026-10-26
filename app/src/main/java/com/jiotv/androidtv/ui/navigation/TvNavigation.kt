package com.jiotv.androidtv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.jiotv.androidtv.ui.screens.MainDashboardScreen
import com.jiotv.androidtv.ui.screens.PlayerScreen
import com.jiotv.androidtv.ui.screens.SearchScreen
import com.jiotv.androidtv.ui.screens.SettingsScreen
import com.jiotv.androidtv.ui.screens.SplashScreen
import com.jiotv.androidtv.ui.screens.ZeeChannelsScreen
import com.jiotv.androidtv.ui.viewmodel.MainViewModel
import com.jiotv.androidtv.ui.viewmodel.PlayerViewModel
import com.jiotv.androidtv.ui.viewmodel.SettingsViewModel

@UnstableApi
@Composable
fun TvNavigation(
    navController: NavHostController,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                viewModel = mainViewModel,
                onSplashFinished = {
                    navController.navigate(Screen.Dashboard.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Dashboard.route) {
            MainDashboardScreen(
                viewModel = mainViewModel,
                onNavigate = { route ->
                    if (route == Screen.Dashboard.route) {
                        navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                    } else {
                        navController.navigate(route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                        }
                    }
                },
                onChannelSelected = { channel ->
                    mainViewModel.setLastSelectedChannel(channel)
                    navController.navigate(Screen.Player.createRoute(channel.id, mainViewModel.selectedCategoryId.value))
                }
            )
        }

        composable(Screen.ZeeChannels.route) {
            ZeeChannelsScreen(
                viewModel = mainViewModel,
                onNavigate = { route ->
                    if (route == Screen.Dashboard.route) {
                        navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                    } else {
                        navController.navigate(route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                        }
                    }
                },
                onChannelSelected = { channel ->
                    mainViewModel.setLastSelectedChannel(channel)
                    navController.navigate(Screen.Player.createRoute(channel.id, "zee_network"))
                }
            )
        }

        composable(Screen.Search.route) {
            SearchScreen(
                viewModel = mainViewModel,
                onNavigate = { route ->
                    if (route == Screen.Dashboard.route) {
                        navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                    } else {
                        navController.navigate(route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                        }
                    }
                },
                onChannelSelected = { channel ->
                    mainViewModel.setLastSelectedChannel(channel)
                    navController.navigate(Screen.Player.createRoute(channel.id))
                }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigate = { route ->
                    if (route == Screen.Dashboard.route) {
                        navController.popBackStack(Screen.Dashboard.route, inclusive = false)
                    } else {
                        navController.navigate(route) {
                            popUpTo(Screen.Dashboard.route) { saveState = true }
                            launchSingleTop = true
                        }
                    }
                }
            )
        }

        composable(
            route = Screen.Player.route,
            arguments = listOf(
                navArgument("channelId") { type = NavType.StringType },
                navArgument("categoryId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId").orEmpty()
            val categoryId = backStackEntry.arguments?.getString("categoryId")
            PlayerScreen(
                channelId = channelId,
                categoryId = categoryId,
                viewModel = playerViewModel,
                onNavigateBack = {
                    playerViewModel.playbackState.value.currentChannel?.let { current ->
                        mainViewModel.setLastSelectedChannel(current)
                    }
                    navController.popBackStack()
                }
            )
        }
    }
}
