package com.issaczerubbabel.ledgar.capture.categorize

import java.time.LocalDate
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln

/**
 * The on-phone guesser that runs after R2: a multinomial Naive Bayes over merchant words, a
 * log-scaled amount bucket, a time-of-day bucket and weekday/weekend, trained on every Expense and
 * Income the user has, hand-typed ones included. It runs on the phone with no network and keeps
 * nothing: [train] rebuilds the counts in memory from the rows it is given.
 *
 * It only answers when its top Category clears [THRESHOLD], when the capture shares at least one
 * merchant word with the training rows (so an unknown merchant isn't guessed from the amount and
 * time alone), and when there were at least [MIN_EXAMPLES] rows and two Categories to tell apart.
 * Its confidence is capped at [CAP], so it is at most Check (ADR-0010).
 */
class NaiveBayesCategorizer private constructor(private val models: Map<String, Model>) {

    data class Guess(val category: String, val probability: Double)

    private class Model(
        val examples: Int,
        val classCounts: Map<String, Int>,
        val featureCounts: Map<String, Map<String, Int>>,
        val featureTotals: Map<String, Int>,
        val vocabulary: Set<String>
    )

    /** The likeliest of [validCategories] for [facts], or null when the model can't tell. */
    fun predict(facts: CaptureFacts, validCategories: Collection<String>): Guess? =
        best(facts, validCategories)?.takeIf { it.probability >= THRESHOLD }

    /** The top Category and its probability before [THRESHOLD] is applied, for tuning it. */
    internal fun best(facts: CaptureFacts, validCategories: Collection<String>): Guess? {
        val model = models[facts.type] ?: return null
        if (model.examples < MIN_EXAMPLES) return null

        val tokens = tokens(facts.merchantNorm)
        if (tokens.none { it in model.vocabulary }) return null

        val classes = model.classCounts.keys.mapNotNull { trained ->
            validCategories.firstOrNull { it.equals(trained, ignoreCase = true) }?.let { trained to it }
        }
        if (classes.size < 2) return null

        val features = tokens + contextFeatures(facts.amount, facts.minuteOfDay, facts.at.toLocalDate())
        val vocabularySize = model.vocabulary.size.toDouble()
        val logScores = classes.map { (trained, shown) ->
            val counts = model.featureCounts[trained].orEmpty()
            val total = model.featureTotals[trained] ?: 0
            var score = ln(model.classCounts.getValue(trained).toDouble() / model.examples)
            features.forEach { f -> score += weight(f) * ln(((counts[f] ?: 0) + 1.0) / (total + vocabularySize)) }
            shown to score
        }
        val best = logScores.maxOf { it.second }
        val normaliser = logScores.sumOf { exp(it.second - best) }
        val (category, score) = logScores.maxBy { it.second }
        return Guess(category, exp(score - best) / normaliser)
    }

    /** R-step form of [predict], for the pipeline: Check at most, with its why text. */
    fun categorize(facts: CaptureFacts, validCategories: Collection<String>): Categorization? {
        val guess = predict(facts, validCategories) ?: return null
        return Categorization(
            facts.merchantNorm, facts.type, guess.category, minOf(guess.probability, CAP), RULE, "Guessed from similar spends"
        )
    }

    companion object {
        const val RULE = "NB"
        const val THRESHOLD = 0.6
        const val CAP = 0.89
        const val MIN_EXAMPLES = 10

        /**
         * A merchant word counts this many times over the amount, time and day features, so the
         * merchant leads and the context only decides between Categories it shares.
         */
        const val WORD_WEIGHT = 3

        private fun weight(feature: String): Int = if (feature.startsWith("w:")) WORD_WEIGHT else 1

        private val STOP_WORDS = setOf("at", "to", "for", "and", "of", "in", "on", "from", "with", "by", "upi", "payment")

        fun train(history: List<PastTransaction>): NaiveBayesCategorizer {
            val models = history
                .filter { it.category.isNotBlank() }
                .groupBy { it.type }
                .mapValues { (_, rows) ->
                    val classCounts = rows.groupingBy { it.category.trim() }.eachCount()
                    val featureCounts = mutableMapOf<String, MutableMap<String, Int>>()
                    val vocabulary = mutableSetOf<String>()
                    rows.forEach { row ->
                        val features = tokens(row.merchantNorm.orEmpty() + " " + row.description) +
                            contextFeatures(row.amount, row.minuteOfDay, row.date)
                        val counts = featureCounts.getOrPut(row.category.trim()) { mutableMapOf() }
                        features.forEach { f ->
                            counts[f] = (counts[f] ?: 0) + weight(f)
                            vocabulary += f
                        }
                    }
                    Model(
                        examples = rows.size,
                        classCounts = classCounts,
                        featureCounts = featureCounts,
                        featureTotals = featureCounts.mapValues { it.value.values.sum() },
                        vocabulary = vocabulary
                    )
                }
            return NaiveBayesCategorizer(models)
        }

        /** Merchant or Description words, cleaned the way merchant names are. */
        internal fun tokens(text: String): List<String> {
            val norm = MerchantNormalizer.normalize(text).norm
            if (norm.startsWith("P2P:")) return listOf("w:${norm.lowercase()}")
            return norm.lowercase().split(' ')
                .filter { it.length > 1 && it !in STOP_WORDS }
                .distinct()
                .map { "w:$it" }
        }

        /**
         * The amount's bucket on a doubling scale (₹30 and ₹80 differ, ₹80 and ₹90 don't), a
         * three-hour slot of the day when the time is known, and weekday or weekend.
         */
        internal fun contextFeatures(amount: Double, minuteOfDay: Int?, date: LocalDate): List<String> = listOfNotNull(
            "amt:${floor(ln(amount.coerceAtLeast(1.0)) / ln(2.0)).toInt()}",
            minuteOfDay?.let { "tod:${it / 180}" },
            if (date.isWeekend()) "day:weekend" else "day:weekday"
        )
    }
}
