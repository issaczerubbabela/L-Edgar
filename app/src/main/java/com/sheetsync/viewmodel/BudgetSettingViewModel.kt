package com.sheetsync.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.sheetsync.data.local.entity.Budget
import com.sheetsync.data.repository.BudgetRepository
import com.sheetsync.data.repository.DropdownOptionRepository
import com.sheetsync.sync.SyncWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class BudgetSettingViewModel @Inject constructor(
    private val budgetRepository: BudgetRepository,
    dropdownOptionRepository: DropdownOptionRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val activeMonthYear = YearMonth.now().format(MONTH_YEAR_FORMATTER)

    private val currentMonthBudgets = budgetRepository.observeBudgets(activeMonthYear)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val expenseCategoryOptions = dropdownOptionRepository
        .getOptionsByType(EXPENSE_CATEGORY_TYPE)
        .map { options ->
            options
                .map { it.name.trim() }
                .filter { it.isNotEmpty() && it != TOTAL_CATEGORY }
                .distinct()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showBottomSheet = MutableStateFlow(false)
    private val _editingId = MutableStateFlow<Long?>(null)
    private val _selectedCategory = MutableStateFlow("")
    private val _amountInput = MutableStateFlow("")

    // Seeded from the DB once on first non-zero value, then user-controlled.
    private val _totalBudgetInput = MutableStateFlow("")
    private var _totalInputSeeded = false

    val uiState: StateFlow<BudgetSettingUiState> = combine(
        currentMonthBudgets,
        expenseCategoryOptions,
        _showBottomSheet,
        _editingId,
        _selectedCategory,
        _amountInput,
        _totalBudgetInput
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val budgets = args[0] as List<Budget>
        val categories = args[1] as List<String>
        val showSheet = args[2] as Boolean
        val editingId = args[3] as Long?
        val selectedCategory = args[4] as String
        val amountInput = args[5] as String
        val totalBudgetInput = args[6] as String

        val totalBudgetDbAmount = budgets.firstOrNull { it.category == TOTAL_CATEGORY }?.amount ?: 0.0
        val sumOfCategoryBudgets = budgets.filterNot { it.category == TOTAL_CATEGORY }.sumOf { it.amount }

        // Seed the text field with the DB value the first time it becomes available
        if (!_totalInputSeeded && totalBudgetDbAmount > 0.0) {
            _totalInputSeeded = true
            _totalBudgetInput.value = totalBudgetDbAmount.toLong().toString()
        }

        val resolvedCategory = selectedCategory.ifBlank { categories.firstOrNull().orEmpty() }

        BudgetSettingUiState(
            items = budgets
                .filterNot { it.category == TOTAL_CATEGORY }
                .map {
                    BudgetSettingItemUi(
                        id = it.id,
                        category = it.category,
                        icon = iconForCategory(it.category),
                        amount = it.amount
                    )
                },
            categoryOptions = categories,
            totalBudgetInput = totalBudgetInput,
            totalBudgetAmount = totalBudgetDbAmount,
            allocatedAmount = sumOfCategoryBudgets,
            otherAmount = maxOf(0.0, totalBudgetDbAmount - sumOfCategoryBudgets),
            showEditorDialog = showSheet,
            editingId = editingId,
            selectedCategory = resolvedCategory,
            amountInput = amountInput
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetSettingUiState())

    init {
        // Overdrive rule: whenever category sum exceeds the stored total, auto-upsert __TOTAL__.
        viewModelScope.launch {
            currentMonthBudgets
                // Skip the first emission to avoid a spurious DB write on load.
                .drop(1)
                .collect { budgets ->
                    val totalDb = budgets.firstOrNull { it.category == TOTAL_CATEGORY }?.amount ?: 0.0
                    val categorySum = budgets.filterNot { it.category == TOTAL_CATEGORY }.sumOf { it.amount }

                    if (categorySum > totalDb) {
                        val existingId = budgets.firstOrNull { it.category == TOTAL_CATEGORY }?.id ?: 0L
                        budgetRepository.upsert(
                            Budget(
                                id = existingId,
                                monthYear = activeMonthYear,
                                category = TOTAL_CATEGORY,
                                amount = categorySum
                            )
                        )
                        // Keep the header text field in sync
                        _totalBudgetInput.value = categorySum.toLong().toString()
                        enqueueSyncWork()
                    }
                }
        }
    }

    // ── Sheet controls ──────────────────────────────────────────────────────────

    fun openAddDialog() {
        _editingId.value = null
        _selectedCategory.value = expenseCategoryOptions.value.firstOrNull().orEmpty()
        _amountInput.value = ""
        _showBottomSheet.value = true
    }

    fun openEditDialog(item: BudgetSettingItemUi) {
        _editingId.value = item.id
        _selectedCategory.value = item.category
        _amountInput.value = item.amount.toLong().toString()
        _showBottomSheet.value = true
    }

    fun closeEditor() {
        _showBottomSheet.value = false
    }

    // ── Inputs ──────────────────────────────────────────────────────────────────

    fun updateCategory(category: String) {
        _selectedCategory.value = category
    }

    fun updateAmount(amount: String) {
        _amountInput.value = amount
    }

    fun updateTotalBudgetInput(amount: String) {
        _totalBudgetInput.value = amount
    }

    // ── Saves ───────────────────────────────────────────────────────────────────

    fun saveTotalBudget() {
        val amount = _totalBudgetInput.value.toDoubleOrNull() ?: return
        viewModelScope.launch {
            val existingId = currentMonthBudgets.value.firstOrNull { it.category == TOTAL_CATEGORY }?.id ?: 0L
            budgetRepository.upsert(
                Budget(
                    id = existingId,
                    monthYear = activeMonthYear,
                    category = TOTAL_CATEGORY,
                    amount = amount
                )
            )
            _totalInputSeeded = true
            enqueueSyncWork()
        }
    }

    fun saveBudget() {
        val amount = _amountInput.value.toDoubleOrNull() ?: return
        val category = _selectedCategory.value.takeIf { it.isNotBlank() } ?: return
        viewModelScope.launch {
            budgetRepository.upsert(
                Budget(
                    id = _editingId.value ?: 0L,
                    monthYear = activeMonthYear,
                    category = category,
                    amount = amount
                )
            )
            _showBottomSheet.value = false
            enqueueSyncWork()
        }
    }

    fun deleteBudget(item: BudgetSettingItemUi) {
        viewModelScope.launch {
            budgetRepository.delete(
                Budget(
                    id = item.id,
                    monthYear = activeMonthYear,
                    category = item.category,
                    amount = item.amount
                )
            )
            enqueueSyncWork()
        }
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

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

    companion object {
        private const val EXPENSE_CATEGORY_TYPE = "EXPENSE_CATEGORY"
        const val TOTAL_CATEGORY = "__TOTAL__"
        private val MONTH_YEAR_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")
    }
}
