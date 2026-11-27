package com.issaczerubbabel.ledgar.capture.pair

import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LedgerMatcherTest {

    private val ist = ZoneId.of("Asia/Kolkata")

    private fun capture(
        amount: Double = 450.0,
        direction: String = "DEBIT",
        accountId: Long? = HDFC,
        at: LocalDateTime = LocalDateTime.of(2026, 11, 14, 13, 5),
        zone: ZoneId = ist
    ) = CapturedTransaction(
        id = 1, sources = "NOTIF", sender = "HDFC Bank", rawText = "", rawHash = "h", capturedAt = 0,
        txnTime = at.atZone(zone).toInstant().toEpochMilli(), amount = amount, direction = direction, channel = "UPI",
        accountId = accountId
    )

    private fun txn(
        id: Long,
        amount: Double = 450.0,
        type: String = TransactionType.EXPENSE,
        date: String = "2026-11-14",
        accountId: Long? = HDFC,
        syncAction: String = "INSERT"
    ) = ExpenseRecord(
        id = id, date = date, type = type, category = "Food", description = "Swiggy", amount = amount,
        accountId = accountId, remarks = "", syncAction = syncAction
    )

    private fun best(capture: CapturedTransaction, vararg txns: ExpenseRecord, linked: Set<Long> = emptySet(), zone: ZoneId = ist) =
        LedgerMatcher.bestMatch(capture, txns.toList(), linked, zone)

    @Test
    fun anUnlinkedExpenseForTheSameAmountOnTheSameAccountWithinThreeDaysMatches() {
        val swiggy = txn(10, date = "2026-11-11")

        assertEquals(swiggy, best(capture(), swiggy))
    }

    @Test
    fun theAmountMustMatchToThePaisa() {
        assertNull(best(capture(), txn(10, amount = 450.50)))
        // Doubles that only differ by floating-point noise are the same paise.
        assertEquals(10L, best(capture(amount = 0.1 + 0.2), txn(10, amount = 0.3))?.id)
    }

    @Test
    fun aFourDayGapIsTooFar() {
        assertNull(best(capture(), txn(10, date = "2026-11-10")))
        assertNull(best(capture(), txn(11, date = "2026-11-18")))
        assertEquals(12L, best(capture(), txn(12, date = "2026-11-17"))?.id)
    }

    @Test
    fun aSpendLoggedBeforeMidnightMatchesAnAlertDatedAfterIt() {
        val justAfterMidnight = capture(at = LocalDateTime.of(2026, 11, 15, 0, 10))

        assertEquals(10L, best(justAfterMidnight, txn(10, date = "2026-11-14"))?.id)
    }

    @Test
    fun theCapturesDateIsTakenInThePhonesTimeZone() {
        // 01:00 on the 18th in Kolkata is 19:30 on the 17th in UTC.
        val lateNight = capture(at = LocalDateTime.of(2026, 11, 18, 1, 0))
        val tooFar = txn(10, date = "2026-11-14")

        assertNull("the 18th in India is 4 days after the 14th", best(lateNight, tooFar, zone = ist))
        assertEquals("but the 17th in UTC is 3 days after", 10L, best(lateNight, tooFar, zone = ZoneId.of("UTC"))?.id)
    }

    @Test
    fun aDebitOnlyMatchesAnExpenseAndACreditOnlyAnIncome() {
        assertNull(best(capture(), txn(10, type = TransactionType.INCOME)))
        assertNull(best(capture(direction = "CREDIT"), txn(10, type = TransactionType.EXPENSE)))
        assertEquals(11L, best(capture(direction = "CREDIT"), txn(11, type = TransactionType.INCOME))?.id)
    }

    @Test
    fun transfersAndBalanceAdjustmentsNeverMatch() {
        assertNull(best(capture(), txn(10, type = TransactionType.TRANSFER, accountId = null)))
        assertNull(best(capture(), txn(11, type = TransactionType.ADJUSTMENT)))
    }

    @Test
    fun aDifferentKnownAccountDoesNotMatch() {
        assertNull(best(capture(), txn(10, accountId = CUB)))
    }

    @Test
    fun anUnknownAccountOnEitherSideStillMatches() {
        assertEquals(10L, best(capture(accountId = null), txn(10, accountId = CUB))?.id)
        assertEquals(11L, best(capture(), txn(11, accountId = null))?.id)
    }

    @Test
    fun aTransactionWaitingToBeDeletedDoesNotMatch() {
        assertNull(best(capture(), txn(10, syncAction = "DELETE")))
    }

    @Test
    fun aTransactionAlreadyLinkedOrConfirmedFromACaptureIsNeverOfferedAgain() {
        assertNull(best(capture(), txn(10), linked = setOf(10)))
        assertEquals(11L, best(capture(), txn(10), txn(11), linked = setOf(10))?.id)
    }

    @Test
    fun theSameAccountComesFirstThenTheNearestDateThenTheNewest() {
        val noAccount = txn(1, accountId = null)
        val sameAccountFar = txn(2, date = "2026-11-12")
        val sameAccountNear = txn(3, date = "2026-11-13")
        val sameAccountNearNewer = txn(4, date = "2026-11-15")

        assertEquals(
            listOf(sameAccountNearNewer, sameAccountNear, sameAccountFar, noAccount),
            LedgerMatcher.matches(capture(), listOf(noAccount, sameAccountFar, sameAccountNear, sameAccountNearNewer), emptySet(), ist)
        )
    }

    @Test
    fun aMalformedDateIsSkipped() {
        assertNull(best(capture(), txn(10, date = "14/11/2026")))
    }

    @Test
    fun isMatchIsTheSameRuleForOneTransaction() {
        assertTrue(LedgerMatcher.isMatch(capture(), txn(10), emptySet(), ist))
        assertFalse(LedgerMatcher.isMatch(capture(), txn(10), setOf(10L), ist))
    }

    private companion object {
        const val HDFC = 1L
        const val CUB = 2L
    }
}
