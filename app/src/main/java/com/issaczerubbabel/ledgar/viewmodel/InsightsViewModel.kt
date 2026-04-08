package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import javax.inject.Inject

data class InsightsSummary(
    val totalIncome: Double,
    val totalExpense: Double,
    val balance: Double,
    val categoryTotals: Map<String, Double>,  // expense categories this month
    val monthlyExpenses: List<Pair<String, Double>> // last 6 months label -> total
)

@HiltViewModel
class InsightsViewModel @Inject constructor(
    repository: ExpenseRepository
) : ViewModel() {

    val summary: StateFlow<InsightsSummary> = repository.getAllRecords()
        .map { records ->
            val now = LocalDate.now()
            val thisMonth = records.filter {
                runCatching { LocalDate.parse(it.date) }.getOrNull()
                    ?.let { d -> d.year == now.year && d.monthValue == now.monthValue } == true
            }
            val totalIncome = thisMonth.filter { it.type == "Income" }.sumOf { it.amount }
            val totalExpense = thisMonth.filter { it.type == "Expense" }.sumOf { it.amount }
            val categoryTotals = thisMonth
                .filter { it.type == "Expense" }
                .groupBy { it.category }
                .mapValues { (_, v) -> v.sumOf { it.amount } }
                .entries.sortedByDescending { it.value }
                .associate { it.key to it.value }

            // Last 6 months monthly expense totals
            val monthlyExpenses = (5 downTo 0).map { offset ->
                val month = now.minusMonths(offset.toLong())
                val label = "${month.month.name.take(3)} ${month.year}"
                val total = records.filter { r ->
                    r.type == "Expense" &&
                    runCatching { LocalDate.parse(r.date) }.getOrNull()
                        ?.let { d -> d.year == month.year && d.monthValue == month.monthValue } == true
                }.sumOf { it.amount }
                label to total
            }

            InsightsSummary(totalIncome, totalExpense, totalIncome - totalExpense, categoryTotals, monthlyExpenses)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InsightsSummary(0.0, 0.0, 0.0, emptyMap(), emptyList()))
}
