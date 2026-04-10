package com.issaczerubbabel.ledgar

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import com.issaczerubbabel.ledgar.ui.navigation.AppNavigation
import com.issaczerubbabel.ledgar.ui.theme.SheetSyncTheme
import com.issaczerubbabel.ledgar.viewmodel.SettingsViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContent {
            val viewModel: SettingsViewModel = hiltViewModel()
            val currentTheme by viewModel.themeState.collectAsState()

            SheetSyncTheme(themeOption = currentTheme) {
                AppNavigation()
            }
        }
    }
}
