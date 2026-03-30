package com.sheetsync.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.repository.ExpenseRepository
import com.sheetsync.util.parseFlexibleDate
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
    val monthLabel: String       = "",
    val summary: PeriodSummary   = PeriodSummary(),
    val groups: List<DayGroup>   = emptyList(),
    val calendarCells: List<CalendarCell> = emptyList()
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _month = MutableStateFlow(LocalDate.now().monthValue)
    private val _year  = MutableStateFlow(LocalDate.now().year)

    /** The date the user has tapped in the Calendar grid. */
    var selectedDate: LocalDate? by mutableStateOf(null)
        private set

    private var userAdjustedPeriod = false

    fun selectDate(date: LocalDate) {
        selectedDate = if (selectedDate == date) null else date
    }

    init {
        viewModelScope.launch {
            repository.getAllRecords().collect { all ->
                if (userAdjustedPeriod || all.isEmpty()) return@collect

                val selectedMonthHasData = all.any { record ->
                    parseRecordDate(record)?.let { d ->
                        d.year == _year.value && d.monthValue == _month.value
                    } == true
                }

                if (!selectedMonthHasData) {
                    val latest = all.mapNotNull { parseRecordDate(it) }.maxOrNull()
                    if (latest != null) {
                        _month.value = latest.monthValue
                        _year.value = latest.year
                    }
                }
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

            HistoryUiState(monthLabel, PeriodSummary(income, expense, income - expense), groups, calCells)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryUiState())

    // ── Month navigation ──────────────────────────────────────────────────────

    fun nextMonth() = shiftMonth(1)
    fun prevMonth() = shiftMonth(-1)

    private fun shiftMonth(delta: Long) {
        userAdjustedPeriod = true
        val next = LocalDate.of(_year.value, _month.value, 1).plusMonths(delta)
        _month.value = next.monthValue
        _year.value  = next.year
        selectedDate = null
    }

    fun delete(record: ExpenseRecord) {
        viewModelScope.launch { repository.delete(record) }
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
