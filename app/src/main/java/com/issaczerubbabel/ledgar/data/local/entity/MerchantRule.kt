package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A saved mapping from a normalized merchant name to a Category, used to categorize future
 * Captured transactions from the same merchant. See "Merchant rule" in CONTEXT.md.
 */
@Entity(tableName = "merchant_rules")
data class MerchantRule(
    @PrimaryKey val merchantNorm: String,
    val category: String,
    val type: String, // Expense | Income
    val defaultAccountId: Long? = null,
    val origin: String, // USER | LEARNED
    val hitCount: Int = 0,
    /** Consecutive same-category confirmations; a LEARNED rule is deleted after 2 overrides. */
    val streak: Int = 0,
    val updatedAt: Long
)
