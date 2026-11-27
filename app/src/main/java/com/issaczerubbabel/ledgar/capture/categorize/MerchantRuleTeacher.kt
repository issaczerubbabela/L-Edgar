package com.issaczerubbabel.ledgar.capture.categorize

import com.issaczerubbabel.ledgar.data.local.dao.MerchantRuleDao
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import javax.inject.Inject

/**
 * Saves what a capture's outcome teaches its merchant's rule ([MerchantRuleLearner]). Shared by
 * Confirm, Same one and an edit in the Ledger, so all three learn exactly the same way.
 */
class MerchantRuleTeacher @Inject constructor(private val merchantRuleDao: MerchantRuleDao) {

    /**
     * [description] picks the rule's description from the existing rule: the user's rename, or
     * null to forget it. [toAccountId] is a Transfer's other Account. Runs in the caller's Room
     * transaction, if any. A capture without a merchant teaches nothing.
     */
    suspend fun teach(
        capture: CapturedTransaction,
        type: String,
        category: String,
        alwaysUse: Boolean,
        description: (existing: MerchantRule?) -> String?,
        toAccountId: Long?,
        now: Long = System.currentTimeMillis()
    ) {
        val merchant = capture.merchantNorm ?: return
        val existing = merchantRuleDao.get(merchant, capture.direction)
        val change = MerchantRuleLearner.onConfirmed(
            existing = existing,
            merchantNorm = merchant,
            direction = capture.direction,
            type = type,
            finalCategory = category,
            alwaysUse = alwaysUse,
            now = now,
            description = description(existing),
            toAccountId = toAccountId
        )
        when (change) {
            is RuleChange.Upsert -> merchantRuleDao.upsert(change.rule)
            is RuleChange.Delete -> merchantRuleDao.delete(change.merchantNorm, change.direction)
            RuleChange.None -> Unit
        }
    }
}
