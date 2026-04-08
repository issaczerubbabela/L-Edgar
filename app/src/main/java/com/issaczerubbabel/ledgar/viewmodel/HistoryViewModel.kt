package com.issaczerubbabel.ledgar.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.sync.SyncWorker
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

// ── Data models ───────────────────────────────────────────────────────────────

data class PeriodSummary(
    val income: Double  = 0.0,
    val expense: Double = 0.0,
    val total: Double   = 0.0
)

data class DayGroup(
    val date: LocalDate,
    val dayNumber: String,
    val dateLabel: String,
    val dayOfWeekBadge: String,
    val dayIncome: Double,
    val dayExpense: Double,
    val records: List<ExpenseRecord>
)

data class CalendarCell(
    val date: LocalDate,
    val isCurrentMonth: Boolean,
    val isToday: Boolean,
    val dayIncome: Double,
    val dayExpense: Double,
    val dayNet: Double,          // positive = net income, negative = net expense
    val categories: List<String>,// distinct categories for dot colours
    val totalTransactions: Int
)

data class HistoryUiState(
    val selectedMonth: Int      = LocalDate.now().monthValue,
    val selectedYear: Int       = LocalDate.now().year,
    val monthLabel: String       = "",
    val summary: PeriodSummary   = PeriodSummary(),
    val groups: List<DayGroup>   = emptyList(),
    val calendarCells: List<CalendarCell> = emptyList()
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val workManager: WorkManager,
    accountRepository: AccountRepository,
    dropdownOptionRepository: DropdownOptionRepository
) : ViewModel() {

    private val _month = MutableStateFlow(LocalDate.now().monthValue)
    private val _year  = MutableStateFlow(LocalDate.now().year)
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

                val latest = records
                    .mapNotNull { parseRecordDate(it) }
                    .maxOrNull() ?: return@collect

                // Default the tab to the most recent available month so imported
                // historical records are visible immediately after import.
                if (_month.value != latest.monthValue || _year.value != latest.year) {
                    _month.value = latest.monthValue
                    _year.value = latest.year
                    selectedDate = null
                }
                shouldAutoFocusLatestMonth = false
            }
        }
    }

    val uiState: StateFlow<HistoryUiState> =
        combine(repository.getAllRecords(), _month, _year) { records, month, year ->

            val monthDate  = LocalDate.of(year, month, 1)
            val monthLabel = monthDate.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH))
            val today      = LocalDate.now()

            // ── Filter to current month ────────────────────────────────────
            val monthRecords = records.filter { r ->
                runCatching { parseRecordDate(r) }
                    .getOrNull()?.let { d -> d.monthValue == month && d.year == year } == true
            }

            val income  = monthRecords.filter { it.type == "Income"  }.sumOf { it.amount }
            val expense = monthRecords.filter { it.type == "Expense" }.sumOf { it.amount }

            // ── Group by date for both Daily list and Calendar ─────────────
            val byDate: Map<LocalDate, List<ExpenseRecord>> = monthRecords
                .mapNotNull { r ->
                    parseRecordDate(r)?.let { it to r }
                }
                .groupBy({ it.first }, { it.second })

            // ── Daily groups (for the Daily tab list) ──────────────────────
            val groups = byDate.entries
                .sortedByDescending { it.key }
                .map { (date, recs) ->
                    val dow = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                    DayGroup(
                        date           = date,
                        dayNumber      = date.dayOfMonth.toString(),
                        dateLabel      = "${date.year}/${date.monthValue.toString().padStart(2, '0')} $dow",
                        dayOfWeekBadge = dow,
                        dayIncome      = recs.filter { it.type == "Income"  }.sumOf { it.amount },
                        dayExpense     = recs.filter { it.type == "Expense" }.sumOf { it.amount },
                        records        = recs.sortedByDescending { it.date }
                    )
                }

            // ── Calendar cells (7 × 5 or 7 × 6 grid) ─────────────────────
            val calCells = buildCalendarCells(monthDate, today, byDate, records)

            HistoryUiState(
                selectedMonth = month,
                selectedYear = year,
                monthLabel = monthLabel,
                summary = PeriodSummary(income, expense, income - expense),
                groups = groups,
                calendarCells = calCells
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    // ── Month navigation ──────────────────────────────────────────────────────

    fun nextMonth() = shiftMonth(1)
    fun prevMonth() = shiftMonth(-1)

    fun setMonthYear(year: Int, month: Int) {
        val safeMonth = month.coerceIn(1, 12)
        val safeYear = year.coerceIn(1900, 2100)
        shouldAutoFocusLatestMonth = false
        _month.value = safeMonth
        _year.value = safeYear
        selectedDate = null
        clearSelection()
    }

    private fun shiftMonth(delta: Long) {
        shouldAutoFocusLatestMonth = false
        val next = LocalDate.of(_year.value, _month.value, 1).plusMonths(delta)
        _month.value = next.monthValue
        _year.value  = next.year
        selectedDate = null
        clearSelection()
    }

    fun delete(record: ExpenseRecord) {
        viewModelScope.launch {
            repository.delete(record)
            enqueueSyncWork()
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
            enqueueSyncWork()
        }
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

    // ── Calendar grid builder ─────────────────────────────────────────────────

    private fun buildCalendarCells(
        firstDay: LocalDate,
        today: LocalDate,
        byDate: Map<LocalDate, List<ExpenseRecord>>,
        allRecords: List<ExpenseRecord>
    ): List<CalendarCell> {

        // Also need out-of-month cells to show no-amount but maybe different month records
        val allByDate: Map<LocalDate, List<ExpenseRecord>> = allRecords
            .mapNotNull { r -> parseRecordDate(r)?.let { it to r } }
            .groupBy({ it.first }, { it.second })

        val leadingDays = when (firstDay.dayOfWeek) {
            DayOfWeek.SUNDAY    -> 0
            DayOfWeek.MONDAY    -> 1
            DayOfWeek.TUESDAY   -> 2
            DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY  -> 4
            DayOfWeek.FRIDAY    -> 5
            DayOfWeek.SATURDAY  -> 6
            else                -> 0
        }

        val cells = mutableListOf<CalendarCell>()

        // Previous month trailing days
        for (i in leadingDays - 1 downTo 0) {
            val date = firstDay.minusDays((i + 1).toLong())
            cells += makeCell(date, isCurrentMonth = false, today, allByDate)
        }

        // Current month
        for (day in 1..firstDay.lengthOfMonth()) {
            val date = LocalDate.of(firstDay.year, firstDay.month, day)
            cells += makeCell(date, isCurrentMonth = true, today, byDate)
        }

        // Next month leading days to complete grid (35 or 42 cells)
        val target = if (cells.size <= 35) 35 else 42
        var nextDay = 1
        while (cells.size < target) {
            val date = firstDay.plusMonths(1).withDayOfMonth(nextDay++)
            cells += makeCell(date, isCurrentMonth = false, today, allByDate)
        }

        return cells
    }

    private fun makeCell(
        date: LocalDate,
        isCurrentMonth: Boolean,
        today: LocalDate,
        byDate: Map<LocalDate, List<ExpenseRecord>>
    ): CalendarCell {
        val recs    = byDate[date] ?: emptyList()
        val income  = recs.filter { it.type == "Income"  }.sumOf { it.amount }
        val expense = recs.filter { it.type == "Expense" }.sumOf { it.amount }
        return CalendarCell(
            date              = date,
            isCurrentMonth    = isCurrentMonth,
            isToday           = date == today,
            dayIncome         = income,
            dayExpense        = expense,
            dayNet            = income - expense,
            categories        = recs.map { it.category }.distinct(),
            totalTransactions = recs.size
        )
    }

    private fun parseRecordDate(record: ExpenseRecord): LocalDate? =
        parseFlexibleDate(record.date) ?: record.remoteTimestamp?.let(::parseFlexibleDate)
}
