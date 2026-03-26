package com.sheetsync.ui.navigation

import androidx.compose.animation.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.Color
import com.sheetsync.ui.screens.HistoryScreen
import com.sheetsync.ui.screens.InsightsScreen
import com.sheetsync.ui.screens.LogScreen
import com.sheetsync.ui.screens.BudgetSettingScreen
import com.sheetsync.ui.screens.SettingsScreen
import com.sheetsync.ui.theme.FabRed

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Log : Screen("log", "Log", Icons.Filled.AddCircle)
    object Trans : Screen("trans", "Trans.", Icons.Filled.MenuBook)
    object Stats : Screen("stats", "Stats", Icons.Filled.BarChart)
    object Accounts : Screen("accounts", "Accounts", Icons.Filled.Paid)
    object More : Screen("more", "More", Icons.Filled.MoreHoriz)
    object BudgetSetting : Screen("budget_setting", "BudgetSetting", Icons.Filled.Settings)
}

val bottomNavItems = listOf(Screen.Trans, Screen.Stats, Screen.Accounts, Screen.More)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF101114),
                tonalElevation = 0.dp
            ) {
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
                        label = { Text(screen.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = FabRed,
                            selectedTextColor = FabRed,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = Color(0xFF26292F)
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Trans.route) {
            composable(Screen.Log.route) {
                LogScreen(
                    innerPadding = innerPadding,
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(Screen.Trans.route) {
                HistoryScreen(
                    navInsets = innerPadding,
                    onNavigateToLog = {
                        navController.navigate(Screen.Log.route) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    },
                    onNavigateToBudgetSetting = {
                        navController.navigate(Screen.BudgetSetting.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Stats.route) { InsightsScreen(innerPadding) }
            composable(Screen.Accounts.route) { SettingsScreen(innerPadding) }
            composable(Screen.More.route) { SettingsScreen(innerPadding) }
            composable(Screen.BudgetSetting.route) {
                BudgetSettingScreen(
                    innerPadding = innerPadding,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
