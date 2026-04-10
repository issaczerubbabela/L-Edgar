package com.issaczerubbabel.ledgar.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.preferences.AppLockAuthMode
import com.issaczerubbabel.ledgar.data.preferences.ThemePreferenceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AppLockConfig(
    val enabled: Boolean = false,
    val authMode: AppLockAuthMode = AppLockAuthMode.SYSTEM,
    val timeoutMinutes: Int = 5,
    val hasPinConfigured: Boolean = false
)

@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val preferenceRepository: ThemePreferenceRepository
) : ViewModel() {

    val config: StateFlow<AppLockConfig> = combine(
        preferenceRepository.appLockEnabled,
        preferenceRepository.appLockAuthMode,
        preferenceRepository.appLockTimeoutMinutes,
        preferenceRepository.hasAppPinConfigured
    ) { enabled, authMode, timeoutMinutes, hasPinConfigured ->
        AppLockConfig(
            enabled = enabled,
            authMode = authMode,
            timeoutMinutes = timeoutMinutes,
            hasPinConfigured = hasPinConfigured
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLockConfig())

    var isUnlocked by mutableStateOf(true)
        private set

    private var lastBackgroundedAtMillis: Long? = null

    init {
        viewModelScope.launch {
            var previousEnabled: Boolean? = null
            preferenceRepository.appLockEnabled
                .distinctUntilChanged()
                .collect { enabled ->
                if (previousEnabled == null) {
                    isUnlocked = !enabled
                } else if (previousEnabled != enabled) {
                    isUnlocked = !enabled
                }
                previousEnabled = enabled
                if (!enabled) {
                    lastBackgroundedAtMillis = null
                }
            }
        }
    }

    fun markUnlocked() {
        isUnlocked = true
    }

    fun lockNow() {
        if (config.value.enabled) {
            isUnlocked = false
        }
    }

    fun onAppBackgrounded(nowMillis: Long = System.currentTimeMillis()) {
        if (!config.value.enabled) return
        lastBackgroundedAtMillis = nowMillis
    }

    fun onAppForegrounded(nowMillis: Long = System.currentTimeMillis()) {
        if (!config.value.enabled) return
        val backgroundedAt = lastBackgroundedAtMillis ?: return
        val elapsed = nowMillis - backgroundedAt
        val timeoutMs = config.value.timeoutMinutes * 60_000L
        if (elapsed >= timeoutMs) {
            lockNow()
        }
        lastBackgroundedAtMillis = null
    }

    suspend fun verifyPin(rawPin: String): Boolean {
        return preferenceRepository.verifyAppPin(rawPin)
    }
}
