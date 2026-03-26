package com.sheetsync.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sheetsync.data.local.entity.AccountRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AccountRecord): Long

    @Delete
    suspend fun delete(record: AccountRecord)

    @Query("SELECT * FROM account_records ORDER BY accountName ASC")
    fun getAllAccounts(): Flow<List<AccountRecord>>
}
