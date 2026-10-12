package com.issaczerubbabel.ledgar.account

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.DropdownRole
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountBook
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.viewmodel.reconcileSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReconcileTest {

    private val today = LocalDate.of(2026, 10, 8)
    private val savings = AccountRecord(
        id = 1, groupName = "Bank", accountName = "HDFC Savings", initialBalance = 79110.0,
        initialBalanceDate = "2026-09-30", reconciledAt = "2026-09-14"
    )

    @Test
    fun aDifferenceAddsOneAdjustmentDatedTodayAndStampsTheAccount() {
        val outcome = Reconcile.reconcile(savings, appBalance = 83910.0, bankBalance = 84250.0, today = today)
        val adjustment = outcome.adjustment!!
        assertEquals(TransactionType.ADJUSTMENT, adjustment.type)
        assertEquals(340.0, adjustment.amount, 0.0)
        assertEquals(1L, adjustment.accountId)
        assertEquals("2026-10-08", adjustment.date)
        assertFalse(adjustment.isSynced)
        assertEquals("2026-10-08", outcome.account.reconciledAt)
        assertEquals(79110.0, outcome.account.initialBalance, 0.0)
        assertEquals("2026-09-30", outcome.account.initialBalanceDate)
    }

    @Test
    fun aLowerBankFigureAddsANegativeAdjustment() {
        val outcome = Reconcile.reconcile(savings, appBalance = 84250.0, bankBalance = 84000.5, today = today)
        assertEquals(-249.5, outcome.adjustment!!.amount, 0.0)
    }

    @Test
    fun matchingFiguresOnlyStampTheAccount() {
        val outcome = Reconcile.reconcile(savings, appBalance = 0.1 + 0.2, bankBalance = 0.3, today = today)
        assertNull(outcome.adjustment)
        assertEquals("2026-10-08", outcome.account.reconciledAt)
    }

    @Test
    fun startingFreshMovesTheInitialBalanceToTodayWithoutATransaction() {
        val outcome = Reconcile.startFresh(savings, bankBalance = 50000.0, today = today)
        assertNull(outcome.adjustment)
        assertEquals(50000.0, outcome.account.initialBalance, 0.0)
        assertEquals("2026-10-08", outcome.account.initialBalanceDate)
        assertEquals("2026-10-08", outcome.account.reconciledAt)
    }

    @Test
    fun theLabelSaysHowLongAgoAndCashIsCounted() {
        assertEquals("Reconciled today", Reconcile.lastCheckedLabel(today, "Bank", today))
        assertEquals("Reconciled yesterday", Reconcile.lastCheckedLabel(today.minusDays(1), "Bank", today))
        assertEquals("Reconciled 24 days ago", Reconcile.lastCheckedLabel(LocalDate.of(2026, 9, 14), "Bank", today))
        assertEquals("Counted 2 days ago", Reconcile.lastCheckedLabel(today.minusDays(2), "Cash", today))
        assertEquals("Not reconciled yet", Reconcile.lastCheckedLabel(null, "Bank", today))
    }

    @Test
    fun onlyAnAccountCheckedMoreThanThirtyDaysAgoIsStale() {
        assertFalse(Reconcile.isStale(today.minusDays(30), today))
        assertTrue(Reconcile.isStale(today.minusDays(31), today))
        assertFalse(Reconcile.isStale(null, today))
    }

    @Test
    fun theSheetShowsTheDifferenceAsTheUserTypes() {
        val book = AccountBook.of(
            listOf(savings), emptyList(),
            listOf(ExpenseRecord(id = 1, date = "2026-10-02", type = "Income", category = "Freelance", description = "",
                amount = 4800.0, accountId = 1, remarks = ""))
        )
        val sheet = reconcileSheet(book, 1, "84,250", today)!!
        assertEquals("₹83,910.00", sheet.appFigure)
        assertEquals("+₹340.00", sheet.difference)
        assertEquals("Reconciled 24 days ago", sheet.lastChecked)
        assertTrue(sheet.note.startsWith("Adds a Balance adjustment of +₹340.00 dated 8 Oct 2026."))
        assertTrue(reconcileSheet(book, 1, "83910", today)!!.matches)
        assertNull(reconcileSheet(book, 1, "abc", today)!!.difference)
    }

    @Test
    fun aLiabilitysSheetWorksInAmountOwed() {
        val card = AccountRecord(id = 3, groupName = "Credit card", accountName = "HDFC Millennia", initialBalance = -16490.0,
            initialBalanceDate = "2026-09-30")
        val roles = listOf(DropdownOption(optionType = "ACCOUNT_GROUP", name = "Credit card", displayOrder = 0, role = DropdownRole.LIABILITY))
        val sheet = reconcileSheet(AccountBook.of(listOf(card), roles, emptyList()), 3, "16,690", today)!!
        assertEquals("Bank says you owe", sheet.bankLabel)
        assertEquals("₹16,490.00", sheet.appFigure)
        assertEquals(-16690.0, sheet.bankBalance!!, 0.0)
        assertEquals("−₹200.00", sheet.difference)
        assertTrue(sheet.isDifferenceNegative)
    }
}
