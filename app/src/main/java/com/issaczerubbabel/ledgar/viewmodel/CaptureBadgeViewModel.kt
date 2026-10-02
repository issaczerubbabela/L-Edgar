package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.repository.CaptureRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** How many captures are waiting, for the Trans tab badge and the banner on the Trans screen. */
@HiltViewModel
class CaptureBadgeViewModel @Inject constructor(
    captureRepository: CaptureRepository
) : ViewModel() {

    val pendingCount: StateFlow<Int> = captureRepository.observePendingCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
