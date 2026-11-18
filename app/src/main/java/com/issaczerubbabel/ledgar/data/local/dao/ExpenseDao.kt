package com.issaczerubbabel.ledgar.data.local.dao

import androidx.room.*
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.DescriptionUse
import kotlinx.coroutines.flow.Flow

/** Identifies one version of a Transaction that is waiting to Sync. */
data class PendingVersion(val id: Long, val localVersion: Long)

@Dao
interface ExpenseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ExpenseRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ExpenseRecord>)

    @Update
    suspend fun update(record: ExpenseRecord)

    /**
     * Saves an edit built from an earlier read without undoing what Sync did meanwhile: a Remote
     * timestamp and Transaction ID Sync assigned are kept, and a Transaction that never synced stays an `INSERT`.
     */
    @Transaction
    suspend fun updateKeepingSyncState(record: ExpenseRecord) {
        val current = getById(record.id) ?: return
        val syncAction = when {
            record.syncAction == "DELETE" -> "DELETE"
            current.syncAction == "INSERT" -> "INSERT"
            else -> record.syncAction
        }
        update(
            record.copy(
                remoteTimestamp = current.remoteTimestamp?.takeIf { it.isNotBlank() } ?: record.remoteTimestamp,
                syncAction = syncAction,
                syncId = current.syncId ?: record.syncId,
                syncedRevision = current.syncedRevision,
                sheetConflictJson = current.sheetConflictJson
            )
        )
    }

    @Query("SELECT * FROM expense_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ExpenseRecord?

    @Query("SELECT * FROM expense_records WHERE syncAction != 'DELETE' ORDER BY date DESC")
    fun getAllRecords(): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expense_records WHERE isBookmarked = 1 AND syncAction != 'DELETE' ORDER BY date DESC, id DESC")
    fun getBookmarkedTransactions(): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expense_records")
    suspend fun getAllRecordsSnapshot(): List<ExpenseRecord>

    @Query("SELECT * FROM expense_records WHERE type = :type AND syncAction != 'DELETE' ORDER BY date DESC")
    fun getByType(type: String): Flow<List<ExpenseRecord>>

    @Query("SELECT * FROM expense_records WHERE isSynced = 0")
    suspend fun getUnsyncedRecords(): List<ExpenseRecord>

    @Query("SELECT id, localVersion FROM expense_records WHERE isSynced = 0 ORDER BY id")
    fun observePendingVersions(): Flow<List<PendingVersion>>

    /**
     * Settles a Transaction whose content the Sheet now has, recording the Sheet row's [revision] as
     * the base for the next merge, unless it changed after Sync read it.
     */
    @Query(
        """
        UPDATE expense_records
        SET isSynced = 1, syncAction = 'NONE', syncedRevision = :revision, sheetConflictJson = NULL
        WHERE id = :id AND localVersion = :version AND syncAction != 'DELETE'
        """
    )
    suspend fun markSyncedIfUnchanged(id: Long, version: Long, revision: String?): Int

    // The queries below touch only sync bookkeeping columns, which don't raise localVersion, so several
    // steps of one Pull can apply to the same row against the version it was planned from.

    @Query("UPDATE expense_records SET syncId = :syncId WHERE id = :id AND localVersion = :version AND syncId IS NULL")
    suspend fun setSyncIdIfUnchanged(id: Long, version: Long, syncId: String): Int

    @Query("UPDATE expense_records SET syncedRevision = :revision WHERE id = :id AND localVersion = :version")
    suspend fun setBaseIfUnchanged(id: Long, version: Long, revision: String?): Int

    @Query("UPDATE expense_records SET sheetConflictJson = :sheetJson WHERE id = :id AND localVersion = :version")
    suspend fun setConflictIfUnchanged(id: Long, version: Long, sheetJson: String?): Int

    @Query("DELETE FROM expense_records WHERE id = :id AND localVersion = :version")
    suspend fun deleteIfUnchanged(id: Long, version: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnoringDuplicateSyncId(record: ExpenseRecord): Long

    @Query("UPDATE expense_records SET syncedRevision = :sheetRevision, sheetConflictJson = NULL WHERE id = :id")
    suspend fun resolveConflictKeepingPhone(id: Long, sheetRevision: String?)

    @Query("UPDATE expense_records SET sheetConflictJson = NULL, isSynced = 0, syncAction = 'DELETE' WHERE id = :id")
    suspend fun clearConflictAndMarkDeleted(id: Long)

    @Query("UPDATE expense_records SET isSynced = 0, syncAction = 'UPDATE' WHERE id IN (:ids) AND syncAction != 'DELETE'")
    suspend fun markForReupload(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM expense_records WHERE syncId IS NULL")
    suspend fun countWithoutSyncId(): Int

    @Query("SELECT * FROM expense_records WHERE sheetConflictJson IS NOT NULL AND syncAction != 'DELETE' ORDER BY date DESC, id DESC")
    fun observeConflicts(): Flow<List<ExpenseRecord>>

    /** Removes a deleted Transaction for good once the Sheet no longer has it, unless it changed since Sync read it. */
    @Query("DELETE FROM expense_records WHERE id = :id AND localVersion = :version AND syncAction = 'DELETE'")
    suspend fun finishDeleteIfUnchanged(id: Long, version: Long): Int

    @Delete
    suspend fun delete(record: ExpenseRecord)

    @Query(
        """
        UPDATE expense_records
        SET isBookmarked = :isBookmarked,
            isSynced = 0,
            syncAction = CASE WHEN syncAction IN ('INSERT', 'DELETE') THEN syncAction ELSE 'UPDATE' END
        WHERE id = :id
        """
    )
    suspend fun updateBookmarkStatus(id: Long, isBookmarked: Boolean)

    @Query(
        """
        UPDATE expense_records
        SET isSynced = 0,
            syncAction = 'DELETE'
        WHERE id IN (:ids)
          AND syncAction != 'DELETE'
        """
    )
    suspend fun markTransactionsDeletedByIds(ids: List<Long>)

    @Query(
        """
        UPDATE expense_records
        SET isSynced = 0,
            syncAction = 'DELETE'
        WHERE id = :id
          AND syncAction != 'DELETE'
        """
    )
    suspend fun markTransactionDeletedById(id: Long)

    @Query(
        """
        UPDATE expense_records
        SET date = :newDate,
            isSynced = 0,
            syncAction = CASE WHEN syncAction IN ('INSERT', 'DELETE') THEN syncAction ELSE 'UPDATE' END
        WHERE id IN (:ids)
        """
    )
    suspend fun updateTransactionsDateByIds(ids: List<Long>, newDate: String)

    /**
     * Gives [newCategory], a Category of [type], to those of [ids] that are that type. A guard even if
     * the caller is wrong: a Transfer or Balance adjustment never gets a Category, and an Expense never
     * an income one.
     */
    @Query(
        """
        UPDATE expense_records
        SET category = :newCategory,
            isSynced = 0,
            syncAction = CASE WHEN syncAction IN ('INSERT', 'DELETE') THEN syncAction ELSE 'UPDATE' END
        WHERE id IN (:ids)
          AND type = :type
          AND type IN ('Expense', 'Income')
        """
    )
    suspend fun updateTransactionsCategoryByIds(ids: List<Long>, newCategory: String, type: String)

    /** Moves those of [ids] that have one Account to [accountId]; a Transfer keeps its from and to Accounts. */
    @Query(
        """
        UPDATE expense_records
        SET accountId = CASE WHEN type IN ('Expense', 'Income', 'Adjustment') THEN :accountId ELSE accountId END,
            fromAccountId = CASE WHEN type = 'Expense' THEN :accountId ELSE fromAccountId END,
            toAccountId = CASE WHEN type = 'Income' THEN :accountId ELSE toAccountId END,
            isSynced = 0,
            syncAction = CASE WHEN syncAction IN ('INSERT', 'DELETE') THEN syncAction ELSE 'UPDATE' END
        WHERE id IN (:ids)
          AND type IN ('Expense', 'Income', 'Adjustment')
        """
    )
    suspend fun updateTransactionsAssetByIds(ids: List<Long>, accountId: Long)

    @Query(
        """
        UPDATE expense_records
        SET description = :newDescription,
            isSynced = 0,
            syncAction = CASE WHEN syncAction IN ('INSERT', 'DELETE') THEN syncAction ELSE 'UPDATE' END
        WHERE id IN (:ids)
        """
    )
    suspend fun updateTransactionsDescriptionByIds(ids: List<Long>, newDescription: String)

    @Query("DELETE FROM expense_records WHERE id = :id")
    suspend fun hardDeleteById(id: Long)

    @Query(
        """
        UPDATE expense_records
        SET isSynced = 0,
            syncAction = 'DELETE'
        WHERE (accountId = :accountId OR fromAccountId = :accountId OR toAccountId = :accountId)
          AND syncAction != 'DELETE'
        """
    )
    suspend fun markLinkedTransactionsDeletedForAccount(accountId: Long)

    @Query(
        """
        UPDATE expense_records
        SET accountId = CASE WHEN accountId = :fromAccountId THEN :toAccountId ELSE accountId END,
            fromAccountId = CASE WHEN fromAccountId = :fromAccountId THEN :toAccountId ELSE fromAccountId END,
            toAccountId = CASE WHEN toAccountId = :fromAccountId THEN :toAccountId ELSE toAccountId END,
            isSynced = 0,
            syncAction = CASE
                WHEN syncAction = 'INSERT' THEN 'INSERT'
                WHEN syncAction = 'DELETE' THEN 'DELETE'
                ELSE 'UPDATE'
            END
        WHERE (accountId = :fromAccountId OR fromAccountId = :fromAccountId OR toAccountId = :fromAccountId)
          AND syncAction != 'DELETE'
        """
    )
    suspend fun reassignLinkedTransactionsForAccount(fromAccountId: Long, toAccountId: Long)

    @Query("DELETE FROM expense_records")
    suspend fun deleteAll()

        @Query(
                """
                SELECT COUNT(*) FROM expense_records
                WHERE syncAction != 'DELETE'
                    AND (accountId = :accountId OR fromAccountId = :accountId OR toAccountId = :accountId)
                """
        )
        suspend fun countRecordsForAccount(accountId: Long): Int

    /**
     * Duplicate detection: matches on date + type + category + amount.
     * Returns the first matching record, or null if it's a new entry.
     */
    @Query("""
        SELECT * FROM expense_records
        WHERE date = :date
          AND type = :type
          AND category = :category
          AND amount = :amount
        LIMIT 1
    """)
    suspend fun findDuplicate(date: String, type: String, category: String, amount: Double): ExpenseRecord?

    /** Past Descriptions of [type] since [since] (yyyy-MM-dd), newest first, for DescriptionSuggestions. */
    @Query("""
        SELECT description, category, accountId, date, id FROM expense_records
        WHERE type = :type AND date >= :since AND syncAction != 'DELETE' AND TRIM(description) != ''
        ORDER BY date DESC, id DESC
        LIMIT :limit
    """)
    suspend fun recentForSuggestions(type: String, since: String, limit: Int = 500): List<DescriptionUse>

    /**
     * Fetch all records between two dates (inclusive).
     * Used for month aggregation and weekly breakdowns.
     */
    @Query("""
        SELECT * FROM expense_records
        WHERE date >= :startDate AND date <= :endDate
          AND syncAction != 'DELETE'
        ORDER BY date DESC
    """)
    fun getRecordsByDateRange(startDate: String, endDate: String): Flow<List<ExpenseRecord>>

    @Query(
        """
        SELECT * FROM expense_records
                WHERE (fromAccountId = :accountId OR toAccountId = :accountId)
                    AND syncAction != 'DELETE'
        ORDER BY date DESC, id DESC
        """
    )
    fun getRecordsForAccount(accountId: Long): Flow<List<ExpenseRecord>>

    @Query(
        """
        SELECT * FROM expense_records
        WHERE accountId = :accountId
          AND date >= :startDate
          AND date <= :endDate
          AND syncAction != 'DELETE'
        ORDER BY date DESC, id DESC
        """
    )
    fun getRecordsForAccountInMonth(accountId: Long, startDate: String, endDate: String): Flow<List<ExpenseRecord>>

    @Query(
        """
        SELECT * FROM expense_records
        WHERE accountId = :accountId
          AND date >= :startDate
          AND date <= :endDate
          AND syncAction != 'DELETE'
        ORDER BY date ASC, id ASC
        """
    )
    fun getTransactionsForAccountInMonth(accountId: Long, startDate: String, endDate: String): Flow<List<ExpenseRecord>>

    // ---- Account links by name -------------------------------------------------------------------
    // A Transaction names its Account by id. The accountName, fromAccountName and toAccountName columns
    // hold the name the Sheet gave, and only count while the id is null (toSheetDto prefers the id).

    /** Writes [name] into the name columns of every Transaction linked to Account [accountId], so the link can be rebuilt by name. */
    @Query(
        """
        UPDATE expense_records
        SET accountName = CASE WHEN accountId = :accountId THEN :name ELSE accountName END,
            fromAccountName = CASE WHEN fromAccountId = :accountId THEN :name ELSE fromAccountName END,
            toAccountName = CASE WHEN toAccountId = :accountId THEN :name ELSE toAccountName END
        WHERE accountId = :accountId OR fromAccountId = :accountId OR toAccountId = :accountId
        """
    )
    suspend fun rememberAccountName(accountId: Long, name: String)

    /** Links the Expenses, Incomes and Balance adjustments with no Account whose Sheet name is [name]. Returns how many. */
    @Query(
        """
        UPDATE expense_records
        SET accountId = :accountId,
            fromAccountId = CASE WHEN type = 'Expense' THEN :accountId ELSE fromAccountId END,
            toAccountId = CASE WHEN type = 'Income' THEN :accountId ELSE toAccountId END
        WHERE accountId IS NULL AND type != 'Transfer'
          AND trim(accountName) = trim(:name) COLLATE NOCASE
        """
    )
    suspend fun linkSingleAccountByName(accountId: Long, name: String): Int

    /** Links the Transfers coming from no Account whose Sheet from-name is [name]. Returns how many. */
    @Query(
        """
        UPDATE expense_records SET fromAccountId = :accountId
        WHERE fromAccountId IS NULL AND type = 'Transfer' AND trim(fromAccountName) = trim(:name) COLLATE NOCASE
        """
    )
    suspend fun linkTransferFromByName(accountId: Long, name: String): Int

    /** Links the Transfers going to no Account whose Sheet to-name is [name]. Returns how many. */
    @Query(
        """
        UPDATE expense_records SET toAccountId = :accountId
        WHERE toAccountId IS NULL AND type = 'Transfer' AND trim(toAccountName) = trim(:name) COLLATE NOCASE
        """
    )
    suspend fun linkTransferToByName(accountId: Long, name: String): Int

    /** Links every Transaction with no Account whose Sheet name is [name] to Account [accountId]. Returns how many. */
    @Transaction
    suspend fun linkAccountByName(accountId: Long, name: String): Int =
        linkSingleAccountByName(accountId, name) + linkTransferFromByName(accountId, name) + linkTransferToByName(accountId, name)

    /** Re-points a synced Transaction at the Accounts its Sheet row names, unless it changed since Sync read it. */
    @Query(
        """
        UPDATE expense_records
        SET accountId = :accountId, fromAccountId = :fromAccountId, toAccountId = :toAccountId,
            accountName = :accountName, fromAccountName = :fromAccountName, toAccountName = :toAccountName
        WHERE id = :id AND localVersion = :version AND isSynced = 1
        """
    )
    suspend fun relinkIfUnchanged(
        id: Long,
        version: Long,
        accountId: Long?,
        fromAccountId: Long?,
        toAccountId: Long?,
        accountName: String?,
        fromAccountName: String?,
        toAccountName: String?
    ): Int

    /**
     * Account names from the Sheet that this phone has no Account for, with how many Transactions use
     * each. A row either has no Account ([UnmatchedAccountName.linkedAccountId] null), or, from before
     * that was fixed, was put on a guessed fallback Account although its name matches none.
     */
    @Query(
        """
        SELECT name, linkedAccountId, COUNT(*) AS count FROM (
            SELECT trim(accountName) AS name, NULL AS linkedAccountId FROM expense_records
            WHERE syncAction != 'DELETE' AND type != 'Transfer' AND accountId IS NULL AND trim(ifnull(accountName, '')) != ''
            UNION ALL
            SELECT trim(fromAccountName), NULL FROM expense_records
            WHERE syncAction != 'DELETE' AND type = 'Transfer' AND fromAccountId IS NULL AND trim(ifnull(fromAccountName, '')) != ''
            UNION ALL
            SELECT trim(toAccountName), NULL FROM expense_records
            WHERE syncAction != 'DELETE' AND type = 'Transfer' AND toAccountId IS NULL AND trim(ifnull(toAccountName, '')) != ''
            UNION ALL
            SELECT trim(e.accountName), e.accountId FROM expense_records e
            WHERE e.syncAction != 'DELETE' AND e.type IN ('Expense', 'Income') AND e.accountId IS NOT NULL
              AND trim(ifnull(e.accountName, '')) != ''
              AND NOT EXISTS (
                  SELECT 1 FROM account_records m
                  WHERE trim(m.accountName) = trim(e.accountName) COLLATE NOCASE
                     OR trim(m.groupName) = trim(e.accountName) COLLATE NOCASE
              )
        )
        GROUP BY name COLLATE NOCASE, linkedAccountId
        ORDER BY count DESC, name COLLATE NOCASE
        """
    )
    fun observeUnmatchedAccountNames(): Flow<List<UnmatchedAccountName>>

    @Query(
        """
        UPDATE expense_records
        SET accountId = :accountId,
            fromAccountId = CASE WHEN type = 'Expense' THEN :accountId ELSE fromAccountId END,
            toAccountId = CASE WHEN type = 'Income' THEN :accountId ELSE toAccountId END,
            accountName = :accountName,
            fromAccountName = CASE WHEN type = 'Expense' THEN :accountName ELSE fromAccountName END,
            toAccountName = CASE WHEN type = 'Income' THEN :accountName ELSE toAccountName END,
            isSynced = CASE WHEN :markPending THEN 0 ELSE isSynced END,
            syncAction = CASE WHEN :markPending AND syncAction NOT IN ('INSERT', 'DELETE') THEN 'UPDATE' ELSE syncAction END
        WHERE syncAction != 'DELETE' AND type != 'Transfer'
          AND trim(accountName) = trim(:name) COLLATE NOCASE
          AND (accountId IS NULL OR NOT EXISTS (
              SELECT 1 FROM account_records m WHERE m.id = expense_records.accountId
                AND (trim(m.accountName) = trim(:name) COLLATE NOCASE OR trim(m.groupName) = trim(:name) COLLATE NOCASE)
          ))
        """
    )
    suspend fun mapSingleAccountName(name: String, accountId: Long, accountName: String, markPending: Boolean): Int

    @Query(
        """
        UPDATE expense_records
        SET fromAccountId = :accountId, fromAccountName = :accountName,
            isSynced = CASE WHEN :markPending THEN 0 ELSE isSynced END,
            syncAction = CASE WHEN :markPending AND syncAction NOT IN ('INSERT', 'DELETE') THEN 'UPDATE' ELSE syncAction END
        WHERE syncAction != 'DELETE' AND type = 'Transfer' AND fromAccountId IS NULL
          AND trim(fromAccountName) = trim(:name) COLLATE NOCASE
        """
    )
    suspend fun mapTransferFromName(name: String, accountId: Long, accountName: String, markPending: Boolean): Int

    @Query(
        """
        UPDATE expense_records
        SET toAccountId = :accountId, toAccountName = :accountName,
            isSynced = CASE WHEN :markPending THEN 0 ELSE isSynced END,
            syncAction = CASE WHEN :markPending AND syncAction NOT IN ('INSERT', 'DELETE') THEN 'UPDATE' ELSE syncAction END
        WHERE syncAction != 'DELETE' AND type = 'Transfer' AND toAccountId IS NULL
          AND trim(toAccountName) = trim(:name) COLLATE NOCASE
        """
    )
    suspend fun mapTransferToName(name: String, accountId: Long, accountName: String, markPending: Boolean): Int

    /**
     * Puts every Transaction listed under the unmatched [name] on Account [accountId], called [accountName].
     * When that name differs from [name] they wait to sync, so the Sheet shows the Account they are on now.
     */
    @Transaction
    suspend fun mapAccountName(name: String, accountId: Long, accountName: String): Int {
        val markPending = !name.trim().equals(accountName.trim(), ignoreCase = true)
        return mapSingleAccountName(name, accountId, accountName, markPending) +
            mapTransferFromName(name, accountId, accountName, markPending) +
            mapTransferToName(name, accountId, accountName, markPending)
    }

    /** Marks every Transaction on Account [accountId] as waiting to sync, so the Sheet learns its new name. */
    @Query(
        """
        UPDATE expense_records
        SET isSynced = 0,
            syncAction = CASE WHEN syncAction = 'INSERT' THEN syncAction ELSE 'UPDATE' END
        WHERE (accountId = :accountId OR fromAccountId = :accountId OR toAccountId = :accountId)
          AND syncAction != 'DELETE'
        """
    )
    suspend fun markAccountTransactionsPending(accountId: Long): Int
}

/** One row of [ExpenseDao.observeUnmatchedAccountNames]. */
data class UnmatchedAccountName(val name: String, val linkedAccountId: Long?, val count: Int)
