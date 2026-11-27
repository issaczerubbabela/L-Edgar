package com.issaczerubbabel.ledgar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleKey
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantRuleDao {

    @Query("SELECT * FROM merchant_rules WHERE merchantNorm = :merchantNorm AND direction = :direction LIMIT 1")
    suspend fun get(merchantNorm: String, direction: String): MerchantRule?

    suspend fun get(key: MerchantRuleKey): MerchantRule? = get(key.merchantNorm, key.direction)

    /** Merchants the user renamed, for titling Captured transactions. */
    @Query("SELECT merchantNorm, direction, description FROM merchant_rules WHERE description IS NOT NULL")
    fun observeRenames(): Flow<List<MerchantRename>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRule)

    @Query("DELETE FROM merchant_rules WHERE merchantNorm = :merchantNorm AND direction = :direction")
    suspend fun delete(merchantNorm: String, direction: String)
}

data class MerchantRename(val merchantNorm: String, val direction: String, val description: String)
