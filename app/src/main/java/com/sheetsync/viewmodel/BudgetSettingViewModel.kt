package com.sheetsync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheetsync.data.local.entity.BudgetRecord
import com.sheetsync.data.repository.BudgetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BudgetSettingViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository
) : ViewModel() {

    private val _showEditorDialog = MutableStateFlow(false)
    private val _editingId = MutableStateFlow<Long?>(null)
    private val _selectedCategory = MutableStateFlow("Food")
    private val _amountInput = MutableStateFlow("")

    val categories = listOf("Food", "Social Life", "Transport", "Shopping", "Utilities", "Health", "Education")

    val uiState: StateFlow<BudgetSettingUiState> = combine(
        budgetRepository.observeBudgets(),
        _showEditorDialog,
        _editingId,
        _selectedCategory,
        _amountInput
    ) { budgets, showEditor, editingId, category, amount ->
        BudgetSettingUiState(
            items = budgets.map {
                BudgetSettingItemUi(
                    id = it.id,
                    category = it.category,
                    icon = iconForCategory(it.category),
                    amount = it.amount
                )
            },
            showEditorDialog = showEditor,
            editingId = editingId,
            selectedCategory = category,
            amountInput = amount
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetSettingUiState())

    fun openAddDialog() {
        _editingId.value = null
        _selectedCategory.value = categories.first()
        _amountInput.value = ""
        _showEditorDialog.value = true
    }

    fun openEditDialog(item: BudgetSettingItemUi) {
        _editingId.value = item.id
        _selectedCategory.value = item.category
        _amountInput.value = item.amount.toString()
        _showEditorDialog.value = true
    }

    fun closeEditor() {
        _showEditorDialog.value = false
    }

    fun updateCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateAmount(amount: String) {
        _amountInput.value = amount
    }

    fun saveBudget() {
        val amount = _amountInput.value.toDoubleOrNull() ?: return
        viewModelScope.launch {
            budgetRepository.upsert(
                BudgetRecord(
                    id = _editingId.value ?: 0L,
                    category = _selectedCategory.value,
                    iconEmoji = iconForCategory(_selectedCategory.value),
                    amount = amount
                )
            )
            _showEditorDialog.value = false
        }
    }

    fun deleteBudget(item: BudgetSettingItemUi) {
        viewModelScope.launch {
            budgetRepository.delete(
                BudgetRecord(
                    id = item.id,
                    category = item.category,
                    iconEmoji = item.icon,
                    amount = item.amount
                )
            )
        }
    }

    private fun iconForCategory(category: String): String = when (category) {
        "Food" -> "🍜"
        "Social Life" -> "🧑‍🤝‍🧑"
        "Transport" -> "🚌"
        "Shopping" -> "🛍️"
        "Utilities" -> "💡"
        "Health" -> "🏥"
        "Education" -> "📘"
        else -> "📒"
    }
}
