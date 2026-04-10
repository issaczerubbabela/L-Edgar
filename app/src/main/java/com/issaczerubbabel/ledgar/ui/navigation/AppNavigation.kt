package com.issaczerubbabel.ledgar.ui.navigation

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
import com.issaczerubbabel.ledgar.ui.screens.HistoryScreen
import com.issaczerubbabel.ledgar.ui.screens.InsightsScreen
import com.issaczerubbabel.ledgar.ui.screens.LogScreen
import com.issaczerubbabel.ledgar.ui.screens.AccountDetailScreen
import com.issaczerubbabel.ledgar.ui.screens.AccountsScreen
import com.issaczerubbabel.ledgar.ui.screens.AddAccountScreen
import com.issaczerubbabel.ledgar.ui.screens.BookmarksScreen
import com.issaczerubbabel.ledgar.ui.screens.BudgetSettingScreen
import com.issaczerubbabel.ledgar.ui.screens.DropdownManagementScreen
import com.issaczerubbabel.ledgar.ui.screens.FilterSelectionScreen
import com.issaczerubbabel.ledgar.ui.screens.FilteredTransactionsScreen
import com.issaczerubbabel.ledgar.ui.screens.OverallAccountStatsScreen
import com.issaczerubbabel.ledgar.ui.screens.SearchScreen
import com.issaczerubbabel.ledgar.ui.screens.SettingsScreen
import com.issaczerubbabel.ledgar.ui.screens.AppsScriptSetupScreen
import com.issaczerubbabel.ledgar.ui.screens.ChangelogScreen
import com.issaczerubbabel.ledgar.data.preferences.AppLockAuthMode
import com.issaczerubbabel.ledgar.viewmodel.AppLockViewModel
import com.issaczerubbabel.ledgar.viewmodel.ACCOUNT_ROUTE_ADD
import kotlinx.coroutines.launch

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
    object Changelog : Screen("changelog", "Changelog", Icons.Filled.Settings)
}

private const val LOG_BASE_ROUTE = "log"
private const val FILTERED_BASE_ROUTE = "filtered_transactions"
private const val APP_LOCK_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_WEAK or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

private fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private fun buildSystemAuthIssueMessage(code: Int): Pair<String, String> {
    return when (code) {
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
            "Security Setup Required" to
                "No biometric or device credential is configured. Set up fingerprint/face or a lock screen pattern/PIN/password in system settings."

        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
            "Biometric Hardware Missing" to
                "This device has no biometric hardware. Use a device credential lock (pattern/PIN/password) or switch app lock mode to App PIN."

        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
            "Biometric Temporarily Unavailable" to
                "Biometric hardware is currently unavailable. Try again in a few seconds or use App PIN mode."

        BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
            "Security Update Required" to
                "The device requires a security update before biometric authentication can be used."

        BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED ->
            "Authentication Unsupported" to
                "This device or OEM policy does not support the requested system authentication mode."

        BiometricManager.BIOMETRIC_STATUS_UNKNOWN ->
            "Authentication Status Unknown" to
                "The system could not determine biometric availability. Check lock screen and biometric enrollment in settings."

        else ->
            "Authentication Unavailable" to
                "System biometric/device credential authentication is unavailable (code: $code)."
    }
}

