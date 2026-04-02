package com.sheetsync.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.sheetsync.data.local.entity.AccountRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AccountRecord): Long

    @Update
    suspend fun update(record: AccountRecord)

    @Delete
    suspend fun delete(record: AccountRecord)

    @Query("SELECT * FROM account_records ORDER BY accountName ASC")
    fun getAllAccounts(): Flow<List<AccountRecord>>

    @Query("SELECT * FROM account_records ORDER BY accountName ASC")
    suspend fun getAllAccountsSnapshot(): List<AccountRecord>

    @Query("SELECT * FROM account_records WHERE isHidden = 0 ORDER BY accountName ASC")
    fun getVisibleAccounts(): Flow<List<AccountRecord>>

    @Query("SELECT * FROM account_records WHERE id = :accountId LIMIT 1")
    suspend fun getAccountById(accountId: Long): AccountRecord?
}
