package com.issaczerubbabel.ledgar.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripExportsTest {

    private val members = listOf(
        TripMember(1, "You", isSelf = true), TripMember(2, "Rahul"), TripMember(3, "Priya"), TripMember(4, "Arjun")
    )
    private val all = members.map { it.id }

    private fun e(id: Long, amount: Long, payer: Long, purpose: String, date: String = "2026-10-10", ids: List<Long> = all, category: String = "") =
        TripExpenseInput(id, date, purpose, amount, payer, SplitMode.EQUAL, ids, emptyMap(), category)

    private val expenses = listOf(e(1, 480000, 1, "Train tickets", category = "Transportation"), e(2, 264000, 2, "Lunch, \"fish\"", date = "2026-10-11"))

    @Test
    fun summaryTextForTheGroupChat() {
        assertEquals(
            "Goa trip\n" +
                "Total spent: ₹7,440\n\n" +
                "Shares\n" +
                "• You: ₹1,860 (paid ₹4,800)\n" +
                "• Rahul: ₹1,860 (paid ₹2,640)\n" +
                "• Priya: ₹1,860 (paid ₹0)\n" +
                "• Arjun: ₹1,860 (paid ₹0)\n\n" +
                "To settle up\n" +
                "• Priya pays You ₹1,860\n" +
                "• Arjun pays You ₹1,080\n" +
                "• Arjun pays Rahul ₹780",
            TripExports.summaryText("Goa trip", members, expenses, emptyList())
        )
    }

    @Test
    fun aStatementHasOnlyOnePersonsLines() {
        assertEquals(
            "Goa trip · Arjun\n\n" +
                "• 10 Oct  Train tickets: Share ₹1,200\n" +
                "• 11 Oct  Lunch, \"fish\": Share ₹660\n\n" +
                "Paid ₹0 · Share ₹1,860\n" +
                "Pay You ₹1,080\n" +
                "Pay Rahul ₹780",
            TripExports.statementText("Goa trip", 4, members, expenses, emptyList())
        )
    }

    @Test
    fun aStatementForSomeoneOwedMoneyListsWhoPaysThem() {
        assertEquals(
            "Goa trip · You\n\n" +
                "• 10 Oct  Train tickets: paid ₹4,800, Share ₹1,200\n" +
                "• 11 Oct  Lunch, \"fish\": Share ₹660\n\n" +
                "Paid ₹4,800 · Share ₹1,860\n" +
                "Priya pays you ₹1,860\n" +
                "Arjun pays you ₹1,080",
            TripExports.statementText("Goa trip", 1, members, expenses, emptyList())
        )
    }

    @Test
    fun aSettledPersonIsToldSo() {
        val settled = listOf(e(1, 40000, 1, "Cab", ids = listOf(1, 2)))
        val text = TripExports.statementText("Trip", 3, members, settled, emptyList())
        assertEquals("Trip · Priya\n\nNothing recorded yet.\n\nPaid ₹0 · Share ₹0\nYou're settled.", text)
    }

    @Test
    fun expensesCsvHasAColumnPerPersonAndQuotesWhenNeeded() {
        assertEquals(
            "date,purpose,category,amount,paid_by,You,Rahul,Priya,Arjun\n" +
                "2026-10-10,Train tickets,Transportation,4800.00,You,1200.00,1200.00,1200.00,1200.00\n" +
                "2026-10-11,\"Lunch, \"\"fish\"\"\",,2640.00,Rahul,660.00,660.00,660.00,660.00",
            TripExports.expensesCsv(members, expenses)
        )
    }

    @Test
    fun balancesCsvHasOneRowPerPersonThenTheSettlements() {
        val settlements = listOf(TripSettlementInput(3, 1, 100000))
        assertEquals(
            "person,paid,share,settled,balance\n" +
                "You,4800.00,1860.00,-1000.00,1940.00\n" +
                "Rahul,2640.00,1860.00,0.00,780.00\n" +
                "Priya,0.00,1860.00,1000.00,-860.00\n" +
                "Arjun,0.00,1860.00,0.00,-1860.00\n" +
                "\n" +
                "settlement_from,settlement_to,amount\n" +
                "Priya,You,1000.00",
            TripExports.balancesCsv(members, expenses, settlements)
        )
    }

    @Test
    fun theReportCoversPeopleExpensesAndThePlan() {
        val blocks = TripExports.reportBlocks("Goa trip", "10–12 Oct 2026", members, expenses, emptyList())
        assertEquals(ReportBlock.Title("Goa trip"), blocks.first())
        assertEquals(
            listOf("People", "Expenses", "Settle up"),
            blocks.filterIsInstance<ReportBlock.Heading>().map { it.text }
        )
        assertTrue(blocks.contains(ReportBlock.Line("Arjun pays Rahul ₹780")))
        assertEquals(2, blocks.filterIsInstance<ReportBlock.Row>().count { it.cells.first().contains(" Oct") })
    }
}
