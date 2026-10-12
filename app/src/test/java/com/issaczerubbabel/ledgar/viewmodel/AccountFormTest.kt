package com.issaczerubbabel.ledgar.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountFormTest {

    private val others = listOf("HDFC Savings", "Wallet")

    @Test
    fun aCompleteNewAccountHasNoErrors() {
        assertTrue(AccountForm.validate("Bank", "SBI Salary", "12,430.50", others, isAdding = true).isEmpty)
    }

    @Test
    fun eachMissingFieldIsReportedNextToIt() {
        val errors = AccountForm.validate("", "  ", "abc", others, isAdding = true)
        assertEquals("Pick a group", errors.group)
        assertEquals("Enter a name", errors.name)
        assertEquals("Enter an amount, like 1250.50", errors.amount)
    }

    @Test
    fun aNameAlreadyTakenIsRefusedWhateverItsCase() {
        assertEquals(
            "You already have an account called hdfc savings",
            AccountForm.validate("Bank", " hdfc savings ", "0", others, isAdding = true).name
        )
    }

    @Test
    fun editingDoesNotCheckTheReadOnlyAmount() {
        assertNull(AccountForm.validate("Bank", "HDFC Savings", "", listOf("Wallet"), isAdding = false).amount)
    }

    @Test
    fun aLiabilitysAmountOwedIsStoredNegative() {
        assertEquals(-12000.0, AccountForm.initialBalance(12000.0, isLiability = true), 0.0)
        assertEquals(12000.0, AccountForm.initialBalance(12000.0, isLiability = false), 0.0)
        assertEquals(12000.0, AccountForm.amountShown(-12000.0, isLiability = true), 0.0)
        assertEquals("0.0", AccountForm.initialBalance(0.0, isLiability = true).toString())
    }

    @Test
    fun deletingAnAccountWithoutTransactionsNeedsNoChoice() {
        assertTrue(AccountForm.canConfirmDelete(0, deleteTransactions = false, moveToAccountId = null, accountId = 5, typedName = "", accountName = "Card"))
    }

    @Test
    fun movingTransactionsNeedsAnotherAccount() {
        assertFalse(AccountForm.canConfirmDelete(38, deleteTransactions = false, moveToAccountId = null, accountId = 5, typedName = "", accountName = "Card"))
        assertFalse(AccountForm.canConfirmDelete(38, deleteTransactions = false, moveToAccountId = 5, accountId = 5, typedName = "", accountName = "Card"))
        assertTrue(AccountForm.canConfirmDelete(38, deleteTransactions = false, moveToAccountId = 2, accountId = 5, typedName = "", accountName = "Card"))
    }

    @Test
    fun deletingTransactionsNeedsTheAccountsNameTyped() {
        assertFalse(AccountForm.canConfirmDelete(38, deleteTransactions = true, moveToAccountId = null, accountId = 5, typedName = "card", accountName = "HDFC Millennia"))
        assertTrue(AccountForm.canConfirmDelete(38, deleteTransactions = true, moveToAccountId = null, accountId = 5, typedName = "HDFC Millennia ", accountName = "HDFC Millennia"))
    }
}
