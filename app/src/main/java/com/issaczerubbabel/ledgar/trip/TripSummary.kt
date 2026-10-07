package com.issaczerubbabel.ledgar.trip

import java.time.LocalDate

data class CategoryTotal(val category: String, val paise: Long) {
    val isUncategorised: Boolean get() = category.isEmpty()
}

data class DayTotal(val date: String, val paise: Long)

/** The numbers behind the Summary tab's charts. */
object TripSummary {

    /**
     * Spending by Category, largest first. With [justMe], each expense counts only [selfId]'s Share,
     * which is what Post adds to the ledger. Expenses with no Category are grouped under "".
     */
    fun categoryTotals(expenses: List<TripExpenseInput>, selfId: Long?, justMe: Boolean): List<CategoryTotal> {
        val totals = LinkedHashMap<String, Long>()
        expenses.forEach { e ->
            val value = if (justMe) (selfId?.let { TripMath.shares(e)[it] } ?: 0L) else e.amountPaise
            if (value > 0) totals.merge(e.category, value, Long::plus)
        }
        return totals.map { CategoryTotal(it.key, it.value) }
            .sortedWith(compareByDescending<CategoryTotal> { it.paise }.thenBy { it.category })
    }

    /** One total per day from the first to the last expense, with zero for days in between. */
    fun dayTotals(expenses: List<TripExpenseInput>): List<DayTotal> {
        if (expenses.isEmpty()) return emptyList()
        val byDate = expenses.groupBy { it.date }.mapValues { (_, list) -> list.sumOf { it.amountPaise } }
        val first = LocalDate.parse(byDate.keys.min())
        val last = LocalDate.parse(byDate.keys.max())
        val out = mutableListOf<DayTotal>()
        var day = first
        while (!day.isAfter(last)) {
            out += DayTotal(day.toString(), byDate[day.toString()] ?: 0L)
            day = day.plusDays(1)
        }
        return out
    }

    fun biggest(expenses: List<TripExpenseInput>, count: Int = 5): List<TripExpenseInput> =
        expenses.sortedWith(compareByDescending<TripExpenseInput> { it.amountPaise }.thenBy { it.date }.thenBy { it.id }).take(count)

    fun uncategorisedCount(expenses: List<TripExpenseInput>): Int = expenses.count { it.category.isBlank() }

    fun averagePerMemberPaise(totalPaise: Long, memberCount: Int): Long =
        if (memberCount <= 0) 0L else Math.round(totalPaise.toDouble() / memberCount)
}
