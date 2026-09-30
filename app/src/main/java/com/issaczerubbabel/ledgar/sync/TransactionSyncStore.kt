package com.issaczerubbabel.ledgar.sync

import androidx.room.withTransaction
import com.google.gson.Gson
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import java.util.UUID
import javax.inject.Inject

/** The local side of Transaction Sync. Every write is conditional on the version Sync read. */
interface TransactionSyncStore {
    suspend fun allTransactions(): List<ExpenseRecord>
    suspend fun pending(): List<ExpenseRecord>
    /** Rows from before Transaction IDs that a Pull still has to link. */
    suspend fun hasRowsWithoutSyncId(): Boolean
    suspend fun accountNamesById(): Map<Long, String>

    /** Applies a Pull's steps in one database transaction and returns how many took effect. */
    suspend fun apply(steps: List<MergeStep>): Int
    suspend fun markSyncedIfUnchanged(id: Long, version: Long, revision: String?)
    suspend fun finishDeleteIfUnchanged(id: Long, version: Long)
}

class RoomTransactionSyncStore @Inject constructor(
    private val database: SheetSyncDatabase,
    private val mapper: SheetTransactionMapper
) : TransactionSyncStore {
    private val expenseDao = database.expenseDao()
    private val gson = Gson()

    override suspend fun allTransactions() = expenseDao.getAllRecordsSnapshot()

    override suspend fun pending() = expenseDao.getUnsyncedRecords()

    override suspend fun hasRowsWithoutSyncId() = expenseDao.countWithoutSyncId() > 0

    override suspend fun accountNamesById() =
        database.accountDao().getAllAccountsSnapshot().associate { it.id to it.accountName }

    override suspend fun apply(steps: List<MergeStep>): Int {
        if (steps.isEmpty()) return 0
        val needsAccounts = steps.any { it is MergeStep.InsertFromSheet || it is MergeStep.ApplyFromSheet }
        return database.withTransaction {
            val accounts = if (needsAccounts) mapper.resolveAccountContext() else null
            steps.count { step -> applyStep(step, accounts) }
        }
    }

    private suspend fun applyStep(step: MergeStep, accounts: SheetTransactionMapper.AccountContext?): Boolean = when (step) {
        is MergeStep.Link -> expenseDao.setSyncIdIfUnchanged(step.localId, step.version, step.syncId) > 0
        is MergeStep.AssignNewId ->
            expenseDao.setSyncIdIfUnchanged(step.localId, step.version, UUID.randomUUID().toString()) > 0
        is MergeStep.RecordBase -> expenseDao.setBaseIfUnchanged(step.localId, step.version, step.revision) > 0
        is MergeStep.Settle -> expenseDao.markSyncedIfUnchanged(step.localId, step.version, step.revision) > 0
        is MergeStep.Conflict ->
            expenseDao.setConflictIfUnchanged(step.localId, step.version, gson.toJson(step.row.dto)) > 0
        is MergeStep.ClearConflict -> expenseDao.setConflictIfUnchanged(step.localId, step.version, null) > 0
        is MergeStep.DeleteLocal -> expenseDao.deleteIfUnchanged(step.localId, step.version) > 0
        is MergeStep.FinishDelete -> expenseDao.finishDeleteIfUnchanged(step.localId, step.version) > 0
        is MergeStep.InsertFromSheet -> insertFromSheet(step.row, checkNotNull(accounts))
        is MergeStep.ApplyFromSheet -> applyFromSheet(step, checkNotNull(accounts))
    }

    private suspend fun insertFromSheet(row: PulledRow, accounts: SheetTransactionMapper.AccountContext): Boolean {
        val mapped = mapper.mapImportRecord(row.dto, accounts.accountsByName, accounts.accountsByGroup, accounts.fallbackAccountId)
        if (mapped.discarded) return false
        return expenseDao.insertIgnoringDuplicateSyncId(
            mapped.record.copy(
                id = 0,
                syncId = row.syncId,
                syncedRevision = row.revision,
                remoteTimestamp = row.timestampKey,
                isSynced = true,
                syncAction = "NONE"
            )
        ) > 0
    }

    private suspend fun applyFromSheet(step: MergeStep.ApplyFromSheet, accounts: SheetTransactionMapper.AccountContext): Boolean {
        val current = expenseDao.getById(step.localId)?.takeIf { it.localVersion == step.version } ?: return false
        val mapped = mapper.mapImportRecord(step.row.dto, accounts.accountsByName, accounts.accountsByGroup, accounts.fallbackAccountId)
        if (mapped.discarded) return false
        val sheet = mapped.record
        expenseDao.update(
            current.copy(
                date = sheet.date,
                type = sheet.type,
                category = sheet.category,
                description = sheet.description,
                amount = sheet.amount,
                accountId = sheet.accountId,
                remarks = sheet.remarks,
                fromAccountId = sheet.fromAccountId,
                toAccountId = sheet.toAccountId,
                accountName = sheet.accountName,
                fromAccountName = sheet.fromAccountName,
                toAccountName = sheet.toAccountName,
                isBookmarked = sheet.isBookmarked,
                remoteTimestamp = step.row.timestampKey ?: current.remoteTimestamp,
                isSynced = true,
                syncAction = "NONE",
                syncedRevision = step.row.revision,
                sheetConflictJson = null
            )
        )
        return true
    }

    override suspend fun markSyncedIfUnchanged(id: Long, version: Long, revision: String?) {
        expenseDao.markSyncedIfUnchanged(id, version, revision)
    }

    override suspend fun finishDeleteIfUnchanged(id: Long, version: Long) {
        expenseDao.finishDeleteIfUnchanged(id, version)
    }
}
