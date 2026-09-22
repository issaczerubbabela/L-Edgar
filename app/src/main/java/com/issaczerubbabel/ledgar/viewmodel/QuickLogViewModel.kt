package com.issaczerubbabel.ledgar.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

private const val EXPENSE_CATEGORY_TYPE = "EXPENSE_CATEGORY"

@HiltViewModel
class QuickLogViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    private val dropdownOptionRepository: DropdownOptionRepository,
    private val workManager: WorkManager
) : ViewModel() {

    val categories = dropdownOptionRepository
        .getOptionsByType(EXPENSE_CATEGORY_TYPE)
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var amount by mutableStateOf("")
    var selectedCategory by mutableStateOf("")
    var errorMessage by mutableStateOf<String?>(null)

    private val _saveSuccess = MutableSharedFlow<Unit>(replay = 0)
    val saveSuccess: SharedFlow<Unit> = _saveSuccess.asSharedFlow()

    fun save() {
        val parsedAmount = amount.toDoubleOrNull()
        if (parsedAmount == null || parsedAmount <= 0.0) {
            errorMessage = "Enter a valid amount"
            return
        }
        if (selectedCategory.isBlank()) {
            errorMessage = "Select a category"
            return
        }

        viewModelScope.launch {
            runCatching {
                expenseRepository.save(
                    ExpenseRecord(
                        date = LocalDate.now().toString(),
                        type = "Expense",
                        category = selectedCategory,
                        description = "",
                        amount = parsedAmount,
                        accountId = null,
                        remarks = "",
                        isSynced = false,
                        syncAction = "INSERT"
                    )
                )
            }.onSuccess {
                enqueueSyncWork()
                _saveSuccess.emit(Unit)
            }.onFailure {
                errorMessage = it.message ?: "Failed to save transaction"
            }
        }
    }

    /**
     * Creates a new expense category from the Quick Log sheet, mirroring
     * LogViewModel.addCategoryInline: case-insensitive match selects the existing option
     * instead of inserting a duplicate, since there is no unique index on (optionType, name).
     */
    fun addCategoryInline(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return

        val existing = categories.value.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        if (existing != null) {
            selectedCategory = existing
            return
        }

        viewModelScope.launch {
            val options = dropdownOptionRepository.getOptionsByType(EXPENSE_CATEGORY_TYPE).first()
            val maxOrder = options.maxOfOrNull { it.displayOrder } ?: -1
            dropdownOptionRepository.insert(
                DropdownOption(
                    optionType = EXPENSE_CATEGORY_TYPE,
                    name = trimmed,
                    displayOrder = maxOrder + 1
                )
            )
            selectedCategory = trimmed
            enqueueSyncWork()
        }
    }

    fun clearError() {
        errorMessage = null
    }

    private fun enqueueSyncWork() {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .addTag(SyncWorker.TAG)
            .build()
        workManager.enqueueUniqueWork(SyncWorker.TAG, ExistingWorkPolicy.REPLACE, request)
    }
}