private fun Context.openSystemSecuritySettings() {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
            putExtra(
                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                APP_LOCK_AUTHENTICATORS
            )
        }
    } else {
        Intent(Settings.ACTION_SECURITY_SETTINGS)
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}

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
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val appLockViewModel: AppLockViewModel = androidx.hilt.navigation.compose.hiltViewModel()
    val lockConfig by appLockViewModel.config.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDest = navBackStackEntry?.destination
    var authInProgress by remember { mutableStateOf(false) }
    var showPinUnlockDialog by remember { mutableStateOf(false) }
    var pinInput by remember { mutableStateOf("") }
    var pinErrorMessage by remember { mutableStateOf<String?>(null) }
    var showSystemAuthIssueDialog by remember { mutableStateOf(false) }
    var systemAuthIssueTitle by remember { mutableStateOf("") }
    var systemAuthIssueMessage by remember { mutableStateOf("") }
    val isRootLogScreen =
        currentDest?.route?.startsWith(LOG_BASE_ROUTE) == true && navController.previousBackStackEntry == null

    val showBottomBar =
        currentDest?.route != Screen.FilteredTransactions.route &&
            currentDest?.route != Screen.Log.route

    val navigateToTransactionsAfterUnlock = {
        appLockViewModel.markUnlocked()
        navController.navigate(Screen.Trans.route) {
            popUpTo(LOG_BASE_ROUTE) { inclusive = true }
            launchSingleTop = true
        }
    }

    val launchSystemAuth: () -> Unit = launchSystemAuth@{
        val activity = context.findFragmentActivity() ?: run {
            Toast.makeText(context, "Unable to start authentication", Toast.LENGTH_SHORT).show()
            return@launchSystemAuth
        }

        val biometricManager = BiometricManager.from(activity)
        val authStatus = biometricManager.canAuthenticate(APP_LOCK_AUTHENTICATORS)
        if (authStatus != BiometricManager.BIOMETRIC_SUCCESS) {
            val (title, message) = buildSystemAuthIssueMessage(authStatus)
            systemAuthIssueTitle = title
            systemAuthIssueMessage = message
            showSystemAuthIssueDialog = true
            if (lockConfig.authMode == AppLockAuthMode.SYSTEM_OR_PIN && lockConfig.hasPinConfigured) {
                pinErrorMessage = null
                pinInput = ""
                showPinUnlockDialog = true
            }
            return@launchSystemAuth
        }

        authInProgress = true
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(
                    result: BiometricPrompt.AuthenticationResult
                ) {
                    authInProgress = false
                    navigateToTransactionsAfterUnlock()
                }

                override fun onAuthenticationError(
                    errorCode: Int,
                    errString: CharSequence
                ) {
                    authInProgress = false
                }

                override fun onAuthenticationFailed() {
                    // Keep the prompt open for another attempt.
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock L-Edgar")
            .setSubtitle("Authenticate to open your main screens")
            .setAllowedAuthenticators(APP_LOCK_AUTHENTICATORS)
            .build()

        prompt.authenticate(promptInfo)
    }

    val startUnlockFromLog: () -> Unit = startUnlockFromLog@{
        if (authInProgress) return@startUnlockFromLog

        // Root Log -> main app entry always goes through system credentials.
        launchSystemAuth()
    }

    val enterAppFromLog: () -> Unit = {
        startUnlockFromLog()
    }

    DisposableEffect(lifecycleOwner, lockConfig.enabled, lockConfig.timeoutMinutes) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> appLockViewModel.onAppBackgrounded()
                Lifecycle.Event.ON_START -> appLockViewModel.onAppForegrounded()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(lockConfig.enabled, appLockViewModel.isUnlocked, currentDest?.route) {
        val route = currentDest?.route ?: return@LaunchedEffect
        val isLogRoute = route.startsWith(LOG_BASE_ROUTE)
        if (lockConfig.enabled && !appLockViewModel.isUnlocked && !isLogRoute) {
            navController.navigate(logRoute()) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }

    if (showPinUnlockDialog) {
        AlertDialog(
            onDismissRequest = {
                showPinUnlockDialog = false
                pinInput = ""
                pinErrorMessage = null
            },
            title = { Text("Enter App PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pinInput,
                        onValueChange = { value ->
                            pinInput = value.filter(Char::isDigit).take(8)
                            pinErrorMessage = null
                        },
                        singleLine = true,
                        label = { Text("PIN") },
                        isError = pinErrorMessage != null,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.NumberPassword
                        )
                    )
                    pinErrorMessage?.let { message ->
                        Text(
                            text = message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        val verified = appLockViewModel.verifyPin(pinInput)
                        if (verified) {
                            showPinUnlockDialog = false
                            pinInput = ""
                            pinErrorMessage = null
                            navigateToTransactionsAfterUnlock()
                        } else {
                            pinErrorMessage = "Incorrect PIN"
                        }
                    }
                }) {
                    Text("Unlock")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPinUnlockDialog = false
                    pinInput = ""
                    pinErrorMessage = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showSystemAuthIssueDialog) {
        AlertDialog(
            onDismissRequest = { showSystemAuthIssueDialog = false },
            title = { Text(systemAuthIssueTitle) },
            text = { Text(systemAuthIssueMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showSystemAuthIssueDialog = false
                    context.openSystemSecuritySettings()
                }) {
                    Text("Open Security Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSystemAuthIssueDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = showBottomBar,
                enter = fadeIn(animationSpec = tween(220)) +
                    slideInVertically(
                        animationSpec = tween(220),
                        initialOffsetY = { it / 2 }
                    ),
                exit = fadeOut(animationSpec = tween(180)) +
                    slideOutVertically(
                        animationSpec = tween(180),
                        targetOffsetY = { it / 2 }
                    )
            ) {
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
                                    popUpTo(screen.route) { inclusive = true }
                                    launchSingleTop = true
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
        NavHost(
            navController = navController,
            startDestination = LOG_BASE_ROUTE,
            enterTransition = {
                fadeIn(animationSpec = tween(220)) +
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(220)
                    )
            },
            exitTransition = {
                fadeOut(animationSpec = tween(180)) +
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Left,
                        animationSpec = tween(180)
                    )
            },
            popEnterTransition = {
                fadeIn(animationSpec = tween(180)) +
                    slideIntoContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(180)
                    )
            },
            popExitTransition = {
                fadeOut(animationSpec = tween(160)) +
                    slideOutOfContainer(
                        AnimatedContentTransitionScope.SlideDirection.Right,
                        animationSpec = tween(160)
                    )
            }
        ) {
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
                    showEnterAppButton = isRootLogScreen,
                    onEnterApp = enterAppFromLog,
                    onBack = {
                        if (isRootLogScreen) {
                            startUnlockFromLog()
                            return@LogScreen
                        }

                        if (appLockViewModel.isUnlocked) {
                            val popped = navController.popBackStack()
                            if (!popped && isRootLogScreen) {
                                enterAppFromLog()
                            }
                            return@LogScreen
                        }

                        startUnlockFromLog()
                    },
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
                    },
                    onNavigateToChangelog = {
                        navController.navigate(Screen.Changelog.route) {
                            launchSingleTop = true
                        }
                    }
                )
            }
            composable(Screen.Changelog.route) {
                ChangelogScreen(
                    innerPadding = innerPadding,
                    onBack = { navController.popBackStack() }
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
