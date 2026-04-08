package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

@HiltViewModel
class MonthlyViewModel @Inject constructor(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _selectedYear = MutableStateFlow(LocalDate.now().year)
    private val _expandedMonths = MutableStateFlow(setOf(monthKey(LocalDate.now().year, LocalDate.now().monthValue)))

    val uiState: StateFlow<MonthlyTabUiState> =
        combine(repository.getAllRecords(), _selectedYear, _expandedMonths) { records, year, expanded ->
            computeMonthlyUiState(records, year, expanded)
        }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = MonthlyTabUiState(isLoading = true)
            )

    fun nextYear() {
        _selectedYear.update { it + 1 }
    }

    fun prevYear() {
        _selectedYear.update { it - 1 }
    }

    fun toggleMonthExpanded(monthId: String) {
        _expandedMonths.update { expanded ->
            if (expanded.contains(monthId)) expanded - monthId else expanded + monthId
        }
    }

    private fun computeMonthlyUiState(
        allRecords: List<ExpenseRecord>,
        selectedYear: Int,
        expandedMonths: Set<String>
    ): MonthlyTabUiState {
        val today = LocalDate.now()
        val parsed = allRecords.mapNotNull { record ->
            parseRecordDate(record)?.let { date -> date to record }
        }

        val yearRecords = parsed
            .filter { (date, _) -> date.year == selectedYear }
            .map { it.second }

        val yearIncome = yearRecords.filter { it.type == "Income" }.sumOf { it.amount }
        val yearExpense = yearRecords.filter { it.type == "Expense" }.sumOf { it.amount }

        val maxMonth = when {
            selectedYear < today.year -> 12
            selectedYear == today.year -> today.monthValue
            else -> 0
        }

        val monthRange = if (maxMonth == 0) emptyList() else (maxMonth downTo 1).toList()

        val monthGroups = monthRange.map { month ->
            val key = monthKey(selectedYear, month)
            buildMonthGroup(
                year = selectedYear,
                month = month,
                records = yearRecords,
                isExpanded = expandedMonths.contains(key)
            )
        }

        return MonthlyTabUiState(
            selectedYear = selectedYear,
            summary = PeriodSummary(
                income = yearIncome,
                expense = yearExpense,
                total = yearIncome - yearExpense
            ),
            monthGroups = monthGroups,
            isLoading = false
        )
    }

    private fun buildMonthGroup(
        year: Int,
        month: Int,
        records: List<ExpenseRecord>,
        isExpanded: Boolean
    ): MonthGroup {
        val firstDay = LocalDate.of(year, month, 1)
        val lastDay = YearMonth.of(year, month).atEndOfMonth()

        val monthRecords = records.filter { record ->
            runCatching { parseRecordDate(record) }
                .getOrNull()
                ?.let { date -> date.monthValue == month && date.year == year } == true
        }

        val monthIncome = monthRecords.filter { it.type == "Income" }.sumOf { it.amount }
        val monthExpense = monthRecords.filter { it.type == "Expense" }.sumOf { it.amount }

        return MonthGroup(
            monthId = monthKey(year, month),
            year = year,
            month = month,
            startDate = firstDay,
            endDate = lastDay,
            monthIncome = monthIncome,
            monthExpense = monthExpense,
            monthTotal = monthIncome - monthExpense,
            weeks = if (isExpanded) buildWeeklyItems(firstDay, lastDay, monthRecords) else emptyList(),
            isExpanded = isExpanded
        )
    }

    private fun buildWeeklyItems(
        monthStart: LocalDate,
        monthEnd: LocalDate,
        monthRecords: List<ExpenseRecord>
    ): List<WeeklyItem> {
        val weekItems = mutableListOf<WeeklyItem>()
        var weekStart = monthStart

        while (weekStart <= monthEnd) {
            val weekEnd = minOf(weekStart.plusDays(6), monthEnd)

            val weekRecords = monthRecords.filter { record ->
                runCatching { parseRecordDate(record) }
                    .getOrNull()
                    ?.let { date -> date >= weekStart && date <= weekEnd } == true
            }

            val income = weekRecords.filter { it.type == "Income" }.sumOf { it.amount }
            val expense = weekRecords.filter { it.type == "Expense" }.sumOf { it.amount }

            weekItems += WeeklyItem(
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

        return weekItems
    }

    private companion object {
        fun monthKey(year: Int, month: Int): String = "$year-$month"
    }

    private fun parseRecordDate(record: ExpenseRecord): LocalDate? =
        parseFlexibleDate(record.date) ?: record.remoteTimestamp?.let(::parseFlexibleDate)
}
