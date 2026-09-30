package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

object MerchantRuleOrigin {
    /** Set by the user ("Always use this"); never demoted automatically. */
    const val USER = "USER"

    /** Earned by repeated confirmations; deleted after two overrides in a row. */
    const val LEARNED = "LEARNED"

    /** Not a rule yet: only tracks a run of same-category confirmations. Never matched. */
    const val CANDIDATE = "CANDIDATE"
}

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
    val origin: String, // see MerchantRuleOrigin
    /** Confirmations recorded for this merchant. */
    val hitCount: Int = 0,
    /** CANDIDATE: consecutive same-category confirmations. LEARNED: consecutive overrides. */
    val streak: Int = 0,
    val updatedAt: Long
)
