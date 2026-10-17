package com.godraw.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.godraw.app.AppContainer
import com.godraw.app.ui.about.AboutScreen
import com.godraw.app.ui.editor.EditorScreen
import com.godraw.app.ui.home.HomeScreen
import com.godraw.app.ui.settings.SettingsScreen
import com.godraw.app.ui.subscription.SubscriptionScreen
import com.godraw.app.ui.tutorials.TutorialsScreen

object Routes {
    const val HOME = "home"
    const val EDITOR = "editor/{projectId}"
    const val SETTINGS = "settings"
    const val SUBSCRIPTION = "subscription"
    const val TUTORIALS = "tutorials"
    const val ABOUT = "about"

    fun editor(projectId: String) = "editor/$projectId"
}

@Composable
fun GoDrawNavHost(container: AppContainer) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onOpenProject = { id -> nav.navigate(Routes.editor(id)) { launchSingleTop = true } },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenSubscription = { nav.navigate(Routes.SUBSCRIPTION) },
                onOpenTutorials = { nav.navigate(Routes.TUTORIALS) },
            )
        }
        composable(Routes.EDITOR, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { entry ->
            val id = entry.arguments?.getString("projectId").orEmpty()
            EditorScreen(
                container = container,
                projectId = id,
                onClose = { nav.popBackStack() },
                onOpenSubscription = { nav.navigate(Routes.SUBSCRIPTION) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onBack = { nav.popBackStack() },
                onOpenSubscription = { nav.navigate(Routes.SUBSCRIPTION) },
                onOpenAbout = { nav.navigate(Routes.ABOUT) },
            )
        }
        composable(Routes.SUBSCRIPTION) { SubscriptionScreen(container, onBack = { nav.popBackStack() }) }
        composable(Routes.TUTORIALS) { TutorialsScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.ABOUT) { AboutScreen(onBack = { nav.popBackStack() }) }
    }
}
