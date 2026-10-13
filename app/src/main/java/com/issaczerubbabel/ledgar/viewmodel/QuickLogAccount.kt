package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord

/** Which Account Quick Add should preselect. Pure, so it's unit-tested without a ViewModel. */
internal object QuickLogAccount {

    /**
     * [lastExpenseAccountId], unless it's no longer one of [visibleAccounts] (Hidden or deleted
     * since that Expense was logged), in which case Quick Add starts with no Account chosen.
     */
    fun seed(lastExpenseAccountId: Long?, visibleAccounts: List<AccountRecord>): Long? =
        lastExpenseAccountId?.takeIf { id -> visibleAccounts.any { it.id == id } }
}
