package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.UnparsedAlert
import com.issaczerubbabel.ledgar.data.repository.CaptureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UnparsedAlertsViewModel @Inject constructor(
    private val captureRepository: CaptureRepository
) : ViewModel() {

    val alerts: StateFlow<List<UnparsedAlert>> = captureRepository.observeUnparsed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { captureRepository.deleteUnparsed(id) }
    }

    fun clearAll() {
        viewModelScope.launch { captureRepository.clearUnparsed() }
    }
}
