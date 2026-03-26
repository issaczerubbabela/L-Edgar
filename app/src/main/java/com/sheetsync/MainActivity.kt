package com.sheetsync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.sheetsync.data.preferences.ThemePreferenceRepository
import com.sheetsync.ui.navigation.AppNavigation
import com.sheetsync.ui.theme.SheetSyncTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var themeRepository: ThemePreferenceRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDark by themeRepository.isDarkTheme.collectAsState(initial = true)
            SheetSyncTheme(isDarkTheme = isDark) {
                AppNavigation()
            }
        }
    }
}
