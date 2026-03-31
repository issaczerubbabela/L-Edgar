package com.sheetsync.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.repository.DropdownOptionRepository
import com.sheetsync.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class QuickLogViewModel @Inject constructor(
    private val expenseRepository: ExpenseRepository,
    dropdownOptionRepository: DropdownOptionRepository
) : ViewModel() {

    val categories = dropdownOptionRepository
        .getOptionsByType("EXPENSE_CATEGORY")
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val paymentModes = dropdownOptionRepository
        .getOptionsByType("PAYMENT_MODE")
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
                val paymentMode = paymentModes.value.firstOrNull().orEmpty()
                expenseRepository.save(
                    ExpenseRecord(
                        date = LocalDate.now().toString(),
                        type = "Expense",
                        category = selectedCategory,
                        description = "",
                        amount = parsedAmount,
                        paymentMode = paymentMode,
                        remarks = "",
                        isSynced = false,
                        syncAction = "INSERT"
                    )
                )
            }.onSuccess {
                _saveSuccess.emit(Unit)
            }.onFailure {
                errorMessage = it.message ?: "Failed to save transaction"
            }
        }
    }

    fun clearError() {
        errorMessage = null
    }
}
