package com.sheetsync.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.repository.ExpenseRepository
import com.sheetsync.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class LogViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val workManager: WorkManager
) : ViewModel() {

    var selectedDate by mutableStateOf(LocalDate.now())
    var selectedType by mutableStateOf("Expense")
    var selectedCategory by mutableStateOf("")
    var description by mutableStateOf("")
    var amount by mutableStateOf("")
    var selectedPaymentMode by mutableStateOf("")
    var remarks by mutableStateOf("")
    var saveSuccess by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    fun save() {
        val parsedAmount = amount.toDoubleOrNull()
        if (parsedAmount == null || parsedAmount <= 0.0) {
            errorMessage = "Enter a valid amount"; return
        }
        if (selectedCategory.isBlank()) { errorMessage = "Select a category"; return }
        if (selectedPaymentMode.isBlank()) { errorMessage = "Select a payment mode"; return }

        viewModelScope.launch {
            val record = ExpenseRecord(
                date = selectedDate.toString(),
                type = selectedType,
                category = selectedCategory,
                description = description,
                amount = parsedAmount,
                paymentMode = selectedPaymentMode,
                remarks = remarks,
                isSynced = false
            )
            repository.save(record)
            enqueueSyncWork()
            saveSuccess = true
            resetForm()
        }
    }

    fun resetSaveSuccess() { saveSuccess = false }
    fun clearError() { errorMessage = null }

    private fun resetForm() {
        selectedDate = LocalDate.now()
        selectedType = "Expense"
        selectedCategory = ""
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
        workManager.enqueueUniqueWork(SyncWorker.TAG, ExistingWorkPolicy.KEEP, request)
    }
}
