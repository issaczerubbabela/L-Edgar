package com.issaczerubbabel.ledgar.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.sync.SyncScheduler
import com.issaczerubbabel.ledgar.sync.SyncStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    accountRepository: AccountRepository,
    dropdownOptionRepository: DropdownOptionRepository,
    private val syncScheduler: SyncScheduler,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val accounts: StateFlow<List<AccountRecord>> = accountRepository
        .getAllVisibleAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategories: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType("EXPENSE_CATEGORY")
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType("INCOME_CATEGORY")
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var selectedDate by mutableStateOf(LocalDate.now())
    var selectedType by mutableStateOf("Expense")
    var selectedCategory by mutableStateOf("")
    var selectedAccountId by mutableStateOf<Long?>(null)
    var selectedFromAccountId by mutableStateOf<Long?>(null)
    var selectedToAccountId by mutableStateOf<Long?>(null)
    var description by mutableStateOf("")
    var amount by mutableStateOf("")
    var remarks by mutableStateOf("")
    var saveSuccess by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var syncStatus by mutableStateOf(SyncStatus.Idle)

    private var editingRecordId: Long? = null
    private var hasStartedSyncObserver = false
    val isEditMode: Boolean get() = editingRecordId != null

    init {
        val navId = savedStateHandle.get<Long>("transactionId")
        editingRecordId = navId?.takeIf { it > 0L }
        val copyTransactionId = savedStateHandle.get<Long>("copyTransactionId")?.takeIf { it > 0L }
        val copyDateMode = savedStateHandle.get<String>("copyDateMode") ?: "original"

        if (editingRecordId != null) {
            viewModelScope.launch {
                val record = repository.getById(editingRecordId ?: return@launch)
                if (record == null) {
                    errorMessage = "Transaction not found"
                    return@launch
                }
                selectedDate = runCatching { LocalDate.parse(record.date) }.getOrDefault(LocalDate.now())
                selectedType = record.type
                selectedCategory = record.category
                selectedAccountId = when (record.type) {
                    "Expense", "Income" -> record.accountId
                    else -> null
                }
                selectedFromAccountId = record.fromAccountId
                selectedToAccountId = record.toAccountId
                description = record.description
                amount = if (record.amount % 1.0 == 0.0) record.amount.toInt().toString() else record.amount.toString()
                remarks = record.remarks
            }
        } else if (copyTransactionId != null) {
            viewModelScope.launch {
                val source = repository.getById(copyTransactionId)
                if (source == null) {
                    errorMessage = "Transaction to copy not found"
                    return@launch
                }

                selectedDate = if (copyDateMode.equals("today", ignoreCase = true)) {
                    LocalDate.now()
                } else {
                    runCatching { LocalDate.parse(source.date) }.getOrDefault(LocalDate.now())
                }

                selectedType = source.type
                selectedCategory = source.category
                selectedAccountId = when (source.type) {
                    "Expense", "Income" -> source.accountId
                    else -> null
                }
                selectedFromAccountId = source.fromAccountId
                selectedToAccountId = source.toAccountId
                description = source.description
                amount = if (source.amount % 1.0 == 0.0) source.amount.toInt().toString() else source.amount.toString()
                remarks = source.remarks
            }
        }
    }

    fun save() {
        val parsedAmount = amount.toDoubleOrNull()
        if (parsedAmount == null || parsedAmount <= 0.0) {
            errorMessage = "Enter a valid amount"; return
        }
        if (selectedType == "Transfer") {
            if (selectedFromAccountId == null) { errorMessage = "Select From Account"; return }
            if (selectedToAccountId == null) { errorMessage = "Select To Account"; return }
            if (selectedFromAccountId == selectedToAccountId) { errorMessage = "From and To accounts must differ"; return }
        } else {
            if (selectedCategory.isBlank()) { errorMessage = "Select a category"; return }
            if (selectedAccountId == null) { errorMessage = "Select an account"; return }
        }

        viewModelScope.launch {
            val baseRecord = editingRecordId?.let { repository.getById(it) }
            val accountNameById = accounts.value.associate { it.id to it.accountName }

            val record = ExpenseRecord(
                id = baseRecord?.id ?: 0,
                date = selectedDate.toString(),
                type = selectedType,
                category = if (selectedType == "Transfer") "Transfer" else selectedCategory,
                description = description,
                amount = parsedAmount,
                accountId = if (selectedType == "Transfer") null else selectedAccountId,
                remarks = remarks,
                fromAccountId = when (selectedType) {
                    "Expense" -> selectedAccountId
                    "Transfer" -> selectedFromAccountId
                    else -> null
                },
                toAccountId = when (selectedType) {
                    "Income" -> selectedAccountId
                    "Transfer" -> selectedToAccountId
                    else -> null
                },
                toAccountName = when (selectedType) {
                    "Transfer" -> selectedToAccountId?.let { accountNameById[it] }
                    else -> baseRecord?.toAccountName
                },
                isBookmarked = baseRecord?.isBookmarked ?: false,
                isSynced = false,
                remoteTimestamp = baseRecord?.remoteTimestamp,
                syncAction = if (isEditMode) "UPDATE" else "INSERT"
            )

            if (isEditMode) {
                repository.update(record)
            } else {
                repository.save(record)
            }

            saveSuccess = true
            if (!isEditMode) resetForm()
        }
    }

    fun deleteCurrent() {
        val id = editingRecordId ?: return
        viewModelScope.launch {
            val current = repository.getById(id)
            if (current == null) {
                errorMessage = "Transaction not found"
                return@launch
            }

            repository.delete(current)

            saveSuccess = true
        }
    }

    fun resetSaveSuccess() { saveSuccess = false }
    fun clearError() { errorMessage = null }

    fun retrySync() {
        viewModelScope.launch { syncScheduler.retrySync() }
    }

    fun startSyncStatusObserver() {
        if (hasStartedSyncObserver) return
        hasStartedSyncObserver = true
        observeSyncStatus()
    }

    private fun resetForm() {
        selectedDate = LocalDate.now()
        selectedType = "Expense"
        selectedCategory = ""
        selectedAccountId = null
        selectedFromAccountId = null
        selectedToAccountId = null
        description = ""
        amount = ""
        remarks = ""
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            syncScheduler.transactionSyncStatus.collect { syncStatus = it }
        }
    }
}
