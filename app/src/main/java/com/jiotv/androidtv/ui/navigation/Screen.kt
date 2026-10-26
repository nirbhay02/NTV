package com.jiotv.androidtv.ui.navigation

sealed class Screen(val route: String) {
    object Splash : Screen("splash")
    object Dashboard : Screen("dashboard")
    object ZeeChannels : Screen("zee_channels")
    object Search : Screen("search")
    object Settings : Screen("settings")
    object Player : Screen("player/{channelId}?categoryId={categoryId}") {
        fun createRoute(channelId: String, categoryId: String? = null): String {
            return if (!categoryId.isNullOrEmpty()) {
                "player/$channelId?categoryId=$categoryId"
            } else {
                "player/$channelId"
            }
        }
    }
}
