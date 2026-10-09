package com.issaczerubbabel.ledgar.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.ledger.Ledger
import com.issaczerubbabel.ledgar.ledger.LedgerMonth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    accountRepository: AccountRepository,
    dropdownOptionRepository: DropdownOptionRepository
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())
    /** Transactions deleted but still offering Undo: hidden from the Ledger, not yet written. */
    private val pendingDeleteIds = MutableStateFlow<Set<Long>>(emptySet())
    private var shouldAutoFocusLatestMonth = true
    val selectedTxIds = mutableStateListOf<Long>()

    val accounts: StateFlow<List<AccountRecord>> = accountRepository
        .getAllVisibleAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val categories: StateFlow<List<String>> = combine(
        dropdownOptionRepository.getOptionsByType("EXPENSE_CATEGORY"),
        dropdownOptionRepository.getOptionsByType("INCOME_CATEGORY")
    ) { expense, income ->
        (expense + income).map { it.name }.distinct().sorted()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The date the user has tapped in the Calendar grid. */
    var selectedDate: LocalDate? by mutableStateOf(null)
        private set

    fun selectDate(date: LocalDate) {
        selectedDate = if (selectedDate == date) null else date
    }

    init {
        viewModelScope.launch {
            repository.getAllRecords().collect { records ->
                if (records.isEmpty()) {
                    shouldAutoFocusLatestMonth = true
                    return@collect
                }

                if (!shouldAutoFocusLatestMonth) return@collect

                // Open on the most recent month with Transactions, so imported history is
                // visible straight away.
                val latest = withContext(Dispatchers.Default) { Ledger.latestMonth(records) } ?: return@collect
                if (shouldAutoFocusLatestMonth && _month.value != latest) {
                    _month.value = latest
                    selectedDate = null
                }
                shouldAutoFocusLatestMonth = false
            }
        }
    }

    val uiState: StateFlow<LedgerMonth> =
        combine(repository.getAllRecords(), accounts, _month, pendingDeleteIds) { records, accounts, month, pending ->
            Ledger.build(records, accounts, month, LocalDate.now(), pending)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                Ledger.build(emptyList(), emptyList(), YearMonth.now(), LocalDate.now())
            )

    // ── Month navigation ──────────────────────────────────────────────────────

    fun nextMonth() = shiftMonth(1)
    fun prevMonth() = shiftMonth(-1)

    fun setMonthYear(year: Int, month: Int) {
        val safeMonth = month.coerceIn(1, 12)
        val safeYear = year.coerceIn(1900, 2100)
        shouldAutoFocusLatestMonth = false
        _month.value = YearMonth.of(safeYear, safeMonth)
        selectedDate = null
        clearSelection()
    }

    private fun shiftMonth(delta: Long) {
        shouldAutoFocusLatestMonth = false
        _month.value = _month.value.plusMonths(delta)
        selectedDate = null
        clearSelection()
    }

    fun delete(record: ExpenseRecord) {
        viewModelScope.launch {
            repository.delete(record)
        }
    }

    fun onTransactionLongPress(id: Long) {
        if (!selectedTxIds.contains(id)) selectedTxIds.add(id)
    }

    fun toggleTransactionSelection(id: Long) {
        if (selectedTxIds.contains(id)) {
            selectedTxIds.remove(id)
        } else {
            selectedTxIds.add(id)
        }
    }

    fun clearSelection() {
        selectedTxIds.clear()
    }

    fun isSelectionMode(): Boolean = selectedTxIds.isNotEmpty()

    fun selectedSum(records: List<ExpenseRecord>): Double {
        val selected = selectedTxIds.toSet()
        return records
            .asSequence()
            .filter { selected.contains(it.id) }
            .sumOf { record ->
                when (record.type) {
                    "Income" -> record.amount
                    "Expense" -> -record.amount
                    else -> 0.0
                }
            }
    }

    fun deleteSelectedTransactions() {
        val ids = selectedTxIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.deleteTransactionsByIds(ids)
            clearSelection()
        }
    }

    fun updateSelectedDates(newDate: String) {
        val ids = selectedTxIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.updateTransactionsDateByIds(ids = ids, newDate = newDate)
            clearSelection()
        }
    }

    fun updateSelectedCategories(newCategory: String) {
        val ids = selectedTxIds.toList()
        if (ids.isEmpty() || newCategory.isBlank()) return
        viewModelScope.launch {
            repository.updateTransactionsCategoryByIds(ids = ids, newCategory = newCategory)
            clearSelection()
        }
    }

    fun updateSelectedAssets(accountId: Long) {
        val ids = selectedTxIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.updateTransactionsAssetByIds(ids = ids, accountId = accountId)
            clearSelection()
        }
    }

    fun updateSelectedDescriptions(newDescription: String) {
        val ids = selectedTxIds.toList()
        if (ids.isEmpty() || newDescription.isBlank()) return
        viewModelScope.launch {
            repository.updateTransactionsDescriptionByIds(ids = ids, newDescription = newDescription)
            clearSelection()
        }
    }

    fun toggleBookmark(record: ExpenseRecord) {
        viewModelScope.launch {
            repository.setBookmarked(id = record.id, isBookmarked = !record.isBookmarked)
        }
    }
}
