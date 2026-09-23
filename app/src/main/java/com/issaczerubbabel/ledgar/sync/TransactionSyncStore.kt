package com.issaczerubbabel.ledgar.sync

import com.issaczerubbabel.ledgar.data.local.dao.AccountDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.normalizeTimestampKey
import javax.inject.Inject

/** The local side of Transaction Sync. Settling is conditional on the version Sync sent. */
interface TransactionSyncStore {
    suspend fun pending(): List<ExpenseRecord>
    suspend fun remoteTimestampsInUse(): Set<String>
    suspend fun assignRemoteTimestamp(id: Long, timestamp: String)
    suspend fun markSyncedIfUnchanged(id: Long, version: Long)
    suspend fun deleteIfUnchanged(id: Long, version: Long)
    suspend fun accountNamesById(): Map<Long, String>
}

class RoomTransactionSyncStore @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val accountDao: AccountDao
) : TransactionSyncStore {
    override suspend fun pending() = expenseDao.getUnsyncedRecords()

    override suspend fun remoteTimestampsInUse() =
        expenseDao.getAllRemoteTimestamps().mapNotNull(::normalizeTimestampKey).toSet()

    override suspend fun assignRemoteTimestamp(id: Long, timestamp: String) {
        expenseDao.assignRemoteTimestamp(id, timestamp)
    }

    override suspend fun markSyncedIfUnchanged(id: Long, version: Long) {
        expenseDao.markSyncedIfUnchanged(id, version)
    }

    override suspend fun deleteIfUnchanged(id: Long, version: Long) {
        expenseDao.deleteSyncedDeleteIfUnchanged(id, version)
    }

    override suspend fun accountNamesById() =
        accountDao.getAllAccountsSnapshot().associate { it.id to it.accountName }
}
