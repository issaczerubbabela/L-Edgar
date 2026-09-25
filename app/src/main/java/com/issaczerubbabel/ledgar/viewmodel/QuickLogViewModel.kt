package com.issaczerubbabel.ledgar.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.bucket.BucketPreview
import com.issaczerubbabel.ledgar.data.bucket.BucketPreviewCalculator
import com.issaczerubbabel.ledgar.data.bucket.BucketPreviewContext
import com.issaczerubbabel.ledgar.data.bucket.BucketPreviewSource
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.util.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
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
    private val dropdownOptionRepository: DropdownOptionRepository,
    bucketPreviewSource: BucketPreviewSource
) : ViewModel() {

    val categories = dropdownOptionRepository
        .getOptionsByType(TransactionType.EXPENSE_CATEGORY_OPTION)
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bucketContext: StateFlow<BucketPreviewContext?> = bucketPreviewSource.context
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
                        type = TransactionType.EXPENSE,
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
                _saveSuccess.emit(Unit)
            }.onFailure {
                errorMessage = it.message ?: "Failed to save transaction"
            }
        }
    }

    /** Adds the category if it doesn't exist yet, then selects it. */
    fun addCategoryInline(name: String) {
        viewModelScope.launch {
            dropdownOptionRepository.addOptionIfAbsent(TransactionType.EXPENSE_CATEGORY_OPTION, name)
                ?.let { selectedCategory = it }
        }
    }

    /** The bucket this Quick Add expense would land in, counting the amount typed so far. */
    fun bucketPreview(context: BucketPreviewContext?): BucketPreview? = BucketPreviewCalculator.preview(
        context = context,
        type = TransactionType.EXPENSE,
        category = selectedCategory,
        date = LocalDate.now(),
        draftAmount = amount.toDoubleOrNull() ?: 0.0
    )

    fun clearError() {
        errorMessage = null
    }
}
