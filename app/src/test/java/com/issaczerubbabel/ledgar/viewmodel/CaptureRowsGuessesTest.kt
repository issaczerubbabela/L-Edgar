package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.capture.CaptureTrace
import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleKey
import com.issaczerubbabel.ledgar.util.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureRowsGuessesTest {

    private fun captureRow(capture: CapturedTransaction, edit: CaptureRowEdit?, rename: String?) = CaptureRows.build(
        capture, edit,
        renames = rename?.let { mapOf(MerchantRuleKey("OFFICE CANTEEN", "DEBIT") to it) }.orEmpty(),
        knownAccountIds = setOf(3L, 7L, 9L)
    )

    private fun capture(
        category: String? = "Food",
        confidence: Double = 0.8,
        trace: CaptureTrace = CaptureTrace("R2", "You picked Food for this 4 of 5 times", descriptionGuess = "Lunch", descriptionAlternatives = listOf("Tea")),
        accountId: Long? = null
    ) = CapturedTransaction(
        id = 1, sources = "NOTIF", sender = "HDFC Bank", rawText = "", rawHash = "h", capturedAt = 0, txnTime = 0,
        amount = 80.0, direction = "DEBIT", channel = "UPI", merchantRaw = "OFFICE CANTEEN", merchantNorm = "OFFICE CANTEEN",
        accountId = accountId, suggestedType = "Expense", suggestedCategory = category, confidence = confidence,
        decidedBy = trace.rule, traceJson = trace.toJson()
    )

    @Test
    fun theSuggestedDescriptionIsTheTitleWithItsAlternativesAsChips() {
        val row = captureRow(capture(), edit = null, rename = null)

        assertEquals("Lunch", row.title)
        assertTrue(row.titleIsGuess)
        assertEquals(listOf("Tea"), row.descriptionAlternatives)
        assertEquals("OFFICE CANTEEN", row.merchantTitle)
        assertEquals("R2 · You picked Food for this 4 of 5 times", row.why)
        assertEquals(ConfidenceBand.CHECK, row.band)
    }

    @Test
    fun tappingAChipReplacesTheTitleAndOffersTheGuessBack() {
        val row = captureRow(capture(), CaptureRowEdit(description = "Tea"), rename = null)

        assertEquals("Tea", row.title)
        assertFalse(row.titleIsGuess)
        assertEquals(listOf("Lunch"), row.descriptionAlternatives)
    }

    @Test
    fun aSavedRenameWinsOverTheGuess() {
        val row = captureRow(capture(), edit = null, rename = "Canteen")

        assertEquals("Canteen", row.title)
        assertFalse(row.titleIsGuess)
        assertEquals(listOf("Lunch", "Tea"), row.descriptionAlternatives)
    }

    @Test
    fun aPerPaymentGuessWinsOverARenameWhichBecomesAChip() {
        val trace = CaptureTrace("R2", "x", descriptionGuess = "Lunch", descriptionAlternatives = listOf("Tea"), descriptionPerPayment = true)

        val row = captureRow(capture(trace = trace), edit = null, rename = "Tea")

        assertEquals("Lunch", row.title)
        assertTrue(row.titleIsGuess)
        assertEquals(listOf("Tea"), row.descriptionAlternatives)
    }

    @Test
    fun reguessingChangesOnlyTheFieldsTheUserHasNotTouched() {
        val edit = CaptureRowEdit(category = "Snacks")
        val before = captureRow(capture(), edit, rename = null)
        // A Confirm elsewhere re-guessed this capture: new Category and Description guesses.
        val reguessed = capture(category = "Groceries", trace = CaptureTrace("R2", "x", descriptionGuess = "Tea"))

        val after = captureRow(reguessed, edit, rename = null)

        assertEquals("Snacks", before.category)
        assertEquals("Snacks", after.category)
        assertTrue(after.categoryPickedByUser)
        assertEquals("Tea", after.title)
        assertTrue(after.titleIsGuess)

        val typed = captureRow(reguessed, CaptureRowEdit(description = "Biscuits"), rename = null)
        assertEquals("Groceries", typed.category)
        assertEquals("Biscuits", typed.title)
    }

    @Test
    fun aTransferTheUserPickedSurvivesAReguess() {
        val edit = CaptureRows.switchType(CaptureRowEdit(), TransactionType.TRANSFER).copy(otherAccountId = 9L)
        val reguessed = capture(category = "Snacks", trace = CaptureTrace("R2", "x", descriptionGuess = "Tea"))

        val row = captureRow(reguessed, edit, rename = null)

        assertEquals(TransactionType.TRANSFER, row.type)
        assertNull(row.category)
        assertEquals(9L, row.otherAccountId)
        assertTrue(row.categoryPickedByUser)
    }

    @Test
    fun aGuessedAccountFillsTheCardButIsNeverEnoughForBulk() {
        val rule = CaptureTrace("R1", "Saved rule for this merchant", guessedAccountId = 7L)
        val row = captureRow(capture(confidence = 0.98, trace = rule), edit = null, rename = null)

        assertEquals(7L, row.accountId)
        assertTrue(row.accountIsGuess)
        assertTrue(row.canConfirm)
        assertEquals(ConfidenceBand.HIGH, row.band)
        assertFalse(row.isBulkCandidate)

        val picked = captureRow(capture(confidence = 0.98, trace = rule), CaptureRowEdit(accountId = 9L), rename = null)
        assertEquals(9L, picked.accountId)
        assertFalse(picked.accountIsGuess)
        assertTrue(picked.isBulkCandidate)
    }

    @Test
    fun aKnownAccountIsNeverReplacedByAGuess() {
        val row = captureRow(capture(trace = CaptureTrace(guessedAccountId = 7L), accountId = 3L), edit = null, rename = null)

        assertEquals(3L, row.accountId)
        assertFalse(row.accountIsGuess)
    }

    @Test
    fun withNoGuessTheBanksTextIsTheTitle() {
        val row = captureRow(capture(trace = CaptureTrace("R11", "Nothing recognised")), edit = null, rename = null)

        assertEquals("OFFICE CANTEEN", row.title)
        assertFalse(row.titleIsGuess)
        assertEquals(emptyList<String>(), row.descriptionAlternatives)
        assertNull(row.capture.accountId)
    }
}
