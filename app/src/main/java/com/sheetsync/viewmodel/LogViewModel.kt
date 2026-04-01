package com.sheetsync.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import androidx.work.WorkInfo
import com.sheetsync.data.local.entity.AccountRecord
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.repository.AccountRepository
import com.sheetsync.data.repository.DropdownOptionRepository
import com.sheetsync.data.repository.ExpenseRepository
import com.sheetsync.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

enum class SyncStatusUi {
    Idle,
    Syncing,
    Synced,
    Failed
}

@HiltViewModel
class LogViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    accountRepository: AccountRepository,
    dropdownOptionRepository: DropdownOptionRepository,
    private val workManager: WorkManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val accounts: StateFlow<List<AccountRecord>> = accountRepository
        .getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategories: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType("EXPENSE_CATEGORY")
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType("INCOME_CATEGORY")
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val paymentModes: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType("PAYMENT_MODE")
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var selectedDate by mutableStateOf(LocalDate.now())
    var selectedType by mutableStateOf("Expense")
    var selectedCategory by mutableStateOf("")
    var selectedFromAccountId by mutableStateOf<Long?>(null)
    var selectedToAccountId by mutableStateOf<Long?>(null)
    var description by mutableStateOf("")
    var amount by mutableStateOf("")
    var selectedPaymentMode by mutableStateOf("")
    var remarks by mutableStateOf("")
    var saveSuccess by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var syncInfoMessage by mutableStateOf<String?>(null)
    var syncStatus by mutableStateOf(SyncStatusUi.Idle)

    private var editingRecordId: Long? = null
    private var hasObservedInitialWorkerState = false
    private var lastObservedWorkerId: UUID? = null
    private var lastObservedWorkerState: WorkInfo.State? = null
    val isEditMode: Boolean get() = editingRecordId != null

    init {
        val navId = savedStateHandle.get<Long>("transactionId")
        editingRecordId = navId?.takeIf { it > 0L }

        observeSyncStatus()

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
                selectedFromAccountId = record.fromAccountId
                selectedToAccountId = record.toAccountId
                description = record.description
                amount = if (record.amount % 1.0 == 0.0) record.amount.toInt().toString() else record.amount.toString()
                selectedPaymentMode = record.paymentMode
                remarks = record.remarks
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
            if (selectedPaymentMode.isBlank()) { errorMessage = "Select a payment mode"; return }
        }

        viewModelScope.launch {
            val baseRecord = editingRecordId?.let { repository.getById(it) }

            val record = ExpenseRecord(
                id = baseRecord?.id ?: 0,
                date = selectedDate.toString(),
                type = selectedType,
                category = if (selectedType == "Transfer") "Transfer" else selectedCategory,
                description = description,
                amount = parsedAmount,
                paymentMode = if (selectedType == "Transfer") "Transfer" else selectedPaymentMode,
                remarks = remarks,
                fromAccountId = selectedFromAccountId,
                toAccountId = selectedToAccountId,
                isSynced = false,
                remoteTimestamp = baseRecord?.remoteTimestamp,
                syncAction = if (isEditMode) "UPDATE" else "INSERT"
            )

            if (isEditMode) {
                repository.update(record)
            } else {
                repository.save(record)
            }

            syncStatus = SyncStatusUi.Syncing
            enqueueSyncWork()
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

            repository.update(
                current.copy(
                    isSynced = false,
                    syncAction = "DELETE"
                )
            )

            syncStatus = SyncStatusUi.Syncing
            enqueueSyncWork()
            saveSuccess = true
        }
    }

    fun resetSaveSuccess() { saveSuccess = false }
    fun clearError() { errorMessage = null }
    fun clearSyncInfoMessage() { syncInfoMessage = null }

    fun retrySync() {
        syncStatus = SyncStatusUi.Syncing
        enqueueSyncWork()
    }

    private fun resetForm() {
        selectedDate = LocalDate.now()
        selectedType = "Expense"
        selectedCategory = ""
        selectedFromAccountId = null
        selectedToAccountId = null
        description = ""
        amount = ""
        selectedPaymentMode = ""
        remarks = ""
    }

    private fun enqueueSyncWork() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build())
            .addTag(SyncWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(SyncWorker.TAG, ExistingWorkPolicy.REPLACE, request)
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(SyncWorker.TAG).collect { infos ->
                val latest = infos.firstOrNull() ?: run {
                    if (syncStatus != SyncStatusUi.Synced) syncStatus = SyncStatusUi.Idle
                    return@collect
                }

                if (!hasObservedInitialWorkerState) {
                    hasObservedInitialWorkerState = true
                    lastObservedWorkerId = latest.id
                    lastObservedWorkerState = latest.state
                }

                syncStatus = when (latest.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.RUNNING,
                    WorkInfo.State.BLOCKED -> SyncStatusUi.Syncing

                    WorkInfo.State.SUCCEEDED -> SyncStatusUi.Synced
                    WorkInfo.State.FAILED,
                    WorkInfo.State.CANCELLED -> SyncStatusUi.Failed
                }

                val workerJustCompleted =
                    latest.state == WorkInfo.State.SUCCEEDED && (
                        latest.id != lastObservedWorkerId ||
                            lastObservedWorkerState != WorkInfo.State.SUCCEEDED
                        )

                if (workerJustCompleted) {
                    val dropdownCount = latest.outputData.getInt(SyncWorker.KEY_DROPDOWN_BACKUP_COUNT, -1)
                    val budgetCount = latest.outputData.getInt(SyncWorker.KEY_BUDGET_BACKUP_COUNT, -1)
                    if (dropdownCount >= 0 && budgetCount >= 0) {
                        syncInfoMessage = "Sync complete. Dropdown backup: $dropdownCount option${if (dropdownCount == 1) "" else "s"}. Budget backup: $budgetCount row${if (budgetCount == 1) "" else "s"}."
                    } else if (dropdownCount >= 0) {
                        syncInfoMessage = "Sync complete. Dropdown backup: $dropdownCount option${if (dropdownCount == 1) "" else "s"}."
                    }
                }

                lastObservedWorkerId = latest.id
                lastObservedWorkerState = latest.state
            }
        }
    }
}
