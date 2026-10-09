package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the transaction sheet does on screens outside the Ledger: Search, Bookmarks, an Account's page. */
@HiltViewModel
class TransactionActionsViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    accountRepository: AccountRepository
) : ViewModel() {

    /** For the Account names a Transaction's rows and sheet show. */
    val accounts: StateFlow<List<AccountRecord>> = accountRepository
        .getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleBookmark(transaction: ExpenseRecord) {
        viewModelScope.launch { repository.setBookmarked(id = transaction.id, isBookmarked = !transaction.isBookmarked) }
    }

    fun delete(transaction: ExpenseRecord) {
        viewModelScope.launch { repository.delete(transaction) }
    }
}
