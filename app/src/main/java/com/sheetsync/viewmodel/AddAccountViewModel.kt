package com.sheetsync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheetsync.data.local.entity.AccountRecord
import com.sheetsync.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddAccountUiState(
    val selectedGroup: String = ACCOUNT_GROUP_OPTIONS.first(),
    val accountName: String = "",
    val amountInput: String = "",
    val errorMessage: String? = null
)

const val ACCOUNT_ROUTE_ADD = "add_account"

val ACCOUNT_GROUP_OPTIONS = listOf(
    "Cash",
    "Accounts",
    "Card",
    "Debit Card",
    "Savings",
    "Top-Up/Prepaid",
    "Investments",
    "Overdrafts",
    "Loan",
    "Insurance",
    "Others"
)

@HiltViewModel
class AddAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddAccountUiState())
    val uiState: StateFlow<AddAccountUiState> = _uiState.asStateFlow()

    private val _saved = MutableSharedFlow<Unit>(replay = 0)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    fun updateGroup(group: String) {
        _uiState.update { it.copy(selectedGroup = group, errorMessage = null) }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(accountName = name, errorMessage = null) }
    }

    fun updateAmount(amount: String) {
        _uiState.update { it.copy(amountInput = amount, errorMessage = null) }
    }

    fun save() {
        val state = _uiState.value
        val trimmedName = state.accountName.trim()
        val parsedAmount = state.amountInput.trim().toDoubleOrNull()

        if (trimmedName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Enter account name") }
            return
        }
        if (parsedAmount == null) {
            _uiState.update { it.copy(errorMessage = "Enter valid amount") }
            return
        }

        viewModelScope.launch {
            accountRepository.save(
                AccountRecord(
                    accountGroup = state.selectedGroup,
                    accountName = trimmedName,
                    initialBalance = parsedAmount
                )
            )
            _saved.emit(Unit)
        }
    }
}
