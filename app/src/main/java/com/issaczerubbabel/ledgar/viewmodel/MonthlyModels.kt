package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import java.time.LocalDate

/**
 * Represents a single week's aggregated data.
 * Shows: date range, income amount, expense amount, and net total.
 * One WeeklyItem may be highlighted (darker background) as shown in the UI.
 */
data class WeeklyItem(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val income: Double,
    val expense: Double,
    val total: Double,  // income - expense (can be negative)
    val isHighlighted: Boolean = false,  // For the special background styling shown in image_7.png
    val records: List<ExpenseRecord> = emptyList()  // Raw transaction records for this week
)

/**
 * Represents a month with all its weekly breakdown items.
 * Can be expanded or collapsed.
 */
data class MonthGroup(
    val monthId: String,        // e.g., "Mar", "Feb", "Jan"
    val year: Int,
    val month: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val monthIncome: Double,
    val monthExpense: Double,
    val monthTotal: Double,     // income - expense
    val weeks: List<WeeklyItem> = emptyList(),
    val isExpanded: Boolean = false
)

/**
 * UI state for the Monthly tab.
 * Contains all data needed to render the collapsible month groups.
 */
data class MonthlyTabUiState(
    val selectedYear: Int = LocalDate.now().year,
    val summary: PeriodSummary = PeriodSummary(),  // Aggregated totals for selected year
    val monthGroups: List<MonthGroup> = emptyList(),  // List of months with expanded/collapsed state
    val isLoading: Boolean = false
)
