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
 * ADR-0010); for a credit whose alert names its kind (a cashback,
 * [com.issaczerubbabel.ledgar.capture.CaptureKinds]), that kind's mapping (R7) or "needs category";
 * else the [HistoryPredictor]'s history match (R2) and Naive Bayes, both at most Check, then the
 * keyword dictionary (R7, Expenses only), else "needs category" (R11).
 *
 * A rule only counts while its Category is still one of the user's own (ADR-0005). Rules outlive a
 * Category deleted in the app or renamed in the Sheet, and such a stale rule would file captures
 * under the old name at High, so the pipeline carries on as if there were no rule, keeping only
 * the rule's rename, and says why.
 */
class CategorizationPipeline(
    private val keywords: KeywordDictionary,
    private val lookupRule: suspend (MerchantRuleKey) -> MerchantRule?
) {

    /**
     * [categoriesByType] holds the user's own Categories per type (Expense, Income): R1 and R7 only
     * ever return one of them, in the user's spelling. [at] is when the payment happened, which the
     * Naive Bayes step reads as time of day and weekday. [alsoKnownAs] is the bank's own merchant
     * text when another Alert source named the payee ([parsed]'s merchant) differently: a rule saved
     * under it still counts after the merge (ADR-0012), but the capture keeps the payee's name.
     */
    suspend fun categorize(
        parsed: ParsedTxn,
        categoryMapping: Map<String, String>,
        categoriesByType: Map<String, Collection<String>>,
        predictor: HistoryPredictor = HistoryPredictor.EMPTY,
        at: LocalDateTime = LocalDateTime.now(),
        alsoKnownAs: String? = null
    ): Categorization {
        val merchantNorm = MerchantNormalizer.normalize(parsed.merchantRaw).norm
        val type = if (parsed.direction == Direction.CREDIT) TransactionType.INCOME else TransactionType.EXPENSE
        val direction = if (parsed.direction == Direction.CREDIT) "CREDIT" else "DEBIT"
        // The best payee's rule first, then the bank's.
        val names = listOf(merchantNorm, MerchantNormalizer.normalize(alsoKnownAs).norm).filter { it.isNotBlank() }.distinct()

        var rename: String? = null
        var staleRule: String? = null
        for (name in names) {
            val rule = lookupRule(MerchantRuleKey(name, direction))
            if (rule != null && rule.origin != MerchantRuleOrigin.CANDIDATE) {
                if (rule.type != TransactionType.TRANSFER) {
                    val category = categoriesByType[rule.type].orEmpty().firstOrNull { it.equals(rule.category.trim(), ignoreCase = true) }
                    if (category != null) {
                        return Categorization(merchantNorm, rule.type, category, 0.98, "R1", "Saved rule for this merchant")
                    }
                    if (staleRule == null) staleRule = "Saved rule's Category ${rule.category} no longer exists"
                } else if (rule.toAccountId != null) {
                    return Categorization(
                        merchantNorm, TransactionType.TRANSFER, null, 0.98, "R1", "Saved Transfer rule for this merchant",
                        transferAccountId = rule.toAccountId
                    )
                }
            }
            if (rename == null) rename = rule?.description
        }

        /** A guess's own reason, after the note that the saved rule was passed over. */
        fun why(reason: String) = staleRule?.let { "$it · $reason" } ?: reason

        /** R7 for a [kind] mapped to one of the user's Categories of this type, else R11 naming the kind to map. */
        fun byKind(kind: String, reason: String): Categorization {
            val category = CategoryResolver.resolve(kind, categoryMapping, categoriesByType[type].orEmpty())
            return if (category != null) {
                Categorization(merchantNorm, type, category, 0.75, "R7", why(reason))
            } else {
                Categorization(
                    merchantNorm, type, null, 0.0, "R11", why("$reason matched, but $kind isn't mapped to a category yet"),
                    unmappedCanonical = kind
                )
            }
        }

        // The alert says what it is (a cashback) and the user mapped that once, so no history guess overrides it.
        if (type == TransactionType.INCOME && parsed.kind != null) return byKind(parsed.kind, "The alert says ${parsed.kind}")

        predictor.categorize(CaptureFacts(merchantNorm, type, parsed.amount, at), rename)?.let { return it.copy(why = why(it.why)) }

        // Credits get a Category from a rule, history or their own kind, never from a keyword.
        if (type == TransactionType.INCOME) {
            return Categorization(merchantNorm, type, null, 0.0, "R11", staleRule ?: "No saved rule for this payer")
        }

        val hit = keywords.match(merchantNorm, parsed.merchantRaw)
            ?: return Categorization(merchantNorm, type, null, 0.0, "R11", staleRule ?: "Nothing recognised")
        return byKind(hit.canonical, "Keyword \"${hit.keyword}\"")
    }
}
