package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.repository.RecurringRepository
import com.issaczerubbabel.ledgar.util.RepeatSchedule
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

/** How many Ask me first Repeat rules have an occurrence waiting, for the Ledger's chip. */
@HiltViewModel
class RepeatBadgeViewModel @Inject constructor(
    recurringRepository: RecurringRepository
) : ViewModel() {

    val waitingCount: StateFlow<Int> = recurringRepository.observeRules()
        .map { rules -> RepeatSchedule.waiting(rules, LocalDate.now()).size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
}
