package com.issaczerubbabel.ledgar.capture.categorize

/**
 * R2: the Category the user picked most often for this merchant. A past Transaction counts when it
 * was confirmed from a capture of the same merchant, or when its Description is the merchant's
 * saved rename or reads as the merchant itself (a hand-typed "Swiggy" for `SWIGGY`).
 *
 * Confidence is how consistent the picks are (4 of 5 the same is 0.8), capped at [CAP] so it never
 * reaches High (ADR-0010). Below [MIN_CONFIDENCE] it says nothing and the next step runs.
 */
object HistoryMatch {

    const val RULE = "R2"
    const val CAP = 0.89
    const val MIN_CONFIDENCE = 0.6

    fun match(
        facts: CaptureFacts,
        rename: String?,
        history: List<PastTransaction>,
        validCategories: Collection<String>
    ): Categorization? {
        val merchant = facts.merchantNorm
        if (merchant.isBlank()) return null
        val renamed = rename?.trim()?.takeIf { it.isNotEmpty() }

        val matches = history.filter { past ->
            past.type == facts.type && (
                past.merchantNorm == merchant ||
                    (renamed != null && past.description.trim().equals(renamed, ignoreCase = true)) ||
                    (past.merchantNorm == null && MerchantNormalizer.normalize(past.description).norm == merchant)
                )
        }
        if (matches.isEmpty()) return null

        val (category, count) = matches
            .mapNotNull { past -> validCategories.firstOrNull { it.equals(past.category.trim(), ignoreCase = true) } }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }
            ?.toPair() ?: return null

        val consistency = count.toDouble() / matches.size
        if (consistency < MIN_CONFIDENCE) return null

        val why = if (matches.size == 1) {
            "You picked $category for this once"
        } else {
            "You picked $category for this $count of ${matches.size} times"
        }
        return Categorization(merchant, facts.type, category, minOf(consistency, CAP), RULE, why)
    }
}
