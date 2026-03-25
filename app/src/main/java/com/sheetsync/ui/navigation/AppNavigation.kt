package com.sheetsync.ui.navigation

import androidx.compose.animation.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.filled.Settings
import com.sheetsync.ui.screens.HistoryScreen
import com.sheetsync.ui.screens.InsightsScreen
import com.sheetsync.ui.screens.LogScreen
import com.sheetsync.ui.screens.SettingsScreen

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Log : Screen("log", "Log", Icons.Filled.AddCircle)
    object History : Screen("history", "History", Icons.Filled.History)
    object Insights : Screen("insights", "Insights", Icons.Filled.BarChart)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}

val bottomNavItems = listOf(Screen.Log, Screen.History, Screen.Insights, Screen.Settings)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                bottomNavItems.forEach { screen ->
                    val selected = currentDest?.hierarchy?.any { it.route == screen.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(screen.icon, contentDescription = screen.label) },
                        label = { Text(screen.label) }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Log.route) {
            composable(Screen.Log.route) { LogScreen(innerPadding) }
            composable(Screen.History.route) { HistoryScreen(innerPadding) }
            composable(Screen.Insights.route) { InsightsScreen(innerPadding) }
            composable(Screen.Settings.route) { SettingsScreen(innerPadding) }
        }
    }
}
