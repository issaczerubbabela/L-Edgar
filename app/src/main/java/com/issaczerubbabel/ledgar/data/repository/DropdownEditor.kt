package com.issaczerubbabel.ledgar.data.repository

import androidx.room.withTransaction
import com.issaczerubbabel.ledgar.data.bucket.CycleSummaryBuilder
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.util.TransactionType
import javax.inject.Inject

/**
 * Renaming and deleting a Dropdown option, with everything that stores it by name, in one Room
 * transaction. Transactions whose Category is renamed wait to sync, so the Sheet shows the new name.
 */
class DropdownEditor @Inject constructor(private val database: SheetSyncDatabase) {

    sealed interface RenameResult {
        /** [transactionsRenamed] counts the Transactions that now carry the new Category. */
        data class Renamed(val transactionsRenamed: Int) : RenameResult
        data object Blank : RenameResult
        data class NameTaken(val name: String) : RenameResult
        data object Gone : RenameResult
    }

    suspend fun rename(option: DropdownOption, newName: String): RenameResult {
        val name = newName.trim()
        if (name.isEmpty()) return RenameResult.Blank
        return database.withTransaction {
            val options = database.dropdownOptionDao().getAllOptionsSnapshot()
            val current = options.firstOrNull { it.id == option.id } ?: return@withTransaction RenameResult.Gone
            options.firstOrNull { it.optionType == current.optionType && it.id != current.id && it.name.trim().equals(name, ignoreCase = true) }
                ?.let { return@withTransaction RenameResult.NameTaken(it.name) }
            if (current.name == name) return@withTransaction RenameResult.Renamed(0)

            val dao = database.dropdownRenameDao()
            val old = current.name
            // A change of case only keeps every row it already matches, so nothing is left to clear.
            val sameIgnoringCase = old.trim().equals(name, ignoreCase = true)
            dao.renameOption(current.id, name)
            val renamed = when (current.optionType) {
                EXPENSE_CATEGORY -> {
                    dao.renameBudgetCategory(old, name)
                    dao.renameBucketCategory(old, name)
                    if (!sameIgnoringCase) {
                        dao.deleteBudgetCategory(old)
                        dao.deleteBucketCategory(old)
                    }
                    dao.renameMerchantRuleCategory(TransactionType.EXPENSE, old, name)
                    dao.renameRepeatRuleCategory(TransactionType.EXPENSE, old, name)
                    dao.renameCapturedSuggestion(DEBIT, old, name)
                    dao.renameTripExpenseCategory(old, name)
                    dao.renameTransactionCategory(TransactionType.EXPENSE, old, name)
                }
                INCOME_CATEGORY -> {
                    dao.renameMerchantRuleCategory(TransactionType.INCOME, old, name)
                    dao.renameRepeatRuleCategory(TransactionType.INCOME, old, name)
                    dao.renameCapturedSuggestion(CREDIT, old, name)
                    dao.renameTransactionCategory(TransactionType.INCOME, old, name)
                }
                ACCOUNT_GROUP -> {
                    dao.renameAccountGroup(old, name)
                    0
                }
                else -> 0
            }
            RenameResult.Renamed(renamed)
        }
    }

    /**
     * Deletes an option. A deleted expense Category also leaves the running cycle's Buckets, so the
     * bucket editor stops offering it; closed cycles keep their routing as history.
     */
    suspend fun delete(option: DropdownOption) {
        database.withTransaction {
            database.dropdownOptionDao().delete(option)
            if (option.optionType == EXPENSE_CATEGORY) pruneRunningCycle()
        }
    }

    /**
     * Removes the running cycle's routing for Categories no longer in the expense Category list.
     * Does nothing while that list is empty (a fresh install before its first merge), so it can't wipe them.
     */
    suspend fun pruneRunningCycle(): Int = database.withTransaction {
        val keep = database.dropdownOptionDao().getAllOptionsSnapshot()
            .filter { it.optionType == EXPENSE_CATEGORY }
            .map { CycleSummaryBuilder.categoryKey(it.name) }
            .toSet()
        if (keep.isEmpty()) return@withTransaction 0
        val dao = database.dropdownRenameDao()
        val stale = dao.runningCycleRoutings().filter { CycleSummaryBuilder.categoryKey(it.category) !in keep }
        if (stale.isNotEmpty()) dao.deleteRoutings(stale.map { it.id })
        stale.size
    }

    private companion object {
        const val EXPENSE_CATEGORY = "EXPENSE_CATEGORY"
        const val INCOME_CATEGORY = "INCOME_CATEGORY"
        const val ACCOUNT_GROUP = "ACCOUNT_GROUP"
        const val DEBIT = "DEBIT"
        const val CREDIT = "CREDIT"
    }
}
