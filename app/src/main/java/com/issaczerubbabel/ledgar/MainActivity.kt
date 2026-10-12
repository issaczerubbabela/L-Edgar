package com.issaczerubbabel.ledgar

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.issaczerubbabel.ledgar.sync.SyncScheduler
import com.issaczerubbabel.ledgar.ui.navigation.AppNavigation
import com.issaczerubbabel.ledgar.ui.theme.SheetSyncTheme
import com.issaczerubbabel.ledgar.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var syncScheduler: SyncScheduler

    /** Set when a launch asks for Add Transaction (the daily reminder's tap); cleared once opened. */
    private var openAddTransaction by mutableStateOf(false)

    /** Opening the app pulls edits made in the Sheet since the last Sync (ADR-0003). */
    override fun onStart() {
        super.onStart()
        syncScheduler.requestFullSync()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) readLaunchRequest(intent)
        enableEdgeToEdge()

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            val viewModel: SettingsViewModel = hiltViewModel()
            val currentTheme by viewModel.themeState.collectAsStateWithLifecycle()

            SheetSyncTheme(themeOption = currentTheme) {
                AppNavigation(
                    openAddTransaction = openAddTransaction,
                    onAddTransactionOpened = { openAddTransaction = false }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readLaunchRequest(intent)
    }

    private fun readLaunchRequest(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_ADD_TRANSACTION, false) == true) openAddTransaction = true
    }

    companion object {
        const val EXTRA_OPEN_ADD_TRANSACTION = "open_add_transaction"
    }
}
