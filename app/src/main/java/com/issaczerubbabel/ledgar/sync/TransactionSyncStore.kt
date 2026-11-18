package com.issaczerubbabel.ledgar.sync

import androidx.room.withTransaction
import com.google.gson.Gson
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.TransactionType
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
        val needsAccounts = steps.any {
            it is MergeStep.InsertFromSheet || it is MergeStep.ApplyFromSheet || it is MergeStep.RelinkAccounts
        }
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
        is MergeStep.RelinkAccounts -> relink(step, checkNotNull(accounts))
    }

    /** Only ids change, and only to an Account the Sheet's name actually matches: a name with no Account leaves the row as it is. */
    private suspend fun relink(step: MergeStep.RelinkAccounts, accounts: SheetTransactionMapper.AccountContext): Boolean {
        val current = expenseDao.getById(step.localId)
            ?.takeIf { it.localVersion == step.version && it.isSynced }
            ?: return false
        val named = mapper.namedAccounts(step.row.dto, accounts)
        val updated = when (TransactionContent.canonicalType(current.type)) {
            TransactionType.TRANSFER -> current.copy(
                fromAccountId = named.fromId ?: current.fromAccountId,
                toAccountId = named.toId ?: current.toAccountId,
                fromAccountName = named.fromName ?: current.fromAccountName,
                toAccountName = named.toName ?: current.toAccountName
            )
            else -> {
                val id = named.accountId ?: current.accountId
                current.copy(
                    accountId = id,
                    fromAccountId = if (current.type == TransactionType.EXPENSE) id else current.fromAccountId,
                    toAccountId = if (current.type == TransactionType.INCOME) id else current.toAccountId,
                    accountName = named.accountName ?: current.accountName
                )
            }
        }
        val idsChanged = if (TransactionContent.canonicalType(current.type) == TransactionType.TRANSFER) {
            updated.fromAccountId != current.fromAccountId || updated.toAccountId != current.toAccountId
        } else {
            updated.accountId != current.accountId
        }
        if (!idsChanged) return false
        return expenseDao.relinkIfUnchanged(
            id = current.id,
            version = current.localVersion,
            accountId = updated.accountId,
            fromAccountId = updated.fromAccountId,
            toAccountId = updated.toAccountId,
            accountName = updated.accountName,
            fromAccountName = updated.fromAccountName,
            toAccountName = updated.toAccountName
        ) > 0
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
