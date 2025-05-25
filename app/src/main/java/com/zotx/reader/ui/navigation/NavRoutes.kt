package com.zotx.reader.ui.navigation

sealed class NavRoutes(val route: String) {
    object Home : NavRoutes("home")
    object Library : NavRoutes("library")
    object Reader : NavRoutes("reader/{pdfId}") {
        fun createRoute(pdfId: String) = "reader/$pdfId"
    }
    object Settings : NavRoutes("settings")
}
