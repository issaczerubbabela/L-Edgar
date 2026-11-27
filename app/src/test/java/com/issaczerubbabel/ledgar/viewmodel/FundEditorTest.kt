package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.fund.FundsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FundEditorTest {

    private val today = LocalDate.of(2026, 10, 10)

    private val hdfc = AccountRecord(id = 1, groupName = "Bank", accountName = "HDFC", initialBalance = 0.0)
    private val rd = AccountRecord(id = 2, groupName = "Deposits", accountName = "Emergency Fund RD", initialBalance = 0.0)
    private val ministry = AccountRecord(id = 3, groupName = "Bank", accountName = "Ministry Fund", initialBalance = 0.0)
    private val sbi = AccountRecord(id = 4, groupName = "Bank", accountName = "SBI", initialBalance = 0.0)
    private val hidden = AccountRecord(id = 5, groupName = "Bank", accountName = "Old", initialBalance = 0.0, isHidden = true)
    private val accounts = listOf(hdfc, rd, ministry, sbi, hidden)

    private val emergency = Fund(id = 10, name = "Emergency", createdDate = "2026-09-01", sortOrder = 0)
    private val giving = Fund(id = 11, name = "Ministry", createdDate = "2026-09-01", sortOrder = 1)
    // Ministry holds Ministry Fund whole and ₹2,000 of SBI.
    private val snapshot = FundsSnapshot(
        funds = listOf(emergency, giving),
        holdings = listOf(
            FundHolding(1, 11, 3, HoldingKind.WHOLE.name, startDate = "2026-09-01"),
            FundHolding(2, 11, 4, HoldingKind.FIXED.name, amount = 2_000.0, startDate = "2026-09-01"),
            FundHolding(3, 10, 2, HoldingKind.WHOLE.name, openingAmount = 15_000.0, startDate = "2026-09-01")
        )
    )

    @Test
    fun aNewDraftIsEmptyWithTheNextColour() {
        val draft = FundEditor.newDraft(snapshot)
        assertEquals(0L, draft.fundId)
        assertEquals("", draft.name)
        assertEquals(2, draft.colorIndex)
        assertTrue(draft.holdings.isEmpty())
        assertFalse(draft.canSave)
    }

    @Test
    fun anExistingFundLoadsItsHoldings() {
        val draft = FundEditor.draftOf(emergency, snapshot)
        assertEquals("Emergency", draft.name)
        val holding = draft.holdings.single()
        assertEquals(3L, holding.holdingId)
        assertEquals(HoldingKind.WHOLE, holding.kind)
        assertEquals("15000", holding.openingInput)
        assertEquals(LocalDate.of(2026, 9, 1), holding.startDate)
    }

    @Test
    fun wholeAccountChoicesLeaveOutAccountsOtherFundsHoldOrThisDraftUses() {
        val draft = FundEditor.draftOf(emergency, snapshot)
        // RD is already in the draft; Ministry Fund is held whole and SBI in part by Ministry; Old is hidden.
        assertEquals(listOf("HDFC"), FundEditor.accountChoices(HoldingKind.WHOLE, draft, snapshot, accounts).map { it.accountName })
    }

    @Test
    fun fixedAmountChoicesOnlyLeaveOutAccountsAnotherFundHoldsWhole() {
        val draft = FundEditor.draftOf(emergency, snapshot)
        assertEquals(listOf("HDFC", "SBI"), FundEditor.accountChoices(HoldingKind.FIXED, draft, snapshot, accounts).map { it.accountName })
    }

    @Test
    fun aFixedHoldingNeedsAnAmountAndTheTargetMustBeANumber() {
        var draft = FundEditor.newDraft(snapshot).copy(name = "Car")
        draft = FundEditor.addHolding(draft, HoldingKind.FIXED, accountId = 1, balance = 0.0, today = today)
        assertFalse(draft.canSave)
        assertTrue(draft.holdings.single().amountError)
        draft = draft.copy(holdings = draft.holdings.map { it.copy(amountInput = "70,000") })
        assertTrue(draft.canSave)
        draft = draft.copy(targetInput = "lots")
        assertTrue(draft.targetError)
        assertFalse(draft.canSave)
    }

    @Test
    fun aWholeHoldingStartsWithTheAccountsBalanceAsItsOpeningAmount() {
        val draft = FundEditor.addHolding(FundEditor.newDraft(snapshot), HoldingKind.WHOLE, accountId = 1, balance = 30_000.0, today = today)
        val holding = draft.holdings.single()
        assertEquals("30000", holding.openingInput)
        assertEquals(today, holding.startDate)
    }

    @Test
    fun savingTurnsTheDraftIntoAFundAndHoldings() {
        var draft = FundEditor.newDraft(snapshot).copy(name = "  Car ", emoji = "🚗", targetInput = "1,00,000", dueDate = LocalDate.of(2027, 3, 31))
        draft = FundEditor.addHolding(draft, HoldingKind.FIXED, accountId = 1, balance = 0.0, today = today)
        draft = draft.copy(holdings = draft.holdings.map { it.copy(amountInput = "70000") })

        val fund = FundEditor.toFund(draft, existing = null, today = today)
        assertEquals("Car", fund.name)
        assertEquals(100_000.0, fund.targetAmount!!, 0.0)
        assertEquals("2027-03-31", fund.dueDate)
        assertEquals(2, fund.sortOrder)
        assertEquals("2026-10-10", fund.createdDate)

        val holding = FundEditor.toHoldings(draft, fundId = 20).single()
        assertEquals(20L, holding.fundId)
        assertEquals(HoldingKind.FIXED.name, holding.kind)
        assertEquals(70_000.0, holding.amount, 0.0)
        // A blank opening amount on a fixed holding is the amount itself.
        assertEquals(70_000.0, holding.openingAmount, 0.0)
        assertEquals("2026-10-10", holding.startDate)
    }

    @Test
    fun aBlankTargetMeansNoTarget() {
        val draft = FundEditor.newDraft(snapshot).copy(name = "Car", targetInput = " ")
        assertNull(FundEditor.toFund(draft, existing = null, today = today).targetAmount)
    }

    @Test
    fun theHoldingsToRemoveAreTheSavedOnesNoLongerInTheDraft() {
        val draft = FundEditor.draftOf(giving, snapshot)
        val trimmed = draft.copy(holdings = draft.holdings.filter { it.accountId == 3L })
        assertEquals(listOf(2L), FundEditor.removedHoldingIds(trimmed, snapshot))
    }
}
