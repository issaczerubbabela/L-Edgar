package com.issaczerubbabel.ledgar.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.pow

/** One past Transaction's Description, with what it went with. A projection of `expense_records`. */
data class DescriptionUse(
    val description: String,
    val category: String,
    val accountId: Long?,
    /** yyyy-MM-dd */
    val date: String,
    val id: Long
)

/** A Description the user has used before, with the Category and Account it usually goes with. */
data class DescriptionSuggestion(
    /** The most recent spelling. */
    val description: String,
    /** Null when it no longer exists. */
    val category: String?,
    /** Null when the Account is gone or archived. */
    val accountId: Long?
)

/** What picking a suggestion changes: always the Description, and a Category or Account only where null isn't. */
data class SuggestionFill(val description: String, val category: String?, val accountId: Long?)

/**
 * Suggests Descriptions from the user's own Transactions as they type. Derived, never stored: there
 * is nothing to sync or back up. Kept free of Android classes so it runs as a plain JVM test.
 */
object DescriptionSuggestions {

    const val MAX_SUGGESTIONS = 5

    /** With nothing typed yet, only Descriptions used this recently are offered. */
    private const val RECENT_DAYS = 90L

    /** A use this many days old counts half as much as one from today. */
    private const val HALF_LIFE_DAYS = 30.0

    /**
     * Up to [MAX_SUGGESTIONS] Descriptions matching [query], ignoring case and extra spaces: those
     * starting with it first, then those containing it, each ranked by how often and how recently
     * they were used. The Category and Account are what two of the last three uses agree on, else
     * the latest, and are left out when no longer in [validCategories] or [validAccountIds].
     */
    fun suggest(
        uses: List<DescriptionUse>,
        query: String,
        validCategories: Set<String>,
        validAccountIds: Set<Long>,
        today: LocalDate
    ): List<DescriptionSuggestion> {
        val needle = normalize(query)
        val oldest = if (needle.isEmpty()) today.minusDays(RECENT_DAYS) else LocalDate.MIN

        return uses
            .filter { it.description.isNotBlank() }
            .mapNotNull { use -> runCatching { LocalDate.parse(use.date) }.getOrNull()?.let { use to it } }
            .filter { (_, date) -> !date.isBefore(oldest) }
            .groupBy { (use, _) -> normalize(use.description) }
            .filterKeys { it.contains(needle) }
            .map { (key, dated) ->
                val newestFirst = dated.sortedWith(compareByDescending<Pair<DescriptionUse, LocalDate>> { it.second }.thenByDescending { it.first.id })
                val score = dated.sumOf { (_, date) -> 0.5.pow(ChronoUnit.DAYS.between(date, today).coerceAtLeast(0) / HALF_LIFE_DAYS) }
                val latest = newestFirst.map { it.first }
                Ranked(
                    suggestion = DescriptionSuggestion(
                        description = latest.first().description.trim().replace(WHITESPACE, " "),
                        category = usual(latest.map { it.category })?.takeIf { it in validCategories },
                        accountId = usual(latest.map { it.accountId })?.takeIf { it in validAccountIds }
                    ),
                    isPrefix = key.startsWith(needle),
                    score = score
                )
            }
            .sortedWith(compareByDescending<Ranked> { it.isPrefix }.thenByDescending { it.score })
            .take(MAX_SUGGESTIONS)
            .map { it.suggestion }
    }

    /**
     * What picking [suggestion] fills: the Description, plus its Category and Account where the user
     * hasn't chosen one. Editing a Transaction never changes its Category or Account this way.
     */
    fun fill(suggestion: DescriptionSuggestion, categoryChosen: Boolean, accountChosen: Boolean, editing: Boolean) =
        SuggestionFill(
            description = suggestion.description,
            category = suggestion.category.takeUnless { categoryChosen || editing },
            accountId = suggestion.accountId.takeUnless { accountChosen || editing }
        )

    /** What two of the three latest agree on, else the latest. [newestFirst] is never empty. */
    private fun <T> usual(newestFirst: List<T>): T? {
        val lastThree = newestFirst.take(3)
        return lastThree.groupingBy { it }.eachCount().entries.firstOrNull { it.value >= 2 }?.key ?: lastThree.first()
    }

    private fun normalize(text: String) = text.trim().replace(WHITESPACE, " ").lowercase()

    private val WHITESPACE = Regex("\\s+")

    private class Ranked(val suggestion: DescriptionSuggestion, val isPrefix: Boolean, val score: Double)
}
