package com.issaczerubbabel.ledgar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantRuleDao {

    @Query("SELECT * FROM merchant_rules WHERE merchantNorm = :merchantNorm LIMIT 1")
    suspend fun getByMerchant(merchantNorm: String): MerchantRule?

    /** Merchants the user renamed, for titling Captured transactions. */
    @Query("SELECT merchantNorm, description FROM merchant_rules WHERE description IS NOT NULL")
    fun observeRenames(): Flow<List<MerchantRename>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRule)

    @Query("DELETE FROM merchant_rules WHERE merchantNorm = :merchantNorm")
    suspend fun delete(merchantNorm: String)
}

data class MerchantRename(val merchantNorm: String, val description: String)
