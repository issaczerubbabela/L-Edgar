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
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddEditAccountUiState(
    val accountId: Long? = null,
    val selectedGroup: String = "",
    val accountName: String = "",
    val amountInput: String = "",
    val description: String = "",
    val includeInTotals: Boolean = true,
    val isHidden: Boolean = false
) {
    val isEditMode: Boolean
        get() = accountId != null
}

@HiltViewModel
class AddEditAccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val workManager: WorkManager,
    dropdownOptionRepository: DropdownOptionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditAccountUiState())
    val uiState: StateFlow<AddEditAccountUiState> = _uiState.asStateFlow()

    val accountGroups: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType("ACCOUNT_GROUP")
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _events = MutableSharedFlow<String>(replay = 0)
    val events: SharedFlow<String> = _events.asSharedFlow()

    private val _saved = MutableSharedFlow<Unit>(replay = 0)
    val saved: SharedFlow<Unit> = _saved.asSharedFlow()

    private val _deleted = MutableSharedFlow<Unit>(replay = 0)
    val deleted: SharedFlow<Unit> = _deleted.asSharedFlow()

    init {
        viewModelScope.launch {
            accountGroups.collect { groups ->
                _uiState.update { current ->
                    if (groups.isEmpty()) {
                        current.copy(selectedGroup = "")
                    } else if (current.selectedGroup.isBlank() || current.selectedGroup !in groups) {
                        current.copy(selectedGroup = groups.first())
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun startCreate() {
        _uiState.update {
            AddEditAccountUiState(
                selectedGroup = accountGroups.value.firstOrNull().orEmpty()
            )
        }
    }

    fun startEdit(accountId: Long) {
        viewModelScope.launch {
            val account = accountRepository.getAccountById(accountId)
            if (account == null) {
                _events.emit("Account not found")
                return@launch
            }
            _uiState.update {
                AddEditAccountUiState(
                    accountId = account.id,
                    selectedGroup = account.groupName,
                    accountName = account.accountName,
                    amountInput = account.initialBalance.toString(),
                    description = account.description.orEmpty(),
                    includeInTotals = account.includeInTotals,
                    isHidden = account.isHidden
                )
            }
        }
    }

    fun updateGroup(group: String) {
        _uiState.update { it.copy(selectedGroup = group) }
    }

    fun updateName(name: String) {
        _uiState.update { it.copy(accountName = name) }
    }

    fun updateAmount(amount: String) {
        _uiState.update { it.copy(amountInput = amount) }
    }

    fun updateDescription(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun updateIncludeInTotals(include: Boolean) {
        _uiState.update { it.copy(includeInTotals = include) }
    }

    fun updateHidden(hidden: Boolean) {
        _uiState.update { it.copy(isHidden = hidden) }
    }

    fun save() {
        val state = _uiState.value
        val name = state.accountName.trim()
        val parsedAmount = state.amountInput.trim().toDoubleOrNull()

        if (state.selectedGroup.isBlank()) {
            viewModelScope.launch { _events.emit("Select account group") }
            return
        }
        if (name.isBlank()) {
            viewModelScope.launch { _events.emit("Enter account name") }
            return
        }
        if (parsedAmount == null) {
            viewModelScope.launch { _events.emit("Enter valid amount") }
            return
        }

        viewModelScope.launch {
            val existing = state.accountId?.let { accountRepository.getAccountById(it) }
            val displayOrder = existing?.displayOrder
                ?: (accountRepository.getAllAccountsSnapshot().maxOfOrNull { it.displayOrder } ?: -1) + 1

            accountRepository.save(
                AccountRecord(
                    id = state.accountId ?: 0,
                    groupName = state.selectedGroup,
                    accountName = name,
                    initialBalance = parsedAmount,
                    isHidden = state.isHidden,
                    displayOrder = displayOrder,
                    description = state.description.trim().ifBlank { null },
                    includeInTotals = state.includeInTotals
                )
            )

            enqueueAccountBackupSync()
            _saved.emit(Unit)
        }
    }

    fun deleteIfAllowed() {
        val accountId = _uiState.value.accountId ?: return

        viewModelScope.launch {
            if (accountRepository.hasTransactions(accountId)) {
                _events.emit("Cannot delete account with existing transactions. Please Hide it instead.")
                return@launch
            }

            val account = accountRepository.getAccountById(accountId)
            if (account == null) {
                _events.emit("Account not found")
                return@launch
            }

            accountRepository.delete(account)
            enqueueAccountBackupSync()
            _deleted.emit(Unit)
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
