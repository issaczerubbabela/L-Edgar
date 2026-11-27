package com.issaczerubbabel.ledgar.data.local.entity

import androidx.room.Entity

object MerchantRuleOrigin {
    /** Set by the user ("Always use this"); never demoted automatically. */
    const val USER = "USER"

    /** Earned by repeated confirmations; deleted after two overrides in a row. */
    const val LEARNED = "LEARNED"

    /** Not a rule yet: only tracks a run of identical confirmations. Never matched. */
    const val CANDIDATE = "CANDIDATE"
}

/** A Merchant rule is looked up by merchant and by the bank's direction (DEBIT or CREDIT). */
data class MerchantRuleKey(val merchantNorm: String, val direction: String)

/**
 * A saved mapping from a normalized merchant name and the bank's direction to an outcome: Expense
 * or Income with a Category, or a Transfer with the other Account. It also keeps the description
 * the user last renamed the merchant to. See "Merchant rule" in CONTEXT.md.
 */
@Entity(tableName = "merchant_rules", primaryKeys = ["merchantNorm", "direction"])
data class MerchantRule(
    val merchantNorm: String,
    /** DEBIT | CREDIT, as the bank wrote it. A debit and a credit from one merchant are two rules. */
    val direction: String,
    /** The Category, or "Transfer" for a Transfer rule. */
    val category: String,
    val type: String, // Expense | Income | Transfer
    val defaultAccountId: Long? = null,
    val origin: String, // see MerchantRuleOrigin
    /** Confirmations recorded for this merchant. */
    val hitCount: Int = 0,
    /** CANDIDATE: consecutive identical confirmations. LEARNED: consecutive overrides. */
    val streak: Int = 0,
    val updatedAt: Long,
    /** What the user renamed this merchant to when confirming, shown instead of the bank's text. */
    val description: String? = null,
    /**
     * A Transfer rule's other Account: the To-account of a debit, the From-account of a credit.
     * Null for Expense and Income rules.
     */
    val toAccountId: Long? = null
)
