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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import com.sheetsync.ui.screens.HistoryScreen
import com.sheetsync.ui.screens.InsightsScreen
import com.sheetsync.ui.screens.LogScreen
import com.sheetsync.ui.screens.AccountDetailScreen
import com.sheetsync.ui.screens.AccountsScreen
import com.sheetsync.ui.screens.AddAccountScreen
import com.sheetsync.ui.screens.BookmarksScreen
import com.sheetsync.ui.screens.BudgetSettingScreen
import com.sheetsync.ui.screens.DropdownManagementScreen
import com.sheetsync.ui.screens.FilterSelectionScreen
import com.sheetsync.ui.screens.FilteredTransactionsScreen
import com.sheetsync.ui.screens.OverallAccountStatsScreen
import com.sheetsync.ui.screens.SearchScreen
import com.sheetsync.ui.screens.SettingsScreen
import com.sheetsync.ui.screens.AppsScriptSetupScreen
import com.sheetsync.viewmodel.ACCOUNT_ROUTE_ADD

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Log : Screen(
        "log?transactionId={transactionId}&copyTransactionId={copyTransactionId}&copyDateMode={copyDateMode}",
        "Log",
        Icons.Filled.AddCircle
    )
    object Trans : Screen("trans", "Trans.", Icons.Filled.MenuBook)
    object Search : Screen("search", "Search", Icons.Filled.MoreHoriz)
    object FilterSelection : Screen("filter_selection", "Filter", Icons.Filled.Tune)
    object FilteredTransactions : Screen(
        "filtered_transactions?year={year}&month={month}&incomeIds={incomeIds}&expenseIds={expenseIds}&accountIds={accountIds}",
        "Filtered",
        Icons.Filled.Tune
    )
    object Bookmarks : Screen("bookmarks", "Bookmarks", Icons.Filled.Star)
    object Stats : Screen("stats", "Stats", Icons.Filled.BarChart)
    object Accounts : Screen("accounts", "Accounts", Icons.Filled.Paid)
    object AccountDetail : Screen("account_detail/{accountId}", "AccountDetail", Icons.Filled.Paid)
    object OverallAccountStats : Screen("overall_account_stats", "OverallAccountStats", Icons.Filled.BarChart)
    object AddAccount : Screen(ACCOUNT_ROUTE_ADD, "AddAccount", Icons.Filled.Paid)
    object More : Screen("more", "More", Icons.Filled.MoreHoriz)
    object BudgetSetting : Screen("budget_setting", "BudgetSetting", Icons.Filled.Settings)
    object DropdownManagement : Screen("dropdown_management", "DropdownManagement", Icons.Filled.Settings)
    object AppsScriptSetup : Screen("apps_script_setup", "AppsScriptSetup", Icons.Filled.Settings)
}

private const val LOG_BASE_ROUTE = "log"
private const val FILTERED_BASE_ROUTE = "filtered_transactions"

private fun logRoute(
    transactionId: Long? = null,
    copyTransactionId: Long? = null,
    useTodayDateForCopy: Boolean = false
): String {
    val params = mutableListOf<String>()
    transactionId?.let { params += "transactionId=$it" }
    copyTransactionId?.let {
        params += "copyTransactionId=$it"
        params += "copyDateMode=${if (useTodayDateForCopy) "today" else "original"}"
    }
    return if (params.isEmpty()) LOG_BASE_ROUTE else "$LOG_BASE_ROUTE?${params.joinToString("&")}" 
}

private fun filteredTransactionsRoute(
    year: Int,
    month: Int,
    incomeIds: Set<Long>,
    expenseIds: Set<Long>,
    accountIds: Set<Long>
): String {
    val income = incomeIds.sorted().joinToString(",")
    val expense = expenseIds.sorted().joinToString(",")
    val accounts = accountIds.sorted().joinToString(",")
    return "$FILTERED_BASE_ROUTE?year=$year&month=$month&incomeIds=$income&expenseIds=$expense&accountIds=$accounts"
}

