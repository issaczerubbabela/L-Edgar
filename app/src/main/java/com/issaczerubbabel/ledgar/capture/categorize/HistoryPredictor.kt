package com.issaczerubbabel.ledgar.capture.categorize

import com.issaczerubbabel.ledgar.util.TransactionType

/**
 * The learned steps the pipeline runs between R1 and R7, over one snapshot of the user's
 * Transactions: R2 [HistoryMatch], then [NaiveBayesCategorizer]. Build one per ingest or re-guess
 * pass; the Naive Bayes counts are trained once, on first use, and reused for every capture.
 * [categoriesByType] holds the user's own Categories per type, so nothing is ever invented.
 *
 * Only Expenses and Income predict a Category; Transfers in [history] feed the Suggested
 * description only, since a Transfer has no Category.
 */
class HistoryPredictor(
    val history: List<PastTransaction>,
    private val categoriesByType: Map<String, Collection<String>>
) {
    private val categoryHistory by lazy {
        history.filter { it.type == TransactionType.EXPENSE || it.type == TransactionType.INCOME }
    }
    private val naiveBayes by lazy { NaiveBayesCategorizer.train(categoryHistory) }

    fun categorize(facts: CaptureFacts, rename: String?): Categorization? {
        if (facts.type != TransactionType.EXPENSE && facts.type != TransactionType.INCOME) return null
        val valid = categoriesByType[facts.type].orEmpty()
        if (valid.isEmpty()) return null
        return HistoryMatch.match(facts, rename, categoryHistory, valid) ?: naiveBayes.categorize(facts, valid)
    }

    fun describe(facts: CaptureFacts, ignore: Collection<String>): DescriptionGuess =
        DescriptionGuesser.guess(facts, history, ignore)

    companion object {
        val EMPTY = HistoryPredictor(emptyList(), emptyMap())
    }
}
