package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.account.AccountMath
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
import javax.inject.Inject

data class AccountListItemUi(
    val id: Long,
    val accountGroup: String,
    val accountName: String,
    val balance: Double,
    val isHidden: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean
)

data class AccountsScreenUiState(
    val assets: Double = 0.0,
    val liabilities: Double = 0.0,
    val total: Double = 0.0,
    val assetGroups: Map<String, List<AccountListItemUi>> = emptyMap(),
    val liabilityGroups: Map<String, List<AccountListItemUi>> = emptyMap()
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _events = MutableSharedFlow<String>(replay = 0)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val groupedAccounts: StateFlow<Map<String, List<AccountRecord>>> = accountRepository
        .getAllAccounts()
        .map { accounts -> accounts.groupBy { it.accountGroup } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allAccounts: StateFlow<List<AccountRecord>> = accountRepository
        .getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<AccountsScreenUiState> = accountRepository
        .getAccountBook()
        .map { book ->
            val balances = AccountMath.balances(book.accounts, book.transactions)
            val totals = AccountMath.totals(book.accounts, book.transactions)
            val liabilityIds = book.accounts.filter { it.isLiability }.mapTo(mutableSetOf()) { it.id }
            val items = book.records.mapIndexed { index, account ->
                AccountListItemUi(
                    id = account.id,
                    accountGroup = account.accountGroup,
                    accountName = account.accountName,
                    balance = balances.getValue(account.id),
                    isHidden = account.isHidden,
                    canMoveUp = index > 0,
                    canMoveDown = index < book.records.lastIndex
                )
            }
            val (liabilities, assets) = items.partition { it.id in liabilityIds }
            AccountsScreenUiState(
                assets = totals.assets,
                liabilities = totals.liabilities,
                total = totals.netWorth,
                assetGroups = assets.groupBy { it.accountGroup },
                liabilityGroups = liabilities.groupBy { it.accountGroup }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountsScreenUiState())

    fun toggleAccountVisibility(accountId: Long) {
        viewModelScope.launch {
            accountRepository.toggleHidden(accountId)
        }
    }

    fun deleteAccount(accountId: Long) {
        viewModelScope.launch {
            val account = accountRepository.getAccountById(accountId) ?: return@launch
            if (accountRepository.hasTransactions(accountId)) {
                accountRepository.save(
                    account.copy(
                        isHidden = true,
                        includeInTotals = false
                    )
                )
                _events.emit("Account has linked transactions, so it was archived (hidden) instead of deleted.")
                return@launch
            }
            accountRepository.delete(account)
        }
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

    fun moveAccountUp(accountId: Long) {
        viewModelScope.launch {
            val accounts = accountRepository.getAllAccountsSnapshot()
            val currentIndex = accounts.indexOfFirst { it.id == accountId }
            if (currentIndex <= 0) return@launch

            val above = accounts[currentIndex - 1]
            val current = accounts[currentIndex]
            accountRepository.swapDisplayOrder(current.id, above.id)
        }
    }

    fun moveAccountDown(accountId: Long) {
        viewModelScope.launch {
            val accounts = accountRepository.getAllAccountsSnapshot()
            val currentIndex = accounts.indexOfFirst { it.id == accountId }
            if (currentIndex == -1 || currentIndex >= accounts.lastIndex) return@launch

            val below = accounts[currentIndex + 1]
            val current = accounts[currentIndex]
            accountRepository.swapDisplayOrder(current.id, below.id)
        }
    }

}
