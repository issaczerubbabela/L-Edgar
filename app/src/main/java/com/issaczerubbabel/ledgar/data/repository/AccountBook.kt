package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.account.AccountSnapshot
import com.issaczerubbabel.ledgar.account.TransactionSnapshot
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.DropdownRole
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import java.time.LocalDate

/** Everything [com.issaczerubbabel.ledgar.account.AccountMath] needs, read from Room in one go. */
data class AccountBook(
    val records: List<AccountRecord>,
    val accounts: List<AccountSnapshot>,
    val transactions: List<TransactionSnapshot>,
    val transactionRecords: List<ExpenseRecord>
) {
    companion object {
        private val EARLIEST = LocalDate.of(1970, 1, 1)

        fun of(records: List<AccountRecord>, options: List<DropdownOption>, transactions: List<ExpenseRecord>): AccountBook {
            val liabilityGroups = options
                .filter { it.optionType == "ACCOUNT_GROUP" && it.role == DropdownRole.LIABILITY }
                .mapTo(mutableSetOf()) { it.name.trim().lowercase() }
            return AccountBook(
                records = records,
                accounts = records.map { record ->
                    AccountSnapshot(
                        id = record.id,
                        name = record.accountName,
                        group = record.groupName,
                        isLiability = record.groupName.trim().lowercase() in liabilityGroups,
                        initialBalance = record.initialBalance,
                        asOfDate = parseFlexibleDate(record.initialBalanceDate) ?: EARLIEST,
                        includedInTotals = record.includeInTotals
                    )
                },
                // A Transaction without a readable date can't be placed after an As-of date, so it never counts.
                transactions = transactions.mapNotNull { record ->
                    val date = parseFlexibleDate(record.date) ?: return@mapNotNull null
                    TransactionSnapshot(
                        id = record.id,
                        type = record.type,
                        date = date,
                        amount = record.amount,
                        accountId = record.accountId,
                        fromAccountId = record.fromAccountId,
                        toAccountId = record.toAccountId
                    )
                },
                transactionRecords = transactions
            )
        }
    }
}
