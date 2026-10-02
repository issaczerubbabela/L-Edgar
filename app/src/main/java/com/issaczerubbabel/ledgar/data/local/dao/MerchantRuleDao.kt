package com.issaczerubbabel.ledgar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule

@Dao
interface MerchantRuleDao {

    @Query("SELECT * FROM merchant_rules WHERE merchantNorm = :merchantNorm LIMIT 1")
    suspend fun getByMerchant(merchantNorm: String): MerchantRule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: MerchantRule)

    @Query("DELETE FROM merchant_rules WHERE merchantNorm = :merchantNorm")
    suspend fun delete(merchantNorm: String)
}
