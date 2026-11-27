package com.issaczerubbabel.ledgar.fund

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KeepEarmarkedTest {

    // HDFC (1) is the salary Account, Emergency Fund RD (2) and Ministry Fund (3) are held whole, Cash (4) is held by nobody.
    private val names = mapOf(1L to "HDFC", 2L to "Emergency Fund RD", 3L to "Ministry Fund", 4L to "Cash")
    private val emergency = Fund(id = 10, name = "Emergency", createdDate = "2026-09-01")
    private val ministry = Fund(id = 11, name = "Ministry", createdDate = "2026-09-01")
    private val old = Fund(id = 12, name = "Old RD", createdDate = "2026-09-01", archivedAt = "2026-10-01")

    private val emergencyWhole = FundHolding(1, fundId = 10, accountId = 2, kind = HoldingKind.WHOLE.name, startDate = "2026-09-01")
    private val ministryWhole = FundHolding(2, fundId = 11, accountId = 3, kind = HoldingKind.WHOLE.name, startDate = "2026-09-01")
    private val snapshot = FundsSnapshot(listOf(emergency, ministry, old), listOf(emergencyWhole, ministryWhole))

    private fun transfer(amount: Double, from: Long?, to: Long?, type: String = "Transfer") = ExpenseRecord(
        id = 5, date = "2026-10-12", type = type, category = "Transfer", description = "", amount = amount,
        accountId = null, remarks = "", fromAccountId = from, toAccountId = to
    )

    @Test
    fun aTransferOutOfAFundsWholeAccountOffersToKeepItEarmarkedWhereItLands() {
        val offer = KeepEarmarked.offerFor(transfer(15_000.0, from = 2, to = 1), snapshot, names)!!
        assertEquals(10L, offer.fundId)
        assertEquals(1L, offer.toAccountId)
        assertEquals(15_000.0, offer.amount, 0.0)
        assertEquals("2026-10-12", offer.date)
        assertEquals("₹15,000 left Emergency for HDFC", offer.message)
    }

    @Test
    fun noOfferForSetAsideOrTransfersBetweenOtherAccounts() {
        assertNull(KeepEarmarked.offerFor(transfer(15_000.0, from = 1, to = 2), snapshot, names))
        assertNull(KeepEarmarked.offerFor(transfer(500.0, from = 1, to = 4), snapshot, names))
    }

    @Test
    fun noOfferForAMoveIntoAnotherFundsWholeAccount() {
        assertNull(KeepEarmarked.offerFor(transfer(1_000.0, from = 2, to = 3), snapshot, names))
    }

    @Test
    fun noOfferForAnArchivedFundOrSomethingThatIsNotATransfer() {
        val archived = snapshot.copy(holdings = snapshot.holdings + FundHolding(3, fundId = 12, accountId = 4, kind = HoldingKind.WHOLE.name, startDate = "2026-09-01"))
        assertNull(KeepEarmarked.offerFor(transfer(1_000.0, from = 4, to = 1), archived, names))
        assertNull(KeepEarmarked.offerFor(transfer(1_000.0, from = 2, to = null, type = "Expense"), snapshot, names))
        assertNull(KeepEarmarked.offerFor(transfer(0.0, from = 2, to = 1), snapshot, names))
    }

    @Test
    fun keepingItAddsAFixedHoldingOnTheDestination() {
        val offer = KeepEarmarked.offerFor(transfer(15_000.0, from = 2, to = 1), snapshot, names)!!
        val holding = KeepEarmarked.holdingFor(offer, snapshot)
        assertEquals(0L, holding.id)
        assertEquals(10L, holding.fundId)
        assertEquals(1L, holding.accountId)
        assertEquals(HoldingKind.FIXED, holding.holdingKind)
        assertEquals(15_000.0, holding.amount, 0.0)
        assertEquals(15_000.0, holding.openingAmount, 0.0)
        assertEquals("2026-10-12", holding.startDate)
    }

    @Test
    fun keepingItRaisesAFixedHoldingTheFundAlreadyHasThere() {
        val fixed = FundHolding(7, fundId = 10, accountId = 1, kind = HoldingKind.FIXED.name, amount = 70_000.0, openingAmount = 70_000.0, startDate = "2026-09-01")
        val withFixed = snapshot.copy(holdings = snapshot.holdings + fixed)
        val offer = KeepEarmarked.offerFor(transfer(15_000.0, from = 2, to = 1), withFixed, names)!!
        val holding = KeepEarmarked.holdingFor(offer, withFixed)
        assertEquals(7L, holding.id)
        assertEquals(85_000.0, holding.amount, 0.0)
        assertEquals("2026-09-01", holding.startDate)
    }
}
