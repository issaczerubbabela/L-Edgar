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

    /**
     * The Accounts that aliases seen on [sender]'s alerts point to, for guessing an unknown number's
     * Account. Any of a capture's Alerts counts, not only the first.
     */
    @Query(
        "SELECT DISTINCT a.accountId FROM account_aliases a " +
            "INNER JOIN captured_transactions c ON c.accountHint = a.alias " +
            "WHERE c.sender = :sender OR c.id IN (SELECT captureId FROM capture_alerts WHERE sender = :sender)"
    )
    suspend fun accountIdsAliasedForSender(sender: String): List<Long>
}
