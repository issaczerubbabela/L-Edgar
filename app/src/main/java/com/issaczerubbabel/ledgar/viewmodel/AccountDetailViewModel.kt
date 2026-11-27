package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.FundRepository
import com.issaczerubbabel.ledgar.data.repository.UndoableActions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    accountRepository: AccountRepository,
    fundRepository: FundRepository,
    undoableActions: UndoableActions
) : ViewModel() {

    private val accountId: Long = checkNotNull(savedStateHandle.get<String>("accountId")).toLong()

    val uiState: StateFlow<AccountPageUiState> = combine(
        accountRepository.getAccountBook(),
        undoableActions.hiddenTransactionIds,
        fundRepository.observeSnapshot()
    ) { book, hidden, funds ->
        accountPage(book.without(hidden), accountId, LocalDate.now(), funds) ?: AccountPageUiState(accountId = accountId, isLoaded = true)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountPageUiState(accountId = accountId))
}
