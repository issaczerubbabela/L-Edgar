package com.issaczerubbabel.ledgar.trip

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        locked: Map<Long, Long> = emptyMap(),
        date: String = "2026-10-10",
        purpose: String = "Item $id",
        category: String = "Food & Snacks"
    ) = TripExpenseInput(id, date, purpose, amount, payer, mode, ids, locked, category)

    @Test
    fun equalSplitThatDividesEvenly() {
        assertEquals(mapOf(1L to 120000L, 2L to 120000L, 3L to 120000L, 4L to 120000L), TripMath.shares(expense(1, 480000, 1)))
    }

    @Test
    fun leftoverPaiseRotateByExpenseId() {
        assertEquals(mapOf(1L to 3334L, 2L to 3333L, 3L to 3333L), TripMath.shares(expense(3, 10000, 1, ids = listOf(1, 2, 3))))
        assertEquals(mapOf(1L to 3333L, 2L to 3334L, 3L to 3333L), TripMath.shares(expense(4, 10000, 1, ids = listOf(1, 2, 3))))
        assertEquals(mapOf(1L to 3333L, 2L to 3334L, 3L to 3334L), TripMath.shares(expense(1, 10001, 1, ids = listOf(1, 2, 3))))
    }

    @Test
    fun lockingOnePersonSplitsTheRestEquallyBetweenTheOthers() {
        // ₹3,460 with Arjun locked at ₹1,090: the other three share ₹2,370, which is ₹790 each.
        val e = expense(7, 346000, 1, SplitMode.CUSTOM, locked = mapOf(4L to 109000L))
        assertEquals(mapOf(1L to 79000L, 2L to 79000L, 3L to 79000L, 4L to 109000L), TripMath.shares(e))
        assertNull(TripMath.splitProblem(e))
    }

    @Test
    fun lockedAmountsStayPutWhenTheTotalChanges() {
        val e = expense(7, 400000, 1, SplitMode.CUSTOM, locked = mapOf(4L to 109000L))
        assertEquals(mapOf(1L to 97000L, 2L to 97000L, 3L to 97000L, 4L to 109000L), TripMath.shares(e))
    }

    @Test
    fun lockingSeveralPeopleSplitsTheRestBetweenWhoIsLeft() {
        val e = expense(9, 100000, 1, SplitMode.CUSTOM, locked = mapOf(1L to 40000L, 2L to 10000L))
        assertEquals(mapOf(1L to 40000L, 2L to 10000L, 3L to 25000L, 4L to 25000L), TripMath.shares(e))
    }

    @Test
    fun customWithNothingLockedIsTheSameAsEqual() {
        val equal = TripMath.shares(expense(5, 100003, 1))
        val custom = TripMath.shares(expense(5, 100003, 1, SplitMode.CUSTOM))
        assertEquals(equal, custom)
    }

    @Test
    fun leftoverPaiseRotateBetweenThePeopleWhoAreNotLocked() {
        // ₹10.02 with Rahul locked at ₹3.25 leaves 677 paise for three people: 225 each and two left over.
        val e = expense(2, 1002, 1, SplitMode.CUSTOM, locked = mapOf(2L to 325L))
        val s = TripMath.shares(e)
        assertEquals(mapOf(1L to 226L, 2L to 325L, 3L to 225L, 4L to 226L), s)
        assertEquals(1002L, s.values.sum())
    }

    @Test
    fun sharesAlwaysAddUpToTheAmountWhenTheSplitIsValid() {
        for (amount in listOf(1L, 7L, 999L, 10001L, 346001L)) {
            for (locked in listOf(emptyMap(), mapOf(4L to amount / 3), mapOf(1L to amount / 5, 3L to amount / 7))) {
                val e = expense(amount, amount, 1, SplitMode.CUSTOM, locked = locked)
                assertNull(TripMath.splitProblem(e))
                assertEquals("$amount $locked", amount, TripMath.shares(e).values.sum())
            }
        }
    }

    @Test
    fun lockedAmountsAboveTheTotalAreOverAssigned() {
        val e = expense(7, 10000, 1, SplitMode.CUSTOM, locked = mapOf(4L to 15000L))
        assertEquals(SplitProblem.OverAssigned(5000), TripMath.splitProblem(e))
    }

    @Test
    fun whenEveryoneIsLockedTheAmountsMustAddUp() {
        val ids = listOf(1L, 3L)
        val ok = expense(8, 120000, 3, SplitMode.CUSTOM, ids = ids, locked = mapOf(1L to 40000L, 3L to 80000L))
        assertNull(TripMath.splitProblem(ok))
        assertEquals(SplitProblem.UnderAssigned(20000), TripMath.splitProblem(ok.copy(locked = mapOf(1L to 40000L, 3L to 60000L))))
        assertEquals(SplitProblem.OverAssigned(10000), TripMath.splitProblem(ok.copy(locked = mapOf(1L to 50000L, 3L to 80000L))))
    }

    @Test
    fun anExpenseNeedsAtLeastOnePerson() {
        assertEquals(SplitProblem.NoMembers, TripMath.splitProblem(expense(8, 100, 1, ids = emptyList())))
        assertTrue(TripMath.shares(expense(8, 100, 1, ids = emptyList())).isEmpty())
    }

    @Test
    fun lockedAmountsForPeopleNotInTheExpenseAreIgnored() {
        val e = expense(8, 100000, 1, SplitMode.CUSTOM, ids = listOf(1, 2), locked = mapOf(4L to 90000L))
        assertEquals(mapOf(1L to 50000L, 2L to 50000L), TripMath.shares(e))
        assertNull(TripMath.splitProblem(e))
    }

    @Test
    fun balancesAreWhatYouPaidMinusYourShares() {
        val expenses = listOf(expense(1, 480000, 1), expense(2, 264000, 2))
        val b = TripMath.balances(members, expenses, emptyList())
        assertEquals(listOf(294000L, 78000L, -186000L, -186000L), b.map { it.netPaise })
        assertEquals(listOf(0L, 0L, 0L, 0L), b.map { it.settledPaise })
    }

    @Test
    fun settlementsMoveBalancesTowardsZeroWithoutChangingShares() {
        val expenses = listOf(expense(1, 480000, 1))
        val b = TripMath.balances(members, expenses, listOf(TripSettlementInput(fromId = 3, toId = 1, amountPaise = 120000)))
        assertEquals(listOf(240000L, -120000L, 0L, -120000L), b.map { it.netPaise })
        assertEquals(listOf(120000L, 120000L, 120000L, 120000L), b.map { it.sharePaise })
    }

    private val sampleExpenses = listOf(expense(1, 480000, 1), expense(2, 264000, 2))

    @Test
    fun settleUpPlanUsesTheFewestPayments() {
        val plan = TripMath.settleUpPlan(TripMath.balances(members, sampleExpenses, emptyList()))
        assertEquals(listOf(PlannedPayment(3, 1, 186000), PlannedPayment(4, 1, 108000), PlannedPayment(4, 2, 78000)), plan)
    }

    @Test
    fun recordingOnePaymentKeepsTheRestOfThePlan() {
        val after = TripMath.settleUpPlan(TripMath.balances(members, sampleExpenses, listOf(TripSettlementInput(3, 1, 186000))))
        assertEquals(listOf(PlannedPayment(4, 1, 108000), PlannedPayment(4, 2, 78000)), after)
    }

    @Test
    fun everyoneSettledMeansAnEmptyPlan() {
        val expenses = listOf(expense(1, 40000, 1, ids = listOf(1, 2)))
        assertEquals(emptyList<PlannedPayment>(), TripMath.settleUpPlan(TripMath.balances(members, expenses, listOf(TripSettlementInput(2, 1, 20000)))))
    }

    @Test
    fun theSuggestedPaymentsSettleEveryoneToZero() {
        val balances = TripMath.balances(members, sampleExpenses, emptyList())
        val check = TripMath.settlementCheck(balances, TripMath.settleUpPlan(balances))
        assertEquals(listOf(294000L, 78000L, -186000L, -186000L), check.map { it.balancePaise })
        assertEquals(listOf(0L, 0L, 186000L, 186000L), check.map { it.paysPaise })
        assertEquals(listOf(294000L, 78000L, 0L, 0L), check.map { it.receivesPaise })
        assertEquals(listOf(0L, 0L, 0L, 0L), check.map { it.afterPaise })
    }

    @Test
    fun theCheckStillSettlesEveryoneAfterSomePaymentsAreRecorded() {
        val settlements = listOf(TripSettlementInput(3, 1, 100000))
        val balances = TripMath.balances(members, sampleExpenses, settlements)
        val check = TripMath.settlementCheck(balances, TripMath.settleUpPlan(balances))
        assertEquals(listOf(0L, 0L, 0L, 0L), check.map { it.afterPaise })
    }

    @Test
    fun aMissingPaymentLeavesSomeoneUnsettled() {
        val balances = TripMath.balances(members, sampleExpenses, emptyList())
        val partial = TripMath.settleUpPlan(balances).drop(1)
        val check = TripMath.settlementCheck(balances, partial)
        assertTrue(check.any { it.afterPaise != 0L })
    }

    @Test
    fun aMembersLedgerAddsUpToTheirBalance() {
        val settlements = listOf(TripSettlementInput(3, 1, 100000))
        val balances = TripMath.balances(members, sampleExpenses, settlements)
        for (m in members) {
            val ledger = TripMath.memberLedger(m.id, members, sampleExpenses, settlements)
            assertEquals(m.name, balances.first { it.memberId == m.id }.netPaise, ledger.netPaise)
        }
    }

    @Test
    fun aLedgerNamesEachExpenseAndSettlement() {
        val settlements = listOf(TripSettlementInput(3, 1, 100000))
        val ledger = TripMath.memberLedger(1, members, sampleExpenses, settlements)
        assertEquals(
            listOf(
                LedgerLine("Item 1", "paid ₹4,800 · Share ₹1,200", 360000, LedgerKind.EXPENSE),
                LedgerLine("Item 2", "Share ₹660", -66000, LedgerKind.EXPENSE),
                LedgerLine("Got paid by Priya", "Settlement", -100000, LedgerKind.SETTLEMENT)
            ),
            ledger.lines
        )
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
    fun theMemberPaletteHasEightColoursAndHandsOutTheFirstFreeOne() {
        assertEquals(8, MemberPalette.colors.size)
        assertEquals(0, MemberPalette.nextFree(emptyList()))
        assertEquals(2, MemberPalette.nextFree(listOf(0, 1, 3)))
        assertEquals(3, MemberPalette.nextFree((0..7).toList() + listOf(0, 1, 2)))
        assertEquals(MemberPalette.colors[1], MemberPalette.colorFor(9))
    }
}
