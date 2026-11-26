package com.issaczerubbabel.ledgar.capture.categorize

import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleOrigin
import com.issaczerubbabel.ledgar.util.TransactionType

sealed interface RuleChange {
    data object None : RuleChange
    data class Upsert(val rule: MerchantRule) : RuleChange
    data class Delete(val merchantNorm: String, val direction: String) : RuleChange
}

/**
 * How a Confirm changes the saved Merchant rule for that merchant and direction: three identical
 * outcomes in a row earn a LEARNED rule, and two overrides in a row delete it. An outcome is the
 * type plus its Category, or plus the other Account for a Transfer. A USER rule (set with "Always
 * use this") never has its outcome changed automatically.
 *
 * Every Confirm also sets the rule's description: the name the user gave the merchant, or none
 * when they kept the bank's text. A rule with a description outlives losing its outcome: it goes
 * back to being a Candidate, which is never matched, so the rename stays without the outcome.
 */
object MerchantRuleLearner {

    const val CONFIRMATIONS_TO_LEARN = 3
    const val OVERRIDES_TO_DELETE = 2

    /**
     * [finalCategory] is ignored for a Transfer, whose rule stores "Transfer" and [toAccountId]: the
     * Transfer's other Account (To for a debit, From for a credit).
     */
    fun onConfirmed(
        existing: MerchantRule?,
        merchantNorm: String,
        direction: String,
        type: String,
        finalCategory: String,
        alwaysUse: Boolean,
        now: Long,
        description: String? = null,
        toAccountId: Long? = null
    ): RuleChange {
        if (merchantNorm.isBlank()) return RuleChange.None
        val isTransfer = type == TransactionType.TRANSFER
        val category = if (isTransfer) TransactionType.TRANSFER else finalCategory
        val otherAccountId = if (isTransfer) toAccountId else null

        if (alwaysUse) {
            return RuleChange.Upsert(
                MerchantRule(
                    merchantNorm = merchantNorm,
                    direction = direction,
                    category = category,
                    type = type,
                    defaultAccountId = existing?.defaultAccountId,
                    origin = MerchantRuleOrigin.USER,
                    hitCount = (existing?.hitCount ?: 0) + 1,
                    streak = 0,
                    updatedAt = now,
                    description = description,
                    toAccountId = otherAccountId
                )
            )
        }

        fun candidate(hits: Int) = MerchantRule(
            merchantNorm = merchantNorm,
            direction = direction,
            category = category,
            type = type,
            origin = MerchantRuleOrigin.CANDIDATE,
            hitCount = hits,
            streak = 1,
            updatedAt = now,
            description = description,
            toAccountId = otherAccountId
        )

        if (existing == null) return RuleChange.Upsert(candidate(hits = 1))
        if (existing.origin == MerchantRuleOrigin.USER) {
            return if (existing.description == description) {
                RuleChange.None
            } else {
                RuleChange.Upsert(existing.copy(description = description, updatedAt = now))
            }
        }

        val agrees = existing.type == type && if (isTransfer) {
            existing.toAccountId == otherAccountId
        } else {
            existing.category.equals(category, ignoreCase = true)
        }
        val hits = existing.hitCount + 1

        return when (existing.origin) {
            MerchantRuleOrigin.LEARNED -> when {
                agrees -> RuleChange.Upsert(existing.copy(hitCount = hits, streak = 0, updatedAt = now, description = description))
                existing.streak + 1 < OVERRIDES_TO_DELETE ->
                    RuleChange.Upsert(existing.copy(streak = existing.streak + 1, updatedAt = now, description = description))
                description != null -> RuleChange.Upsert(candidate(hits = hits))
                else -> RuleChange.Delete(merchantNorm, direction)
            }
            else -> { // CANDIDATE
                if (!agrees) {
                    RuleChange.Upsert(candidate(hits = hits))
                } else if (existing.streak + 1 >= CONFIRMATIONS_TO_LEARN) {
                    RuleChange.Upsert(
                        existing.copy(
                            origin = MerchantRuleOrigin.LEARNED, hitCount = hits, streak = 0, updatedAt = now, description = description
                        )
                    )
                } else {
                    RuleChange.Upsert(
                        existing.copy(hitCount = hits, streak = existing.streak + 1, updatedAt = now, description = description)
                    )
                }
            }
        }
    }
}
