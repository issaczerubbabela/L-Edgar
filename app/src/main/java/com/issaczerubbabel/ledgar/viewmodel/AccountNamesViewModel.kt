package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.dao.UnmatchedAccountName
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One account name from the Sheet that matches none of the user's Accounts. */
data class AccountNameToFix(
    val name: String,
    val transactionCount: Int,
    /** The Account these Transactions were guessed onto before, or null when they are on none. */
    val countedIn: String?
)

data class AccountNamesUiState(
    val names: List<AccountNameToFix> = emptyList(),
    val accounts: List<AccountRecord> = emptyList(),
    val groups: List<String> = emptyList()
) {
    val transactionCount: Int get() = names.sumOf { it.transactionCount }
}

/** More > Account names: Sheet account names with no Account, and the quick fixes for them. */
@HiltViewModel
class AccountNamesViewModel @Inject constructor(
    private val accounts: AccountRepository,
    dropdowns: DropdownOptionRepository
) : ViewModel() {

    val uiState: StateFlow<AccountNamesUiState> = combine(
        accounts.observeUnmatchedAccountNames(),
        accounts.getAllAccounts(),
        dropdowns.getOptionsByType(ACCOUNT_GROUP)
    ) { unmatched, all, groups ->
        AccountNamesUiState(
            names = toFix(unmatched, all),
            accounts = all,
            groups = groups.map { it.name }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountNamesUiState())

    fun mapTo(name: String, accountId: Long) {
        viewModelScope.launch { accounts.mapAccountName(name, accountId) }
    }

    fun createAccount(name: String, groupName: String) {
        if (name.isBlank() || groupName.isBlank()) return
        viewModelScope.launch { accounts.createAccountForName(name, groupName) }
    }

    companion object {
        const val ACCOUNT_GROUP = "ACCOUNT_GROUP"

        /** One entry per name and the Account it is counted in, biggest first. */
        fun toFix(unmatched: List<UnmatchedAccountName>, accounts: List<AccountRecord>): List<AccountNameToFix> {
            val nameById = accounts.associate { it.id to it.accountName }
            return unmatched.map { row ->
                AccountNameToFix(
                    name = row.name,
                    transactionCount = row.count,
                    countedIn = row.linkedAccountId?.let(nameById::get)
                )
            }
        }
    }
}
