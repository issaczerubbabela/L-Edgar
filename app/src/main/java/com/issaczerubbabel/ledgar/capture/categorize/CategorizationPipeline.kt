package com.issaczerubbabel.ledgar.capture.categorize

import com.issaczerubbabel.ledgar.capture.parse.Direction
import com.issaczerubbabel.ledgar.capture.parse.ParsedTxn
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleKey
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleOrigin
import com.issaczerubbabel.ledgar.util.TransactionType
import java.time.LocalDateTime

enum class ConfidenceBand {
    HIGH, CHECK, LOW;

    companion object {
        fun of(confidence: Double, hasCategory: Boolean): ConfidenceBand = when {
            !hasCategory -> LOW
            confidence >= 0.90 -> HIGH
            confidence >= 0.60 -> CHECK
            else -> LOW
        }
    }
}

/**
 * [category] is always one of the user's own Categories or null. [unmappedCanonical] is set when
 * the keyword dictionary recognised the merchant but that key has no mapping yet, so Settings can
 * offer to map it. A Transfer has no Category: [transferAccountId] is its other Account instead.
 */
data class Categorization(
    val merchantNorm: String,
    val type: String,
    val category: String?,
    val confidence: Double,
    val decidedBy: String,
    val why: String,
    val unmappedCanonical: String? = null,
    val transferAccountId: Long? = null
) {
    val band: ConfidenceBand get() = ConfidenceBand.of(confidence, category != null || transferAccountId != null)
}

/**
 * The categorizer, all on the phone: an exact merchant rule (R1, the only step that reaches High,
 * ADR-0010), then the [HistoryPredictor]'s history match (R2) and Naive Bayes, both at most Check,
 * then the keyword dictionary (R7, Expenses only), else "needs category" (R11).
 */
class CategorizationPipeline(
    private val keywords: KeywordDictionary,
    private val lookupRule: suspend (MerchantRuleKey) -> MerchantRule?
) {

    /** [at] is when the payment happened, which the Naive Bayes step reads as time of day and weekday. */
    suspend fun categorize(
        parsed: ParsedTxn,
        categoryMapping: Map<String, String>,
        expenseCategories: List<String>,
        predictor: HistoryPredictor = HistoryPredictor.EMPTY,
        at: LocalDateTime = LocalDateTime.now()
    ): Categorization {
        val merchantNorm = MerchantNormalizer.normalize(parsed.merchantRaw).norm
        val type = if (parsed.direction == Direction.CREDIT) TransactionType.INCOME else TransactionType.EXPENSE

        var rename: String? = null
        if (merchantNorm.isNotBlank()) {
            val direction = if (parsed.direction == Direction.CREDIT) "CREDIT" else "DEBIT"
            val rule = lookupRule(MerchantRuleKey(merchantNorm, direction))
            if (rule != null && rule.origin != MerchantRuleOrigin.CANDIDATE) {
                if (rule.type != TransactionType.TRANSFER) {
                    return Categorization(merchantNorm, rule.type, rule.category, 0.98, "R1", "Saved rule for this merchant")
                }
                if (rule.toAccountId != null) {
                    return Categorization(
                        merchantNorm, TransactionType.TRANSFER, null, 0.98, "R1", "Saved Transfer rule for this merchant",
                        transferAccountId = rule.toAccountId
                    )
                }
            }
            rename = rule?.description
        }

        predictor.categorize(CaptureFacts(merchantNorm, type, parsed.amount, at), rename)?.let { return it }

        if (type == TransactionType.INCOME) {
            return Categorization(merchantNorm, type, null, 0.0, "R11", "No saved rule for this payer")
        }

        val hit = keywords.match(merchantNorm, parsed.merchantRaw)
            ?: return Categorization(merchantNorm, type, null, 0.0, "R11", "Nothing recognised")

        val category = CategoryResolver.resolve(hit.canonical, categoryMapping, expenseCategories)
        return if (category != null) {
            Categorization(merchantNorm, type, category, 0.75, "R7", "Keyword \"${hit.keyword}\"")
        } else {
            Categorization(
                merchantNorm, type, null, 0.0, "R11",
                "Keyword \"${hit.keyword}\" matched, but ${hit.canonical} isn't mapped to a category yet",
                unmappedCanonical = hit.canonical
            )
        }
    }
}
