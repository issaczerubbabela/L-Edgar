package com.sheetsync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sheetsync.data.local.entity.AccountRecord
import com.sheetsync.data.repository.AccountRepository
import com.sheetsync.data.repository.DropdownOptionRepository
import com.sheetsync.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddAccountUiState(
    val selectedGroup: String = "",
    val accountName: String = "",
    val amountInput: String = "",
    val errorMessage: String? = null
)

const val ACCOUNT_ROUTE_ADD = "add_account"

@HiltViewModel
class AddAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val workManager: WorkManager,
    dropdownOptionRepository: DropdownOptionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddAccountUiState())
    val uiState: StateFlow<AddAccountUiState> = _uiState.asStateFlow()

    val accountGroups: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType("ACCOUNT_GROUP")
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _saved = MutableSharedFlow<Unit>(replay = 0)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    init {
        viewModelScope.launch {
            accountGroups.collect { groups ->
                _uiState.update { current ->
                    when {
                        groups.isEmpty() -> current.copy(selectedGroup = "")
                        current.selectedGroup.isBlank() -> current.copy(selectedGroup = groups.first())
                        current.selectedGroup !in groups -> current.copy(selectedGroup = groups.first())
                        else -> current
                    }
                }
            }
        }
    }

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

        if (state.selectedGroup.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Select account group") }
            return
        }
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
                    groupName = state.selectedGroup,
                    accountName = trimmedName,
                    initialBalance = parsedAmount
                )
            )
            enqueueAccountBackupSync()
            _saved.emit(Unit)
        }
    }

    private fun enqueueAccountBackupSync() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(workDataOf(SyncWorker.KEY_BACKUP_ACCOUNTS to true))
            .addTag(SyncWorker.TAG)
            .build()

        workManager.enqueueUniqueWork(SyncWorker.TAG, ExistingWorkPolicy.REPLACE, request)
    }
}
