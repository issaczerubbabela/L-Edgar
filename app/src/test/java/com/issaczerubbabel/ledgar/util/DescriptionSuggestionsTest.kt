package com.issaczerubbabel.ledgar.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DescriptionSuggestionsTest {

    private val today = LocalDate.of(2026, 10, 13)
    private var nextId = 1L

    private fun use(description: String, daysAgo: Long = 1, category: String = "Food", accountId: Long? = 1) =
        DescriptionUse(description, category, accountId, today.minusDays(daysAgo).toString(), nextId++)

    private fun suggest(uses: List<DescriptionUse>, query: String = "", categories: Set<String> = setOf("Food", "Groceries", "Fuel")) =
        DescriptionSuggestions.suggest(uses, query, categories, validAccountIds = setOf(1, 2), today = today)

    @Test
    fun anEmptyQueryShowsTheMostUsedRecentDescriptions() {
        val uses = List(3) { use("Swiggy") } + use("Petrol") + use("Old shop", daysAgo = 200)

        assertEquals(listOf("Swiggy", "Petrol"), suggest(uses).map { it.description })
    }

    @Test
    fun prefixMatchesComeBeforeMatchesInsideTheText() {
        val uses = List(4) { use("Big Swiggy order") } + use("Swiggy")

        assertEquals(listOf("Swiggy", "Big Swiggy order"), suggest(uses, query = "sw").map { it.description })
    }

    @Test
    fun spellingsDifferingOnlyInCaseAndSpacesAreOneSuggestionWithTheLatestSpelling() {
        val uses = listOf(use("swiggy ", daysAgo = 5), use("SWIGGY", daysAgo = 3), use("Swiggy", daysAgo = 1))

        val suggestions = suggest(uses, query = "  SWI ")

        assertEquals(listOf("Swiggy"), suggestions.map { it.description })
    }

    @Test
    fun theCategoryIsWhatTwoOfTheLastThreeAgreeOn() {
        val uses = listOf(
            use("Swiggy", daysAgo = 3, category = "Food"),
            use("Swiggy", daysAgo = 2, category = "Food"),
            use("Swiggy", daysAgo = 1, category = "Groceries")
        )

        assertEquals("Food", suggest(uses).single().category)
    }

    @Test
    fun withNoAgreementTheMostRecentCategoryWins() {
        val uses = listOf(
            use("Swiggy", daysAgo = 3, category = "Food"),
            use("Swiggy", daysAgo = 2, category = "Fuel"),
            use("Swiggy", daysAgo = 1, category = "Groceries")
        )

        assertEquals("Groceries", suggest(uses).single().category)
    }

    @Test
    fun aDeletedCategoryOrArchivedAccountIsLeftOut() {
        val suggestion = suggest(listOf(use("Swiggy", category = "Takeaway", accountId = 9))).single()

        assertNull(suggestion.category)
        assertNull(suggestion.accountId)
    }

    @Test
    fun atMostFiveAreShown() {
        val uses = (1..8).map { use("Shop $it") }

        assertEquals(5, suggest(uses, query = "shop").size)
    }

    @Test
    fun nothingMatchesNothingIsShown() {
        assertTrue(suggest(listOf(use("Swiggy")), query = "petrol").isEmpty())
        assertTrue(suggest(emptyList()).isEmpty())
    }
}