val bottomNavItems = listOf(Screen.Trans, Screen.Stats, Screen.Accounts, Screen.More)

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    val showBottomBar = currentDest?.route != Screen.FilteredTransactions.route

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
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
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Trans.route) {
            composable(
                route = Screen.Log.route,
                arguments = listOf(
                    navArgument("transactionId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("copyTransactionId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("copyDateMode") {
                        type = NavType.StringType
                        defaultValue = "original"
                    }
                )
            ) {
                LogScreen(
                    innerPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(Screen.Trans.route) {
                HistoryScreen(
                    navInsets = innerPadding,
                    onNavigateToLog = {
                        navController.navigate(logRoute()) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true; restoreState = true
                        }
                    },
                    onNavigateToEditTransaction = { transactionId ->
                        navController.navigate(logRoute(transactionId)) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToCopyTransaction = { transactionId, useToday ->
                        navController.navigate(
                            logRoute(copyTransactionId = transactionId, useTodayDateForCopy = useToday)
                        ) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToBookmarks = {
                        navController.navigate(Screen.Bookmarks.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToSearch = {
                        navController.navigate(Screen.Search.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToFilterSelection = {
                        navController.navigate(Screen.FilterSelection.route) {
                            launchSingleTop = true
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
            composable(Screen.Accounts.route) {
                AccountsScreen(
                    innerPadding = innerPadding,
                    onOpenAccountDetail = { accountId ->
                        navController.navigate("account_detail/$accountId")
                    },
                    onOpenOverallStats = {
                        navController.navigate(Screen.OverallAccountStats.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Screen.AddAccount.route) {
                AddAccountScreen(
                    innerPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() }
                )
            }
            composable(Screen.More.route) {
                SettingsScreen(
                    innerPadding = innerPadding,
                    onNavigateToDropdownManagement = {
                        navController.navigate(Screen.DropdownManagement.route) {
                            launchSingleTop = true
                        }
                    },
                    onNavigateToAppsScriptSetup = {
                        navController.navigate(Screen.AppsScriptSetup.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.BudgetSetting.route) {
                BudgetSettingScreen(
                    innerPadding = innerPadding,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.DropdownManagement.route) {
                DropdownManagementScreen(
                    innerPadding = innerPadding,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.AppsScriptSetup.route) {
                AppsScriptSetupScreen(
                    innerPadding = innerPadding,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Bookmarks.route) {
                BookmarksScreen(
                    innerPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    onTransactionClick = { transactionId ->
                        navController.navigate(logRoute(transactionId = transactionId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Search.route) {
                SearchScreen(
                    innerPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    onTransactionClick = { transactionId ->
                        navController.navigate(logRoute(transactionId = transactionId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.FilterSelection.route) {
                FilterSelectionScreen(
                    innerPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                    onApplyFilters = { year, month, incomeIds, expenseIds, accountIds ->
                        navController.navigate(
                            filteredTransactionsRoute(
                                year = year,
                                month = month,
                                incomeIds = incomeIds,
                                expenseIds = expenseIds,
                                accountIds = accountIds
                            )
                        ) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(
                route = Screen.FilteredTransactions.route,
                arguments = listOf(
                    navArgument("year") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("month") { type = NavType.IntType; defaultValue = 0 },
                    navArgument("incomeIds") { type = NavType.StringType; defaultValue = "" },
                    navArgument("expenseIds") { type = NavType.StringType; defaultValue = "" },
                    navArgument("accountIds") { type = NavType.StringType; defaultValue = "" }
                )
            ) {
                FilteredTransactionsScreen(
                    navInsets = innerPadding,
                    onBack = { navController.popBackStack() },
                    onNavigateToEditTransaction = { transactionId ->
                        navController.navigate(logRoute(transactionId = transactionId)) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable("account_detail/{accountId}") {
                AccountDetailScreen(
                    innerPadding = innerPadding,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.OverallAccountStats.route) {
                OverallAccountStatsScreen(
                    innerPadding = innerPadding,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}
