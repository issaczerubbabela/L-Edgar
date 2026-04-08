package com.issaczerubbabel.ledgar.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class FilteredTransactionsUiState(
    val selectedYear: Int = LocalDate.now().year,
    val selectedMonth: Int = LocalDate.now().monthValue,
    val monthLabel: String = "",
    val appliedFilters: List<String> = emptyList(),
    val summary: PeriodSummary = PeriodSummary(),
    val groups: List<DayGroup> = emptyList(),
    val calendarCells: List<CalendarCell> = emptyList(),
    val monthGroups: List<MonthGroup> = emptyList()
)

@HiltViewModel
class FilteredTransactionsViewModel @Inject constructor(
    expenseRepository: ExpenseRepository,
    accountRepository: AccountRepository,
    dropdownOptionRepository: DropdownOptionRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val initialYear = savedStateHandle.get<Int>("year")?.takeIf { it > 0 } ?: LocalDate.now().year
    private val initialMonth = savedStateHandle.get<Int>("month")?.takeIf { it in 1..12 } ?: LocalDate.now().monthValue

    private val incomeIdsArg = parseIds(savedStateHandle.get<String>("incomeIds"))
    private val expenseIdsArg = parseIds(savedStateHandle.get<String>("expenseIds"))
    private val accountIdsArg = parseIds(savedStateHandle.get<String>("accountIds"))

    private val selectedYearMonth = MutableStateFlow(YearMonth.of(initialYear, initialMonth))

    var selectedDate: LocalDate? by mutableStateOf(null)
        private set

    private val allRecords = expenseRepository
        .getAllRecords()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val accounts = accountRepository
        .getAllVisibleAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val incomeCategoriesById = dropdownOptionRepository
        .getOptionsByType("INCOME_CATEGORY")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val expenseCategoriesById = dropdownOptionRepository
        .getOptionsByType("EXPENSE_CATEGORY")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<FilteredTransactionsUiState> = combine(
        selectedYearMonth,
        allRecords,
        accounts,
        incomeCategoriesById,
        expenseCategoriesById
    ) { ym, records, accountOptions, incomeOptions, expenseOptions ->
        val incomeNames = incomeOptions.filter { incomeIdsArg.contains(it.id) }.map { it.name }.toSet()
        val expenseNames = expenseOptions.filter { expenseIdsArg.contains(it.id) }.map { it.name }.toSet()
        val accountNames = accountOptions.filter { accountIdsArg.contains(it.id) }.map { it.accountName }
        buildUiState(
            ym = ym,
            records = records,
            incomeCategoryNames = incomeNames,
            expenseCategoryNames = expenseNames,
            accountIds = accountIdsArg,
            accountNames = accountNames
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FilteredTransactionsUiState())

    fun nextMonth() {
        selectedYearMonth.update { it.plusMonths(1) }
        selectedDate = null
    }

    fun prevMonth() {
        selectedYearMonth.update { it.minusMonths(1) }
        selectedDate = null
    }

    fun selectDate(date: LocalDate) {
        selectedDate = if (selectedDate == date) null else date
    }

    private fun buildUiState(
        ym: YearMonth,
        records: List<ExpenseRecord>,
        incomeCategoryNames: Set<String>,
        expenseCategoryNames: Set<String>,
        accountIds: Set<Long>,
        accountNames: List<String>
    ): FilteredTransactionsUiState {
        val monthRecords = records.filter { record ->
            val date = parseRecordDate(record) ?: return@filter false
            if (date.year != ym.year || date.monthValue != ym.monthValue) return@filter false
            matchesFilters(record, incomeCategoryNames, expenseCategoryNames, accountIds)
        }

        val income = monthRecords.filter { it.type == "Income" }.sumOf { it.amount }
        val expense = monthRecords.filter { it.type == "Expense" }.sumOf { it.amount }

        val byDate: Map<LocalDate, List<ExpenseRecord>> = monthRecords
            .mapNotNull { record -> parseRecordDate(record)?.let { it to record } }
            .groupBy({ it.first }, { it.second })

        val groups = byDate.entries
            .sortedByDescending { it.key }
            .map { (date, dayRecords) ->
                val dow = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
                DayGroup(
                    date = date,
                    dayNumber = date.dayOfMonth.toString(),
                    dateLabel = "${date.year}/${date.monthValue.toString().padStart(2, '0')} $dow",
                    dayOfWeekBadge = dow,
                    dayIncome = dayRecords.filter { it.type == "Income" }.sumOf { it.amount },
                    dayExpense = dayRecords.filter { it.type == "Expense" }.sumOf { it.amount },
                    records = dayRecords.sortedByDescending { it.date }
                )
            }

        val firstDay = LocalDate.of(ym.year, ym.monthValue, 1)
        val calendarCells = buildCalendarCells(firstDay = firstDay, today = LocalDate.now(), monthRecordsByDate = byDate)

        val monthGroup = buildMonthGroup(
            year = ym.year,
            month = ym.monthValue,
            records = monthRecords
        )

        return FilteredTransactionsUiState(
            selectedYear = ym.year,
            selectedMonth = ym.monthValue,
            monthLabel = ym.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)),
            appliedFilters = buildAppliedFilters(incomeCategoryNames, expenseCategoryNames, accountNames),
            summary = PeriodSummary(income = income, expense = expense, total = income - expense),
            groups = groups,
            calendarCells = calendarCells,
            monthGroups = listOf(monthGroup)
        )
    }

    private fun buildMonthGroup(year: Int, month: Int, records: List<ExpenseRecord>): MonthGroup {
        val monthStart = LocalDate.of(year, month, 1)
        val monthEnd = YearMonth.of(year, month).atEndOfMonth()

        val monthIncome = records.filter { it.type == "Income" }.sumOf { it.amount }
        val monthExpense = records.filter { it.type == "Expense" }.sumOf { it.amount }

        return MonthGroup(
            monthId = "$year-$month",
            year = year,
            month = month,
            startDate = monthStart,
            endDate = monthEnd,
            monthIncome = monthIncome,
            monthExpense = monthExpense,
            monthTotal = monthIncome - monthExpense,
            weeks = buildWeeklyItems(monthStart, monthEnd, records),
            isExpanded = true
        )
    }

    private fun buildWeeklyItems(
        monthStart: LocalDate,
        monthEnd: LocalDate,
        records: List<ExpenseRecord>
    ): List<WeeklyItem> {
        val items = mutableListOf<WeeklyItem>()
        var weekStart = monthStart

        while (weekStart <= monthEnd) {
            val weekEnd = minOf(weekStart.plusDays(6), monthEnd)
            val weekRecords = records.filter { record ->
                val date = parseRecordDate(record) ?: return@filter false
                date >= weekStart && date <= weekEnd
            }
            val income = weekRecords.filter { it.type == "Income" }.sumOf { it.amount }
            val expense = weekRecords.filter { it.type == "Expense" }.sumOf { it.amount }
            items += WeeklyItem(
                startDate = weekStart,
                endDate = weekEnd,
                income = income,
                expense = expense,
                total = income - expense,
                isHighlighted = false,
                records = weekRecords
            )
            weekStart = weekEnd.plusDays(1)
        }

        return items
    }

    private fun buildCalendarCells(
        firstDay: LocalDate,
        today: LocalDate,
        monthRecordsByDate: Map<LocalDate, List<ExpenseRecord>>
    ): List<CalendarCell> {
        val leadingDays = when (firstDay.dayOfWeek) {
            DayOfWeek.SUNDAY -> 0
            DayOfWeek.MONDAY -> 1
            DayOfWeek.TUESDAY -> 2
            DayOfWeek.WEDNESDAY -> 3
            DayOfWeek.THURSDAY -> 4
            DayOfWeek.FRIDAY -> 5
            DayOfWeek.SATURDAY -> 6
        }

        val cells = mutableListOf<CalendarCell>()

        for (i in leadingDays - 1 downTo 0) {
            val date = firstDay.minusDays((i + 1).toLong())
            cells += emptyCalendarCell(date = date, isCurrentMonth = false, today = today)
        }

        for (day in 1..firstDay.lengthOfMonth()) {
            val date = LocalDate.of(firstDay.year, firstDay.month, day)
            val recs = monthRecordsByDate[date].orEmpty()
            val income = recs.filter { it.type == "Income" }.sumOf { it.amount }
            val expense = recs.filter { it.type == "Expense" }.sumOf { it.amount }
            cells += CalendarCell(
                date = date,
                isCurrentMonth = true,
                isToday = date == today,
                dayIncome = income,
                dayExpense = expense,
                dayNet = income - expense,
                categories = recs.map { it.category }.distinct(),
                totalTransactions = recs.size
            )
        }

        val target = if (cells.size <= 35) 35 else 42
        var nextDay = 1
        while (cells.size < target) {
            val date = firstDay.plusMonths(1).withDayOfMonth(nextDay++)
            cells += emptyCalendarCell(date = date, isCurrentMonth = false, today = today)
        }

        return cells
    }

    private fun emptyCalendarCell(date: LocalDate, isCurrentMonth: Boolean, today: LocalDate): CalendarCell =
        CalendarCell(
            date = date,
            isCurrentMonth = isCurrentMonth,
            isToday = date == today,
            dayIncome = 0.0,
            dayExpense = 0.0,
            dayNet = 0.0,
            categories = emptyList(),
            totalTransactions = 0
        )

    private fun matchesFilters(
        record: ExpenseRecord,
        incomeCategoryNames: Set<String>,
        expenseCategoryNames: Set<String>,
        accountIds: Set<Long>
    ): Boolean {
        val hasAnyFilter = incomeCategoryNames.isNotEmpty() || expenseCategoryNames.isNotEmpty() || accountIds.isNotEmpty()
        if (!hasAnyFilter) return true

        val matchesIncomeCategory =
            record.type == "Income" && incomeCategoryNames.contains(record.category)

        val matchesExpenseCategory =
            record.type == "Expense" && expenseCategoryNames.contains(record.category)

        val matchesAccount = accountIds.any { accountId ->
            record.accountId == accountId || record.fromAccountId == accountId || record.toAccountId == accountId
        }

        return matchesIncomeCategory || matchesExpenseCategory || matchesAccount
    }

    private fun parseRecordDate(record: ExpenseRecord): LocalDate? =
        parseFlexibleDate(record.date) ?: record.remoteTimestamp?.let(::parseFlexibleDate)

    private fun parseIds(raw: String?): Set<Long> = raw
        ?.split(',')
        ?.mapNotNull { token -> token.trim().toLongOrNull() }
        ?.toSet()
        ?: emptySet()

    private fun buildAppliedFilters(
        incomeCategoryNames: Set<String>,
        expenseCategoryNames: Set<String>,
        accountNames: List<String>
    ): List<String> {
        val filters = mutableListOf<String>()
        if (incomeCategoryNames.isNotEmpty()) {
            filters += "Income: ${incomeCategoryNames.sorted().joinToString(", ")}"
        }
        if (expenseCategoryNames.isNotEmpty()) {
            filters += "Expenses: ${expenseCategoryNames.sorted().joinToString(", ")}"
        }
        if (accountNames.isNotEmpty()) {
            filters += "Accounts: ${accountNames.sorted().joinToString(", ")}"
        }
        return filters.ifEmpty { listOf("All transactions") }
    }
}
