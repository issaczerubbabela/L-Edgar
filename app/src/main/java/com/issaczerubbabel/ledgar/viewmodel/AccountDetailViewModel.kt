package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    accountRepository: AccountRepository
) : ViewModel() {

    private val accountId: Long = checkNotNull(savedStateHandle.get<String>("accountId")).toLong()

    val uiState: StateFlow<AccountPageUiState> = accountRepository.getAccountBook()
        .map { book -> accountPage(book, accountId) ?: AccountPageUiState(accountId = accountId, isLoaded = true) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountPageUiState(accountId = accountId))
}
