package com.issaczerubbabel.ledgar.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.ledger.BatchAction
import com.issaczerubbabel.ledgar.ledger.FilterChipUi
import com.issaczerubbabel.ledgar.ledger.FilterOptions
import com.issaczerubbabel.ledgar.ledger.FilterSection
import com.issaczerubbabel.ledgar.ledger.Ledger
import com.issaczerubbabel.ledgar.ledger.LedgerFilter
import com.issaczerubbabel.ledgar.ledger.filterChips
import com.issaczerubbabel.ledgar.ledger.filterOptions
import com.issaczerubbabel.ledgar.ledger.batchPlan
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.ledger.LedgerMonth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

/** A delete still offering Undo: hidden from the Ledger, written only once it's final. */
data class PendingDelete(
    val transaction: ExpenseRecord,
    /** "Deleted “Uber to office” · ₹212". */
    val message: String
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class HistoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
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

    /** For Change category: the Expense categories, then the Income ones, kept apart. */
    val expenseCategories: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType(TransactionType.EXPENSE_CATEGORY_OPTION)
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val incomeCategories: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType(TransactionType.INCOME_CATEGORY_OPTION)
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The date the user has tapped in the Calendar grid. */
    var selectedDate: LocalDate? by mutableStateOf(null)
        private set

    fun selectDate(date: LocalDate) {
        selectedDate = if (selectedDate == date) null else date
    }

    init {
        // The bottom bar marks the Ledger left when another tab is picked; Back from there finds it clear.
        viewModelScope.launch {
            savedStateHandle.getStateFlow(LEFT_LEDGER, false).collect { left ->
                if (left) {
                    clearFilter()
                    savedStateHandle[LEFT_LEDGER] = false
                }
            }
        }
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
                    clearSelection()
                }
                shouldAutoFocusLatestMonth = false
            }
        }
    }

    /**
     * The active filter: kept across months and trips to Search or an edit. It's cleared when another
     * tab is picked ([LEFT_LEDGER]), or when the bottom bar brings back a fresh Ledger.
     */
    private val _filter = MutableStateFlow(LedgerFilter())
    val filter: StateFlow<LedgerFilter> = _filter.asStateFlow()

    val uiState: StateFlow<LedgerMonth> =
        combine(repository.getAllRecords(), accounts, _month, pendingDeleteIds, _filter) { records, accounts, month, pending, filter ->
            Ledger.build(records, accounts, month, LocalDate.now(), pending, filter)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5000),
                Ledger.build(emptyList(), emptyList(), YearMonth.now(), LocalDate.now())
            )

    val filterChips: StateFlow<List<FilterChipUi>> = combine(_filter, accounts) { filter, accounts ->
        Ledger.filterChips(filter, accounts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ── Filter sheet ──────────────────────────────────────────────────────────

    /** What's ticked in the open filter sheet; applied only with Show. */
    private val _filterDraft = MutableStateFlow(LedgerFilter())
    val filterDraft: StateFlow<LedgerFilter> = _filterDraft.asStateFlow()

    private val sheetMonth = combine(_month, _filterDraft, pendingDeleteIds) { month, draft, pending -> Triple(month, draft, pending) }
    private val categoryLists = combine(expenseCategories, incomeCategories) { expense, income -> expense to income }

    val filterOptions: StateFlow<FilterOptions?> =
        combine(repository.getAllRecords(), accounts, categoryLists, sheetMonth) { records, accounts, (expense, income), (month, draft, pending) ->
            Ledger.filterOptions(records, accounts, expense, income, month, draft, pending)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Opens the sheet on the active filter. */
    fun startFilterDraft() {
        _filterDraft.value = _filter.value
    }

    fun toggleExpenseCategory(name: String) = _filterDraft.update { it.copy(expenseCategories = it.expenseCategories.toggle(name)) }
    fun toggleIncomeCategory(name: String) = _filterDraft.update { it.copy(incomeCategories = it.incomeCategories.toggle(name)) }
    fun toggleAccount(id: Long) = _filterDraft.update { it.copy(accountIds = it.accountIds.toggle(id)) }

    /** "Any" on the Expense or Income list: that list's choices off; the other keeps its own. */
    fun clearDraftCategories(income: Boolean) = _filterDraft.update {
        if (income) it.copy(incomeCategories = emptySet()) else it.copy(expenseCategories = emptySet())
    }
    fun clearDraftAccounts() = _filterDraft.update { it.copy(accountIds = emptySet()) }
    fun clearDraft() {
        _filterDraft.value = LedgerFilter()
    }

    fun applyFilterDraft() {
        _filter.value = _filterDraft.value
        clearSelection()
    }

    fun removeFilter(section: FilterSection) {
        _filter.update { it.without(section) }
        clearSelection()
    }

    fun clearFilter() {
        _filter.value = LedgerFilter()
        clearSelection()
    }

    private fun <T> Set<T>.toggle(item: T): Set<T> = if (item in this) this - item else this + item

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

    // ── Delete with Undo ──────────────────────────────────────────────────────

    private val _pendingDelete = MutableStateFlow<PendingDelete?>(null)
    /** The delete the Undo snackbar is offering, if any. */
    val pendingDelete: StateFlow<PendingDelete?> = _pendingDelete.asStateFlow()
    private var undoTimer: Job? = null
    // A final delete must land even if the ViewModel is cleared mid-write.
    private val commitScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Hides [record] at once and offers Undo, writing nothing yet: SyncTriggers asks for a Sync the
     * moment a row turns unsynced, so marking it now and clearing it on Undo would race that Sync.
     * Another delete finalises this one first.
     */
    fun delete(record: ExpenseRecord) {
        commitPendingDelete()
        _pendingDelete.value = PendingDelete(record, Ledger.deletedMessage(record, accounts.value))
        pendingDeleteIds.update { it + record.id }
        undoTimer = viewModelScope.launch {
            delay(UNDO_WINDOW_MS)
            undoTimer = null
            commitPendingDelete()
        }
    }

    /** Brings [pending] back untouched (Room never saw it), unless a newer delete has replaced it. */
    fun undoDelete(pending: PendingDelete) {
        if (_pendingDelete.value != pending) return
        undoTimer?.cancel()
        undoTimer = null
        _pendingDelete.value = null
        pendingDeleteIds.update { it - pending.transaction.id }
    }

    /** Makes the pending delete final with the usual soft delete, so it syncs as any delete does. */
    fun commitPendingDelete() {
        val pending = _pendingDelete.value ?: return
        undoTimer?.cancel()
        undoTimer = null
        _pendingDelete.value = null
        commitScope.launch {
            try {
                repository.delete(pending.transaction)
            } finally {
                // Hidden until the soft delete lands, so the row never flashes back; shown again
                // if the write failed, since it's still there.
                pendingDeleteIds.update { it - pending.transaction.id }
            }
        }
    }

    override fun onCleared() {
        commitPendingDelete()
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

    /** The selected Transactions among those the Ledger shows. */
    fun selectedTransactions(): List<ExpenseRecord> {
        val selected = selectedTxIds.toSet()
        return uiState.value.days.flatMap { day -> day.rows.map { it.record } }.filter { it.id in selected }
    }

    /** Deletes the selection at once, with no Undo: the confirm before it is the safety. */
    fun deleteSelectedTransactions() {
        val ids = selectedTxIds.toList()
        if (ids.isEmpty()) return
        commitPendingDelete()
        viewModelScope.launch {
            repository.deleteTransactionsByIds(ids)
            clearSelection()
        }
    }

    private val _batchMessages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    /** "Changed 4 Expenses · skipped 1 Transfer", once per batch change. */
    val batchMessages: SharedFlow<String> = _batchMessages.asSharedFlow()

    /** Selects every row the Ledger shows for its month. */
    fun selectAllInMonth() {
        val shown = uiState.value.days.flatMap { day -> day.rows.map { it.id } }
        selectedTxIds.addAll(shown.filterNot { it in selectedTxIds })
    }

    /** Applies [action] to the selected rows it fits, as the batch plan says, and reports what it did. */
    fun applyBatch(action: BatchAction) {
        val plan = Ledger.batchPlan(selectedTransactions(), action)
        clearSelection()
        val ids = plan.changeIds
        if (ids.isNotEmpty()) {
            viewModelScope.launch {
                when (action) {
                    is BatchAction.ChangeDate -> repository.updateTransactionsDateByIds(ids, action.date)
                    is BatchAction.ChangeCategory -> repository.updateTransactionsCategoryByIds(ids, action.category, action.type)
                    is BatchAction.ChangeAccount -> repository.updateTransactionsAssetByIds(ids, action.accountId)
                    is BatchAction.ChangeDescription -> repository.updateTransactionsDescriptionByIds(ids, action.description)
                }
            }
        }
        _batchMessages.tryEmit(plan.summary)
    }

    fun toggleBookmark(record: ExpenseRecord) {
        viewModelScope.launch {
            repository.setBookmarked(id = record.id, isBookmarked = !record.isBookmarked)
        }
    }

    companion object {
        private const val UNDO_WINDOW_MS = 5_000L

        /** Set on the Ledger's back-stack entry when another bottom-bar tab is picked. */
        const val LEFT_LEDGER = "leftLedger"
    }
}
