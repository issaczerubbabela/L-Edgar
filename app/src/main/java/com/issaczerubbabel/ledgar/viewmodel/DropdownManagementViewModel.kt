package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class DropdownOptionType(val key: String, val label: String) {
    ExpenseCategory("EXPENSE_CATEGORY", "Expense Categories"),
    IncomeCategory("INCOME_CATEGORY", "Income Categories"),
    AccountGroup("ACCOUNT_GROUP", "Account Groups"),
    PaymentMode("PAYMENT_MODE", "Payment Modes")
}

data class DropdownManagementUiState(
    val selectedType: DropdownOptionType = DropdownOptionType.ExpenseCategory,
    val options: List<DropdownOption> = emptyList(),
    val isAddDialogVisible: Boolean = false,
    val newOptionName: String = ""
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DropdownManagementViewModel @Inject constructor(
    private val dropdownRepository: DropdownOptionRepository
) : ViewModel() {

    val types: List<DropdownOptionType> = DropdownOptionType.entries

    private val selectedType = MutableStateFlow(DropdownOptionType.ExpenseCategory)
    private val addDialogVisible = MutableStateFlow(false)
    private val newOptionName = MutableStateFlow("")

    private val optionsForSelectedType = selectedType
        .flatMapLatest { type -> dropdownRepository.getOptionsByType(type.key) }

    val uiState: StateFlow<DropdownManagementUiState> = combine(
        selectedType,
        optionsForSelectedType,
        addDialogVisible,
        newOptionName
    ) { type, options, showDialog, optionName ->
        DropdownManagementUiState(
            selectedType = type,
            options = options,
            isAddDialogVisible = showDialog,
            newOptionName = optionName
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DropdownManagementUiState()
    )

    fun selectType(type: DropdownOptionType) {
        selectedType.value = type
    }

    fun showAddDialog() {
        addDialogVisible.value = true
        newOptionName.value = ""
    }

    fun hideAddDialog() {
        addDialogVisible.value = false
        newOptionName.value = ""
    }

    fun updateNewOptionName(value: String) {
        newOptionName.value = value
    }

    fun addOption() {
        val name = uiState.value.newOptionName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            val options = uiState.value.options
            val maxOrder = options.maxOfOrNull { it.displayOrder } ?: -1
            dropdownRepository.insert(
                DropdownOption(
                    optionType = uiState.value.selectedType.key,
                    name = name,
                    displayOrder = maxOrder + 1
                )
            )
            hideAddDialog()
        }
    }

    fun deleteOption(option: DropdownOption) {
        viewModelScope.launch {
            dropdownRepository.delete(option)
        }
    }

    fun moveUp(option: DropdownOption) {
        val options = uiState.value.options
        val currentIndex = options.indexOfFirst { it.id == option.id }
        if (currentIndex <= 0) return

        val above = options[currentIndex - 1]
        val updatedCurrent = option.copy(displayOrder = above.displayOrder)
        val updatedAbove = above.copy(displayOrder = option.displayOrder)

        viewModelScope.launch {
            dropdownRepository.updateOptions(listOf(updatedCurrent, updatedAbove))
        }
    }

    fun moveDown(option: DropdownOption) {
        val options = uiState.value.options
        val currentIndex = options.indexOfFirst { it.id == option.id }
        if (currentIndex == -1 || currentIndex >= options.lastIndex) return

        val below = options[currentIndex + 1]
        val updatedCurrent = option.copy(displayOrder = below.displayOrder)
        val updatedBelow = below.copy(displayOrder = option.displayOrder)

        viewModelScope.launch {
            dropdownRepository.updateOptions(listOf(updatedCurrent, updatedBelow))
        }
    }
}
