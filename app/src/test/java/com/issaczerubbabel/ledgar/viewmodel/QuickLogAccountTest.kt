package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickLogAccountTest {

    private fun account(id: Long, name: String) =
        AccountRecord(id = id, groupName = "Accounts", accountName = name, initialBalance = 0.0)

    @Test
    fun theLastExpensesAccountIsPreselectedWhenItsStillVisible() {
        val visible = listOf(account(1, "Cash"), account(2, "HDFC"))

        assertEquals(2L, QuickLogAccount.seed(lastExpenseAccountId = 2L, visibleAccounts = visible))
    }

    @Test
    fun aRememberedAccountThatsNoLongerVisibleIsDropped() {
        val visible = listOf(account(1, "Cash"))

        assertNull(QuickLogAccount.seed(lastExpenseAccountId = 99L, visibleAccounts = visible))
    }

    @Test
    fun noRememberedAccountLeavesNothingSelected() {
        val visible = listOf(account(1, "Cash"))

        assertNull(QuickLogAccount.seed(lastExpenseAccountId = null, visibleAccounts = visible))
    }
}
