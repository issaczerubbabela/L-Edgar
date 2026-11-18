package com.issaczerubbabel.ledgar.sync

import androidx.room.withTransaction
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.remote.AccountImportDto
import com.issaczerubbabel.ledgar.data.remote.DropdownImportDto
import javax.inject.Inject

/**
 * The list half of Import: makes the phone's Dropdown options and Accounts match the Sheet's
 * Backup tabs. Transactions come in afterwards through the usual Pull.
 */
class SheetListsRestorer @Inject constructor(private val database: SheetSyncDatabase) {

    /** Replaces the phone's Dropdown options with the Sheet's, roles included (ADR-0004). Returns how many. */
    suspend fun restoreDropdowns(sheet: List<DropdownImportDto>): Int {
        val options = sheet
            .filter { it.optionType != PAYMENT_MODE && it.name.isNotBlank() }
            .map { dto ->
                DropdownOption(
                    optionType = dto.optionType,
                    name = dto.name,
                    displayOrder = dto.displayOrder,
                    role = dto.role.orEmpty().trim()
                )
            }
        database.withTransaction { database.dropdownOptionDao().overwriteAllOptions(options) }
        return options.size
    }

    /**
     * Makes the phone's Accounts match the Sheet's and returns how many it has. An Account the Sheet
     * also has (matched by name, ignoring case) is updated in place, so its id and every Transaction
     * linked to it stay as they are. Accounts the Sheet lacks are deleted, but their Transactions keep
     * the Account's name first, and any Transaction whose name matches an Account afterwards is linked.
     * A Sheet with no Accounts at all (a missing tab) changes nothing.
     */
    suspend fun restoreAccounts(sheet: List<AccountImportDto>): Int = database.withTransaction {
        if (sheet.none { it.accountName.isNotBlank() }) return@withTransaction 0
        val accountDao = database.accountDao()
        val expenseDao = database.expenseDao()
        val unmatched = accountDao.getAllAccountsSnapshot().toMutableList()

        val restored = sheet.filter { it.accountName.isNotBlank() }.mapIndexed { index, dto ->
            val match = unmatched.firstOrNull { it.accountName.key() == dto.accountName.key() }
            if (match != null) unmatched.remove(match)
            val record = AccountRecord(
                id = match?.id ?: 0,
                groupName = dto.groupName,
                accountName = dto.accountName,
                initialBalance = dto.initialBalance,
                initialBalanceDate = dto.initialBalanceDate.ifBlank { "1970-01-01" },
                isHidden = dto.isHidden,
                displayOrder = dto.displayOrder ?: index,
                description = dto.description,
                includeInTotals = dto.includeInTotals,
                reconciledAt = dto.reconciledAt?.takeIf { it.isNotBlank() }
            )
            if (match != null) {
                accountDao.update(record)
                record
            } else {
                record.copy(id = accountDao.insert(record))
            }
        }

        unmatched.forEach { gone ->
            expenseDao.rememberAccountName(gone.id, gone.accountName)
            accountDao.delete(gone) // the foreign keys set the Transactions' ids to null
        }
        restored.forEach { expenseDao.linkAccountByName(it.id, it.accountName) }
        restored.size
    }

    private fun String.key() = trim().lowercase()

    private companion object {
        /** Legacy options, replaced by Accounts and never backed up. */
        const val PAYMENT_MODE = "PAYMENT_MODE"
    }
}
