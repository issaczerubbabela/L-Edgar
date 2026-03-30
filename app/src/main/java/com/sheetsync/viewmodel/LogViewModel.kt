package com.sheetsync.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import androidx.work.WorkInfo
import com.sheetsync.data.local.entity.AccountRecord
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.repository.AccountRepository
import com.sheetsync.data.repository.ExpenseRepository
import com.sheetsync.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
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
    private val workManager: WorkManager
) : ViewModel() {

    val accounts: StateFlow<List<AccountRecord>> = accountRepository
        .getAllAccounts()
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
    var syncStatus by mutableStateOf(SyncStatusUi.Idle)

    init {
        observeSyncStatus()
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
            val record = ExpenseRecord(
                date = selectedDate.toString(),
                type = selectedType,
                category = if (selectedType == "Transfer") "Transfer" else selectedCategory,
                description = description,
                amount = parsedAmount,
                paymentMode = if (selectedType == "Transfer") "Transfer" else selectedPaymentMode,
                remarks = remarks,
                fromAccountId = selectedFromAccountId,
                toAccountId = selectedToAccountId,
                isSynced = false
            )
            repository.save(record)
            syncStatus = SyncStatusUi.Syncing
            enqueueSyncWork()
            saveSuccess = true
            resetForm()
        }
    }

    fun resetSaveSuccess() { saveSuccess = false }
    fun clearError() { errorMessage = null }

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

                syncStatus = when (latest.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.RUNNING,
                    WorkInfo.State.BLOCKED -> SyncStatusUi.Syncing

                    WorkInfo.State.SUCCEEDED -> SyncStatusUi.Synced
                    WorkInfo.State.FAILED,
                    WorkInfo.State.CANCELLED -> SyncStatusUi.Failed
                }
            }
        }
    }
}
