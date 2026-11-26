package com.issaczerubbabel.ledgar.capture

import com.issaczerubbabel.ledgar.capture.categorize.Categorization
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CaptureTraceTest {

    @Test
    fun roundTripsEveryGuess() {
        val trace = CaptureTrace("R2", "You picked Food for this 4 of 5 times", null, "Lunch", listOf("Tea", "Snack"), 7L)

        assertEquals(trace, CaptureTrace.parse(trace.toJson()))
        assertEquals(listOf("Lunch", "Tea", "Snack"), trace.descriptionGuesses)
    }

    @Test
    fun readsTracesWrittenBeforeGuessesExisted() {
        val old = CaptureTrace.parse("""{"rule":"R7","why":"Keyword \"swiggy\"","unmappedCanonical":null}""")

        assertEquals("R7", old.rule)
        assertEquals("Keyword \"swiggy\"", old.why)
        assertNull(old.descriptionGuess)
        assertEquals(emptyList<String>(), old.descriptionAlternatives)
        assertNull(old.guessedAccountId)
    }

    @Test
    fun aMissingOrBrokenTraceIsEmpty() {
        assertEquals(CaptureTrace(), CaptureTrace.parse(null))
        assertEquals(CaptureTrace(), CaptureTrace.parse("not json"))
        assertEquals(CaptureTrace(), CaptureTrace.parse("[1,2]"))
    }

    private val capture = CapturedTransaction(
        sources = "NOTIF", sender = "HDFC Bank", rawText = "", rawHash = "h", capturedAt = 0, txnTime = 0,
        amount = 80.0, direction = "DEBIT", channel = "UPI", merchantNorm = "CANTEEN",
        suggestedType = "Expense", suggestedCategory = "Food", confidence = 0.8, decidedBy = "R2",
        traceJson = CaptureTrace("R2", "You picked Food for this 4 of 5 times", descriptionGuess = "Lunch").toJson()
    )

    @Test
    fun aNewPassReplacesEveryGuess() {
        val result = Categorization("CANTEEN", "Expense", "Snacks", 0.89, "R2", "You picked Snacks for this 3 of 3 times")
        val trace = CaptureTrace.of(result, com.issaczerubbabel.ledgar.capture.categorize.DescriptionGuess("Tea", listOf("Lunch")), null)

        val updated = capture.withGuesses(result, trace)

        assertEquals("Snacks", updated.suggestedCategory)
        assertEquals(0.89, updated.confidence, 0.0)
        assertEquals(trace, CaptureTrace.parse(updated.traceJson))
    }

    @Test
    fun aPassWithNoCategoryKeepsTheSuggestionButTakesTheNewDescription() {
        val result = Categorization("CANTEEN", "Expense", null, 0.0, "R11", "Nothing recognised")
        val trace = CaptureTrace.of(result, com.issaczerubbabel.ledgar.capture.categorize.DescriptionGuess("Tea"), null)

        val updated = capture.withGuesses(result, trace)

        assertEquals("Food", updated.suggestedCategory)
        assertEquals("R2", updated.decidedBy)
        val kept = CaptureTrace.parse(updated.traceJson)
        assertEquals("You picked Food for this 4 of 5 times", kept.why)
        assertEquals("Tea", kept.descriptionGuess)
    }
}
