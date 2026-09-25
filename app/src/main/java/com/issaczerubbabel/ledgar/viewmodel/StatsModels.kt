package com.issaczerubbabel.ledgar.viewmodel

import androidx.compose.ui.graphics.Color
import java.time.LocalDate

enum class StatsScope {
    WEEKLY,
    MONTHLY,
    YEARLY,
    SELECT_PERIOD
}

enum class StatsBreakdownTab {
    EXPENSE,
    INCOME
}

data class StatsFilterState(
    val scope: StatsScope = StatsScope.MONTHLY,
    val anchorDate: LocalDate = LocalDate.now(),
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
    val breakdownTab: StatsBreakdownTab = StatsBreakdownTab.EXPENSE,
    val cashFlowCategory: String? = null
) {
    val period: StatsPeriod get() = StatsPeriod.of(scope, anchorDate, customStartDate, customEndDate)
}

data class StatsDateRange(
    val start: LocalDate,
    val end: LocalDate
)

data class CategoryTotal(
    val categoryName: String,
    val totalAmount: Double,
    val assignedColor: Color
)
