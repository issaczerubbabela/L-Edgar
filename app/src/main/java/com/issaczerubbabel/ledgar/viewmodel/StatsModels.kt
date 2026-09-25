package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import java.time.LocalDate

enum class StatsScope {
    CYCLE,
    WEEKLY,
    MONTHLY,
    YEARLY,
    SELECT_PERIOD
}

data class StatsFilterState(
    val scope: StatsScope = StatsScope.MONTHLY,
    val anchorDate: LocalDate = LocalDate.now(),
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
    /** True once the user picked a scope, so the default (Cycle when one exists) no longer applies. */
    val scopeChosen: Boolean = false
) {
    /** The period on screen. Cycle scope falls back to Month when there are no cycles. */
    fun period(cycles: List<BudgetCycle>, today: LocalDate): StatsPeriod =
        if (scope == StatsScope.CYCLE) {
            StatsPeriod.cycleOn(anchorDate, cycles, today) ?: StatsPeriod.of(StatsScope.MONTHLY, anchorDate)
        } else {
            StatsPeriod.of(scope, anchorDate, customStartDate, customEndDate)
        }
}

data class StatsDateRange(
    val start: LocalDate,
    val end: LocalDate
)
