package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.PermanentDeleteStrategy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _events = MutableSharedFlow<String>(replay = 0)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val allAccounts: StateFlow<List<AccountRecord>> = accountRepository
        .getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<AccountsTabUiState> = accountRepository
        .getAccountBook()
        .map { book -> accountsTab(book, LocalDate.now()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountsTabUiState())

    /** Edit order: moves one Account within its group. */
    fun moveAccount(groupName: String, from: Int, to: Int) {
        if (from == to) return
        val order = orderAfterMovingAccount(uiState.value.groups, groupName, from, to)
        viewModelScope.launch { accountRepository.setDisplayOrder(order) }
    }

    /** Edit order: moves a whole group, its Hidden Accounts included. */
    fun moveGroup(from: Int, to: Int) {
        if (from == to) return
        val order = orderAfterMovingGroup(uiState.value.groups, from, to)
        viewModelScope.launch { accountRepository.setDisplayOrder(order) }
    }

    fun deleteAccountPermanently(accountId: Long, reassignToAccountId: Long?) {
        viewModelScope.launch {
            val strategy = if (reassignToAccountId == null) {
                PermanentDeleteStrategy.REMOVE_LINKED_TRANSACTIONS
            } else {
                PermanentDeleteStrategy.REASSIGN_LINKED_TRANSACTIONS
            }

            val deleted = accountRepository.permanentlyDeleteAccount(
                accountId = accountId,
                strategy = strategy,
                reassignToAccountId = reassignToAccountId
            )

            if (!deleted) {
                _events.emit("Unable to delete account permanently")
                return@launch
            }

            _events.emit("Account permanently deleted")
        }
    }
}
