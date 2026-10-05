package com.issaczerubbabel.ledgar.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class TripSummaryTest {

    private val all = listOf(1L, 2L, 3L, 4L)

    private fun e(id: Long, amount: Long, category: String, date: String, ids: List<Long> = all, payer: Long = 1) =
        TripExpenseInput(id, date, "Item $id", amount, payer, SplitMode.EQUAL, ids, emptyMap(), category)

    private val expenses = listOf(
        e(1, 480000, "Transportation", "2026-10-10"),
        e(2, 264000, "Food & Snacks", "2026-10-10"),
        e(3, 900000, "", "2026-10-10"),
        e(4, 78000, "Food & Snacks", "2026-10-12"),
        e(5, 300000, "Fun", "2026-10-12", ids = listOf(2, 3, 4))
    )

    @Test
    fun categoryTotalsForEveryoneAreLargestFirstWithUncategorisedGrouped() {
        assertEquals(
            listOf(CategoryTotal("", 900000), CategoryTotal("Transportation", 480000), CategoryTotal("Fun", 300000), CategoryTotal("Food & Snacks", 342000)).sortedByDescending { it.paise },
            TripSummary.categoryTotals(expenses, selfId = 1, justMe = false)
        )
    }

    @Test
    fun justMeCountsOnlyYourShareAndSkipsExpensesYouWereNotIn() {
        assertEquals(
            listOf(CategoryTotal("", 225000), CategoryTotal("Transportation", 120000), CategoryTotal("Food & Snacks", 85500)),
            TripSummary.categoryTotals(expenses, selfId = 1, justMe = true)
        )
    }

    @Test
    fun dayTotalsFillTheDaysBetween() {
        assertEquals(
            listOf(DayTotal("2026-10-10", 1644000), DayTotal("2026-10-11", 0), DayTotal("2026-10-12", 378000)),
            TripSummary.dayTotals(expenses)
        )
        assertEquals(emptyList<DayTotal>(), TripSummary.dayTotals(emptyList()))
    }

    @Test
    fun biggestExpensesAreLargestFirst() {
        assertEquals(listOf(3L, 1L, 5L), TripSummary.biggest(expenses, 3).map { it.id })
    }

    @Test
    fun countsUncategorisedExpensesAndAveragesPerPerson() {
        assertEquals(1, TripSummary.uncategorisedCount(expenses))
        assertEquals(186000, TripSummary.averagePerMemberPaise(744000, 4))
        assertEquals(0, TripSummary.averagePerMemberPaise(744000, 0))
    }
}
