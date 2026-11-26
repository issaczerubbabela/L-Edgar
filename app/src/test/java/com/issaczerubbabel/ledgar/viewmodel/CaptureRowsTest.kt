package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleKey
import com.issaczerubbabel.ledgar.data.repository.CaptureEdits
import com.issaczerubbabel.ledgar.util.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** The To review card's switch and the Add Transaction form opened with Edit. */
class CaptureRowsTest {

    private val accounts = setOf(HDFC, CUB)

    private fun capture(
        direction: String = "DEBIT",
        accountId: Long? = HDFC,
        suggestedType: String? = if (direction == "CREDIT") TransactionType.INCOME else TransactionType.EXPENSE,
        suggestedCategory: String? = "Bills",
        suggestedToAccountId: Long? = null,
        confidence: Double = 0.75
    ) = CapturedTransaction(
        id = 1, sources = "NOTIF", sender = "HDFC Bank", rawText = "", rawHash = "h", capturedAt = T0, txnTime = T0,
        amount = 5_000.0, direction = direction, channel = "UPI", merchantRaw = "CRED CLUB", merchantNorm = "CRED CLUB",
        accountHint = "1111", accountId = accountId, suggestedType = suggestedType, suggestedCategory = suggestedCategory,
        suggestedToAccountId = suggestedToAccountId, confidence = confidence
    )

    private fun row(capture: CapturedTransaction, edit: CaptureRowEdit? = null, renames: Map<MerchantRuleKey, String> = emptyMap()) =
        CaptureRows.build(capture, edit, renames, accounts, why = "R7 · Keyword")

    @Test
    fun switchingToTransferAsksForTheOtherAccountInsteadOfACategory() {
        val switched = CaptureRows.switchType(CaptureRowEdit(category = "Food"), TransactionType.TRANSFER)
        val card = row(capture(), switched)

        assertTrue(card.isTransfer)
        assertNull(card.category)
        assertNull(card.otherAccountId)
        assertFalse(card.canConfirm)
        assertEquals(ConfidenceBand.LOW, card.band)

        val picked = row(capture(), switched.copy(otherAccountId = CUB))
        assertTrue(picked.canConfirm)
        assertEquals(
            CaptureEdits(TransactionType.TRANSFER, TransactionType.TRANSFER, HDFC, "CRED CLUB", otherAccountId = CUB),
            picked.toEdits()
        )
    }

    @Test
    fun aTransferToTheCapturesOwnAccountCantBeConfirmed() {
        val card = row(capture(), CaptureRowEdit(type = TransactionType.TRANSFER, otherAccountId = HDFC))

        assertFalse(card.canConfirm)
        assertNull(card.toEdits())
    }

    @Test
    fun switchingBackToTheSuggestedTypeBringsTheSuggestionBack() {
        val toIncome = CaptureRows.switchType(CaptureRowEdit(), TransactionType.INCOME)
        assertNull(row(capture(), toIncome).category)

        val back = CaptureRows.switchType(toIncome, TransactionType.EXPENSE)
        assertEquals("Bills", row(capture(), back).category)
    }

    @Test
    fun aSuggestedTransferStartsOnTransferWithItsAccount() {
        val card = row(capture(suggestedType = TransactionType.TRANSFER, suggestedCategory = null, suggestedToAccountId = CUB, confidence = 0.98))

        assertEquals(TransactionType.TRANSFER, card.type)
        assertEquals(CUB, card.otherAccountId)
        assertEquals(ConfidenceBand.HIGH, card.band)
        assertTrue(card.isBulkCandidate)
    }

    @Test
    fun aSuggestedAccountThatWasDeletedIsDropped() {
        val card = row(capture(suggestedType = TransactionType.TRANSFER, suggestedCategory = null, suggestedToAccountId = 99, confidence = 0.98))

        assertNull(card.otherAccountId)
        assertFalse(card.canConfirm)
    }

