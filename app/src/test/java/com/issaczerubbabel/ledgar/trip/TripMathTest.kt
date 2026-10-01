package com.issaczerubbabel.ledgar.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripMathTest {

    private val you = TripMember(1, "You", isSelf = true)
    private val rahul = TripMember(2, "Rahul")
    private val priya = TripMember(3, "Priya")
    private val arjun = TripMember(4, "Arjun")
    private val members = listOf(you, rahul, priya, arjun)
    private val all = members.map { it.id }

    private fun expense(
        id: Long,
        amount: Long,
        payer: Long,
        mode: SplitMode = SplitMode.EQUAL,
        ids: List<Long> = all,
        inputs: Map<Long, Long> = emptyMap(),
        date: String = "2026-10-10",
        purpose: String = "Item $id",
        category: String = "Food & Snacks"
    ) = TripExpenseInput(id, date, purpose, amount, payer, mode, ids, inputs, category)

    @Test
    fun equalSplitThatDividesEvenly() {
        assertEquals(mapOf(1L to 120000L, 2L to 120000L, 3L to 120000L, 4L to 120000L), TripMath.shares(expense(1, 480000, 1)))
    }

    @Test
    fun leftoverPaiseRotateByExpenseId() {
        // ₹100 between 3 leaves one paisa over; it goes to the Member the expense id points at.
        assertEquals(mapOf(1L to 3334L, 2L to 3333L, 3L to 3333L), TripMath.shares(expense(3, 10000, 1, ids = listOf(1, 2, 3))))
        assertEquals(mapOf(1L to 3333L, 2L to 3334L, 3L to 3333L), TripMath.shares(expense(4, 10000, 1, ids = listOf(1, 2, 3))))
        // ₹100.01 leaves two paise over: they go to the next two Members in the rotation.
        assertEquals(mapOf(1L to 3333L, 2L to 3334L, 3L to 3334L), TripMath.shares(expense(1, 10001, 1, ids = listOf(1, 2, 3))))
    }

    @Test
    fun adjustAddsTheExtraOnTopOfAnEqualSplit() {
        // ₹3,460 with Arjun +₹300: ₹3,160 split four ways is ₹790 each.
        val e = expense(7, 346000, 1, SplitMode.ADJUST, inputs = mapOf(4L to 30000L))
        assertEquals(mapOf(1L to 79000L, 2L to 79000L, 3L to 79000L, 4L to 109000L), TripMath.shares(e))
        assertNull(TripMath.splitProblem(e))
    }

    @Test
    fun adjustLargerThanTheAmountIsAProblem() {
        val e = expense(7, 10000, 1, SplitMode.ADJUST, inputs = mapOf(4L to 20000L))
        assertEquals(SplitProblem.AdjustTooLarge, TripMath.splitProblem(e))
    }

    @Test
    fun exactSplitMustAddUp() {
        val ok = expense(8, 120000, 3, SplitMode.EXACT, ids = listOf(1, 3), inputs = mapOf(1L to 40000L, 3L to 80000L))
        assertEquals(mapOf(1L to 40000L, 3L to 80000L), TripMath.shares(ok))
        assertNull(TripMath.splitProblem(ok))
        assertEquals(SplitProblem.ExactLeft(20000), TripMath.splitProblem(ok.copy(inputs = mapOf(1L to 40000L, 3L to 60000L))))
        assertEquals(SplitProblem.ExactLeft(-10000), TripMath.splitProblem(ok.copy(inputs = mapOf(1L to 50000L, 3L to 80000L))))
        assertEquals(SplitProblem.NoMembers, TripMath.splitProblem(ok.copy(memberIds = emptyList())))
    }

    @Test
    fun balancesAreWhatYouPaidMinusYourShares() {
        val expenses = listOf(expense(1, 480000, 1), expense(2, 264000, 2))
        val b = TripMath.balances(members, expenses, emptyList())
        assertEquals(listOf(480000L - 186000L, 264000L - 186000L, -186000L, -186000L), b.map { it.netPaise })
        assertEquals(listOf(0L, 0L, 0L, 0L), b.map { it.settledPaise })
    }

    @Test
    fun settlementsMoveBalancesTowardsZeroWithoutChangingShares() {
        val expenses = listOf(expense(1, 480000, 1))
        val b = TripMath.balances(members, expenses, listOf(TripSettlementInput(fromId = 3, toId = 1, amountPaise = 120000)))
        assertEquals(listOf(240000L, -120000L, 0L, -120000L), b.map { it.netPaise })
        assertEquals(listOf(120000L, 120000L, 120000L, 120000L), b.map { it.sharePaise })
    }

    @Test
    fun settleUpPlanUsesTheFewestPayments() {
        val expenses = listOf(expense(1, 480000, 1), expense(2, 264000, 2))
        val plan = TripMath.settleUpPlan(TripMath.balances(members, expenses, emptyList()))
        // You +2,94,000 · Rahul +78,000 · Priya −1,86,000 · Arjun −1,86,000: Priya before Arjun on a tie.
        assertEquals(
            listOf(
                PlannedPayment(3, 1, 186000),
                PlannedPayment(4, 1, 108000),
                PlannedPayment(4, 2, 78000)
            ),
            plan
        )
    }

    @Test
    fun recordingOnePaymentKeepsTheRestOfThePlan() {
        val expenses = listOf(expense(1, 480000, 1), expense(2, 264000, 2))
        val after = TripMath.settleUpPlan(
            TripMath.balances(members, expenses, listOf(TripSettlementInput(3, 1, 186000)))
        )
        assertEquals(listOf(PlannedPayment(4, 1, 108000), PlannedPayment(4, 2, 78000)), after)
    }

    @Test
    fun everyoneSettledMeansAnEmptyPlan() {
        val expenses = listOf(expense(1, 40000, 1, ids = listOf(1, 2)))
        assertEquals(emptyList<PlannedPayment>(), TripMath.settleUpPlan(TripMath.balances(members, expenses, listOf(TripSettlementInput(2, 1, 20000)))))
    }

    @Test
    fun postPreviewSkipsExpensesWhereYourShareIsZero() {
        val expenses = listOf(
            expense(6, 300000, 2, ids = listOf(2, 3, 4), date = "2026-10-11", purpose = "Parasailing", category = ""),
            expense(1, 480000, 1, purpose = "Train tickets", category = "Transportation"),
            expense(3, 900000, 3, date = "2026-10-10", purpose = "Hotel", category = "")
        )
        assertEquals(
            listOf(
                PostPreviewRow(1, "2026-10-10", "Goa trip · Train tickets", "Transportation", 120000),
                PostPreviewRow(3, "2026-10-10", "Goa trip · Hotel", "", 225000)
            ),
            TripMath.postPreview("Goa trip", selfId = 1, expenses = expenses)
        )
    }

    @Test
    fun summaryTextForTheGroupChat() {
        val expenses = listOf(expense(1, 480000, 1), expense(2, 264000, 2))
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
            TripMath.summaryText("Goa trip", members, expenses, emptyList())
        )
    }

    @Test
    fun csvHasAColumnPerMemberAndQuotesWhenNeeded() {
        val expenses = listOf(
            expense(2, 10000, 2, ids = listOf(1, 2, 3), purpose = "Chai, \"cutting\"", date = "2026-10-11", category = ""),
            expense(1, 40000, 1, ids = listOf(1, 2), purpose = "Petrol", category = "Transportation")
        )
        assertEquals(
            "date,purpose,category,amount,paid_by,You,Rahul,Priya,Arjun\n" +
                "2026-10-10,Petrol,Transportation,400.00,You,200.00,200.00,0.00,0.00\n" +
                "2026-10-11,\"Chai, \"\"cutting\"\"\",,100.00,Rahul,33.33,33.33,33.34,0.00",
            TripMath.csv(members, expenses)
        )
    }
}
