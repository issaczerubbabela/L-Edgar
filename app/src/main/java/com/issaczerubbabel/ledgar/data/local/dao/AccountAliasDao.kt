package com.issaczerubbabel.ledgar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.issaczerubbabel.ledgar.data.local.entity.AccountAlias

@Dao
interface AccountAliasDao {

    @Query("SELECT * FROM account_aliases WHERE alias = :alias LIMIT 1")
    suspend fun getByAlias(alias: String): AccountAlias?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alias: AccountAlias)

    @Query("SELECT * FROM account_aliases")
    suspend fun getAllSnapshot(): List<AccountAlias>
}