    @Test
    fun aRenameBelongsToTheMerchantsDirection() {
        val renames = mapOf(MerchantRuleKey("CRED CLUB", "CREDIT") to "CRED cashback")

        assertEquals("CRED CLUB", row(capture(), renames = renames).title)
        assertEquals("CRED cashback", row(capture(direction = "CREDIT"), renames = renames).title)
    }

    @Test
    fun editPrefillsTheFormFromTheCardWithTheCapturesAccountOnTheBanksSide() {
        val draft = CaptureEdits(TransactionType.TRANSFER, TransactionType.TRANSFER, HDFC, "Card bill", otherAccountId = CUB)

        val debit = CaptureForm.prefill(capture(), draft, "CRED CLUB", ZoneOffset.UTC)
        assertEquals(TransactionType.TRANSFER, debit.type)
        assertEquals(HDFC, debit.fromAccountId)
        assertEquals(CUB, debit.toAccountId)
        assertEquals("Card bill", debit.description)

        val credit = CaptureForm.prefill(capture(direction = "CREDIT"), draft, "CRED CLUB", ZoneOffset.UTC)
        assertEquals(CUB, credit.fromAccountId)
        assertEquals(HDFC, credit.toAccountId)
    }

    @Test
    fun withoutADraftTheFormStartsFromTheCapture() {
        val form = CaptureForm.prefill(capture(), draft = null, title = "CRED CLUB", zone = ZoneOffset.UTC)

        assertEquals(TransactionType.EXPENSE, form.type)
        assertEquals("Bills", form.category)
        assertEquals(HDFC, form.accountId)
        assertEquals(5_000.0, form.amount, 0.0)
        assertEquals(LocalDate.of(2025, 10, 9), form.date)
        assertEquals("CRED CLUB", form.description)
        assertFalse(form.alwaysUse)
    }

    @Test
    fun savingTheFormConfirmsWithEverythingTyped() {
        val edits = CaptureForm.toEdits(
            capture = capture(), type = TransactionType.EXPENSE, category = "Food", accountId = HDFC,
            fromAccountId = null, toAccountId = null, description = "Dinner", remarks = "with tip",
            amount = 549.5, date = LocalDate.of(2026, 11, 20), alwaysUse = true
        )!!

        assertEquals(549.5, edits.amount!!, 0.0)
        assertEquals(LocalDate.of(2026, 11, 20), edits.date)
        assertEquals("with tip", edits.remarks)
        assertTrue(edits.alwaysUse)
        assertNull(edits.otherAccountId)
    }

    @Test
    fun aTransferFromTheFormReadsItsSidesByTheBanksDirection() {
        fun transfer(direction: String) = CaptureForm.toEdits(
            capture = capture(direction = direction), type = TransactionType.TRANSFER, category = TransactionType.TRANSFER,
            accountId = null, fromAccountId = HDFC, toAccountId = CUB, description = "Self", remarks = "",
            amount = 5_000.0, date = LocalDate.of(2026, 11, 20), alwaysUse = false
        )!!

        assertEquals(HDFC, transfer("DEBIT").accountId)
        assertEquals(CUB, transfer("DEBIT").otherAccountId)
        assertEquals(CUB, transfer("CREDIT").accountId)
        assertEquals(HDFC, transfer("CREDIT").otherAccountId)
    }

    @Test
    fun switchingTheFormsTypeKeepsTheCapturesAccountOnItsSide() {
        assertEquals(FormAccounts(null, HDFC, null), CaptureForm.place(capture(), TransactionType.TRANSFER, HDFC, null))
        assertEquals(FormAccounts(null, null, HDFC), CaptureForm.place(capture(direction = "CREDIT"), TransactionType.TRANSFER, HDFC, null))
        assertEquals(FormAccounts(HDFC, null, null), CaptureForm.place(capture(), TransactionType.INCOME, HDFC, null))
    }

    private companion object {
        const val HDFC = 1L
        const val CUB = 2L
        const val T0 = 1_760_000_000_000L // 2025-10-09 UTC
    }
}
