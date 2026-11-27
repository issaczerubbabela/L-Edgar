package com.issaczerubbabel.ledgar.capture.categorize

import com.issaczerubbabel.ledgar.capture.categorize.PredictorFixtures.facts
import com.issaczerubbabel.ledgar.capture.categorize.PredictorFixtures.past
import com.issaczerubbabel.ledgar.util.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HistoryMatchTest {

    private val categories = listOf("Food", "Snacks", "Groceries")

    @Test
    fun fourOfFiveTheSameSuggestsThatCategoryInCheckWithTheCount() {
        val history = List(4) { past("Food", merchant = "CHAI POINT") } + past("Snacks", merchant = "CHAI POINT")

        val result = HistoryMatch.match(facts("CHAI POINT"), null, history, categories)!!

        assertEquals("Food", result.category)
        assertEquals("R2", result.decidedBy)
        assertEquals(0.8, result.confidence, 1e-9)
        assertEquals(ConfidenceBand.CHECK, result.band)
        assertEquals("You picked Food for this 4 of 5 times", result.why)
    }

    @Test
    fun alwaysTheSameIsCappedBelowHigh() {
        val history = List(20) { past("Food", merchant = "CHAI POINT") }

        val result = HistoryMatch.match(facts("CHAI POINT"), null, history, categories)!!

        assertEquals(HistoryMatch.CAP, result.confidence, 1e-9)
        assertEquals(ConfidenceBand.CHECK, result.band)
    }

    @Test
    fun aSingleConfirmationSaysOnce() {
        val result = HistoryMatch.match(facts("CHAI POINT"), null, listOf(past("Food", merchant = "CHAI POINT")), categories)!!

        assertEquals("You picked Food for this once", result.why)
    }

    @Test
    fun inconsistentPicksSayNothingSoTheNextStepRuns() {
        val history = listOf(past("Food", merchant = "M"), past("Snacks", merchant = "M"), past("Groceries", merchant = "M"))

        assertNull(HistoryMatch.match(facts("M"), null, history, categories))
    }

    @Test
    fun handTypedTransactionsCountWhenTheyMatchTheRenameOrTheMerchant() {
        val history = listOf(
            past("Groceries", description = "Corner shop"),
            past("Groceries", description = "corner shop "),
            past("Groceries", description = "Ravi Stores")
        )

        val renamed = HistoryMatch.match(facts("SRI RAVI STORES"), "Corner Shop", history, categories)!!
        assertEquals("Groceries", renamed.category)
        assertEquals("You picked Groceries for this 2 of 2 times", renamed.why)

        val byName = HistoryMatch.match(facts("RAVI STORES"), null, history, categories)!!
        assertEquals("You picked Groceries for this once", byName.why)
    }

    @Test
    fun otherTypesAndOtherMerchantsDontCount() {
        val history = listOf(
            past("Salary", merchant = "ACME", type = TransactionType.INCOME),
            past("Food", merchant = "SOMEONE ELSE")
        )

        assertNull(HistoryMatch.match(facts("ACME"), null, history, categories))
    }

    @Test
    fun aCategoryTheUserNoLongerHasIsNeverSuggested() {
        val history = List(3) { past("Eating out", merchant = "CHAI POINT") }

        assertNull(HistoryMatch.match(facts("CHAI POINT"), null, history, categories))
    }

    @Test
    fun aDeletedCategoryStillCountsAgainstConsistency() {
        // 2 Food of 4 picks is 0.5, under the bar, even though the other two Categories are gone.
        val history = List(2) { past("Food", merchant = "M") } + List(2) { past("Eating out", merchant = "M") }

        assertNull(HistoryMatch.match(facts("M"), null, history, categories))
    }
}
