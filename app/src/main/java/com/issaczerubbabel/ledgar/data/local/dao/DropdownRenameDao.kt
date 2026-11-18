package com.issaczerubbabel.ledgar.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory

/**
 * Everywhere a Category or Account group is stored by name, for renaming one Dropdown option.
 * Names are matched ignoring case and surrounding space, like everywhere else in the app.
 */
@Dao
interface DropdownRenameDao {

    @Query("UPDATE dropdown_options SET name = :newName WHERE id = :optionId")
    suspend fun renameOption(optionId: Long, newName: String)

    /** Renames the Category of every Transaction of [type]; each waits to sync so the Sheet shows the new name. */
    @Query(
        """
        UPDATE expense_records
        SET category = :newName,
            isSynced = 0,
            syncAction = CASE WHEN syncAction = 'INSERT' THEN syncAction ELSE 'UPDATE' END
        WHERE type = :type AND syncAction != 'DELETE' AND trim(category) = trim(:oldName) COLLATE NOCASE
        """
    )
    suspend fun renameTransactionCategory(type: String, oldName: String, newName: String): Int

    /** A month that already has a Budget under the new name keeps that one. */
    @Query("UPDATE OR IGNORE budgets SET category = :newName WHERE trim(category) = trim(:oldName) COLLATE NOCASE")
    suspend fun renameBudgetCategory(oldName: String, newName: String)

    @Query("DELETE FROM budgets WHERE trim(category) = trim(:oldName) COLLATE NOCASE")
    suspend fun deleteBudgetCategory(oldName: String)

    /** In every cycle, closed ones too: their Transactions are renamed as well, so their spend stays in its Bucket. */
    @Query("UPDATE OR IGNORE bucket_categories SET category = :newName WHERE trim(category) = trim(:oldName) COLLATE NOCASE")
    suspend fun renameBucketCategory(oldName: String, newName: String)

    @Query("DELETE FROM bucket_categories WHERE trim(category) = trim(:oldName) COLLATE NOCASE")
    suspend fun deleteBucketCategory(oldName: String)

    @Query("UPDATE merchant_rules SET category = :newName WHERE type = :type AND trim(category) = trim(:oldName) COLLATE NOCASE")
    suspend fun renameMerchantRuleCategory(type: String, oldName: String, newName: String)

    @Query("UPDATE recurring_rules SET category = :newName WHERE type = :type AND trim(category) = trim(:oldName) COLLATE NOCASE")
    suspend fun renameRepeatRuleCategory(type: String, oldName: String, newName: String)

    /** Only Captured transactions still waiting for the user; decided ones are history. */
    @Query(
        """
        UPDATE captured_transactions SET suggestedCategory = :newName
        WHERE status = 'PENDING' AND direction = :direction AND trim(suggestedCategory) = trim(:oldName) COLLATE NOCASE
        """
    )
    suspend fun renameCapturedSuggestion(direction: String, oldName: String, newName: String)

    @Query("UPDATE trip_expenses SET category = :newName WHERE trim(category) = trim(:oldName) COLLATE NOCASE")
    suspend fun renameTripExpenseCategory(oldName: String, newName: String)

    @Query("UPDATE account_records SET groupName = :newName WHERE trim(groupName) = trim(:oldName) COLLATE NOCASE")
    suspend fun renameAccountGroup(oldName: String, newName: String): Int

    /** Every Category routed into the running cycle's Buckets. */
    @Query(
        """
        SELECT bucket_categories.* FROM bucket_categories
        JOIN budget_cycles ON budget_cycles.id = bucket_categories.cycleId
        WHERE budget_cycles.closedAt IS NULL
        """
    )
    suspend fun runningCycleRoutings(): List<BucketCategory>

    @Query("DELETE FROM bucket_categories WHERE id IN (:ids)")
    suspend fun deleteRoutings(ids: List<Long>)
}
