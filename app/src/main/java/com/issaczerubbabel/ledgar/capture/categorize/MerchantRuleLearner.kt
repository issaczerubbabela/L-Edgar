package com.issaczerubbabel.ledgar.capture.categorize

import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleOrigin

sealed interface RuleChange {
    data object None : RuleChange
    data class Upsert(val rule: MerchantRule) : RuleChange
    data class Delete(val merchantNorm: String) : RuleChange
}

/**
 * How a Confirm changes the saved Merchant rule for that merchant: three same-category
 * confirmations in a row earn a LEARNED rule, and two overrides in a row delete it. A USER rule
 * (set with "Always use this") never has its Category changed automatically.
 *
 * Every Confirm also sets the rule's description: the name the user gave the merchant, or none
 * when they kept the bank's text. A rule with a description outlives losing its Category: it goes
 * back to being a Candidate, which is never matched, so the rename stays without the Category.
 */
object MerchantRuleLearner {

    const val CONFIRMATIONS_TO_LEARN = 3
    const val OVERRIDES_TO_DELETE = 2

    fun onConfirmed(
        existing: MerchantRule?,
        merchantNorm: String,
        type: String,
        finalCategory: String,
        alwaysUse: Boolean,
        now: Long,
        description: String? = null
    ): RuleChange {
        if (merchantNorm.isBlank()) return RuleChange.None

        if (alwaysUse) {
            return RuleChange.Upsert(
                MerchantRule(
                    merchantNorm = merchantNorm,
                    category = finalCategory,
                    type = type,
                    defaultAccountId = existing?.defaultAccountId,
                    origin = MerchantRuleOrigin.USER,
                    hitCount = (existing?.hitCount ?: 0) + 1,
                    streak = 0,
                    updatedAt = now,
                    description = description
                )
            )
        }

        if (existing == null) {
            return RuleChange.Upsert(candidate(merchantNorm, type, finalCategory, hits = 1, now = now, description = description))
        }
        if (existing.type != type || existing.origin == MerchantRuleOrigin.USER) {
            return if (existing.description == description) {
                RuleChange.None
            } else {
                RuleChange.Upsert(existing.copy(description = description, updatedAt = now))
            }
        }

        val agrees = existing.category.equals(finalCategory, ignoreCase = true)
        val hits = existing.hitCount + 1

        return when (existing.origin) {
            MerchantRuleOrigin.LEARNED -> when {
                agrees -> RuleChange.Upsert(existing.copy(hitCount = hits, streak = 0, updatedAt = now, description = description))
                existing.streak + 1 < OVERRIDES_TO_DELETE ->
                    RuleChange.Upsert(existing.copy(streak = existing.streak + 1, updatedAt = now, description = description))
                description != null ->
                    RuleChange.Upsert(candidate(merchantNorm, type, finalCategory, hits = hits, now = now, description = description))
                else -> RuleChange.Delete(merchantNorm)
            }
            else -> { // CANDIDATE
                if (!agrees) {
                    RuleChange.Upsert(candidate(merchantNorm, type, finalCategory, hits = hits, now = now, description = description))
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

    /** A fresh run of one confirmation in [category]. */
    private fun candidate(merchantNorm: String, type: String, category: String, hits: Int, now: Long, description: String?) =
        MerchantRule(
            merchantNorm = merchantNorm,
            category = category,
            type = type,
            origin = MerchantRuleOrigin.CANDIDATE,
            hitCount = hits,
            streak = 1,
            updatedAt = now,
            description = description
        )
}
