package com.issaczerubbabel.ledgar.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AccountRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<AccountRecord>)

    @Update
    suspend fun update(record: AccountRecord)

    @Delete
    suspend fun delete(record: AccountRecord)

    @Query("DELETE FROM account_records")
    suspend fun deleteAll()

    @Query("SELECT * FROM account_records ORDER BY displayOrder ASC")
    fun getAllAccounts(): Flow<List<AccountRecord>>

    @Query("SELECT * FROM account_records ORDER BY displayOrder ASC")
    suspend fun getAllAccountsSnapshot(): List<AccountRecord>

    @Query("SELECT * FROM account_records WHERE isHidden = 0 ORDER BY displayOrder ASC")
    fun getVisibleAccounts(): Flow<List<AccountRecord>>

    @Query("SELECT * FROM account_records WHERE id = :accountId LIMIT 1")
    suspend fun getAccountById(accountId: Long): AccountRecord?

    @Query("UPDATE account_records SET isHidden = :isHidden WHERE id = :accountId")
    suspend fun updateHiddenStatus(accountId: Long, isHidden: Boolean)

    @Query("SELECT displayOrder FROM account_records WHERE id = :accountId LIMIT 1")
    suspend fun getDisplayOrder(accountId: Long): Int?

    @Query("UPDATE account_records SET displayOrder = :displayOrder WHERE id = :accountId")
    suspend fun updateDisplayOrder(accountId: Long, displayOrder: Int)

    @Transaction
    suspend fun swapDisplayOrder(firstAccountId: Long, secondAccountId: Long) {
        if (firstAccountId == secondAccountId) return

        val firstOrder = getDisplayOrder(firstAccountId) ?: return
        val secondOrder = getDisplayOrder(secondAccountId) ?: return

        updateDisplayOrder(firstAccountId, secondOrder)
        updateDisplayOrder(secondAccountId, firstOrder)
    }

    @Transaction
    suspend fun overwriteAll(records: List<AccountRecord>) {
        deleteAll()
        if (records.isNotEmpty()) {
            insertAll(records)
        }
    }
}
