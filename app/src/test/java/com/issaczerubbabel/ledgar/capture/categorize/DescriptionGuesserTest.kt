package com.issaczerubbabel.ledgar.capture.categorize

import com.issaczerubbabel.ledgar.capture.categorize.PredictorFixtures.MONDAY
import com.issaczerubbabel.ledgar.capture.categorize.PredictorFixtures.facts
import com.issaczerubbabel.ledgar.capture.categorize.PredictorFixtures.past
import com.issaczerubbabel.ledgar.util.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DescriptionGuesserTest {

    /** Lunch ₹80 around 1pm and Tea ₹30 around 4:30pm at the same canteen, over a few weeks. */
    private val canteen = List(4) { i ->
        listOf(
            past("Food", "Lunch", 80.0, merchant = "OFFICE CANTEEN", time = "13:0$i", date = MONDAY.minusDays(i + 1L)),
            past("Snacks", "Tea", 30.0, merchant = "OFFICE CANTEEN", time = "16:3$i", date = MONDAY.minusDays(i + 1L))
        )
    }.flatten()

    @Test
    fun amountAndTimeDecideBetweenTheMerchantsUsualDescriptions() {
        val lunch = DescriptionGuesser.guess(facts("OFFICE CANTEEN", 80.0, "12:50"), canteen)
        assertEquals("Lunch", lunch.title)
        assertEquals(listOf("Tea"), lunch.alternatives)

        val tea = DescriptionGuesser.guess(facts("OFFICE CANTEEN", 30.0, "16:30"), canteen)
        assertEquals("Tea", tea.title)
        assertEquals(listOf("Lunch"), tea.alternatives)
    }

    @Test
    fun aMerchantWithDifferentDescriptionsIsDescribedPerPayment() {
        assertTrue(DescriptionGuesser.guess(facts("OFFICE CANTEEN", 80.0, "12:50"), canteen).perPayment)

        val oneName = List(3) { past("Groceries", "Instamart", 300.0, merchant = "SWIGGY INSTAMART", time = "20:00") }
        assertFalse(DescriptionGuesser.guess(facts("SWIGGY INSTAMART", 300.0, "20:00"), oneName).perPayment)
    }

    @Test
    fun theMerchantsOwnHistoryOutweighsEverythingElse() {
        // Many close "Snacks" spends elsewhere lose to the merchant's one Lunch.
        val elsewhere = List(10) { past("Snacks", "Samosa", 80.0, merchant = "BAKERY", time = "12:50") }
        val history = elsewhere + past("Food", "Lunch", 150.0, merchant = "OFFICE CANTEEN", time = "19:00")

        val guess = DescriptionGuesser.guess(facts("OFFICE CANTEEN", 80.0, "12:50"), history)

        assertEquals("Lunch", guess.title)
        assertEquals(emptyList<String>(), guess.alternatives)
    }

    @Test
    fun atMostTwoAlternatives() {
        val history = listOf("Lunch", "Tea", "Coffee", "Snack").map { past("Food", it, 50.0, merchant = "M", time = "12:00") }

        assertEquals(DescriptionGuesser.MAX_ALTERNATIVES, DescriptionGuesser.guess(facts("M", 50.0), history).alternatives.size)
    }

    @Test
    fun withoutMerchantHistoryOnlyAgreementAmongCloseSpendsIsGuessed() {
        val oneOff = listOf(past("Food", "Lunch", 80.0, merchant = "ELSEWHERE", time = "13:00"))
        assertNull(DescriptionGuesser.guess(facts("NEW PLACE", 80.0, "12:50"), oneOff).title)

        val habit = List(4) { i -> past("Food", "Lunch", 80.0, merchant = "PLACE $i", time = "13:00", date = MONDAY.minusDays(i + 1L)) }
        assertEquals("Lunch", DescriptionGuesser.guess(facts("NEW PLACE", 80.0, "12:50"), habit).title)
    }

    @Test
    fun theBanksOwnTextAndPlaceholdersAreNeverGuessed() {
        val history = listOf(
            past("Food", "SWIGGY", 300.0, merchant = "SWIGGY", time = "20:00"),
            past("Food", "Payment", 300.0, merchant = "SWIGGY", time = "20:00")
        )

        assertEquals(DescriptionGuess.NONE, DescriptionGuesser.guess(facts("SWIGGY", 300.0, "20:00"), history, ignore = listOf("Swiggy")))
    }

    @Test
    fun onlyTheSameTypeIsUsedAndTheNewestSpellingWins() {
        val history = listOf(
            past("Salary", "Salary", 50000.0, merchant = "ACME", type = TransactionType.INCOME),
            past("Food", "lunch", 80.0, merchant = "M", time = "13:00", date = MONDAY.minusDays(5)),
            past("Food", "Lunch ", 80.0, merchant = "M", time = "13:00", date = MONDAY.minusDays(1))
        )

        assertEquals(DescriptionGuess.NONE, DescriptionGuesser.guess(facts("ACME", 50000.0), history))
        assertEquals("Lunch", DescriptionGuesser.guess(facts("M", 80.0, "13:00"), history).title)
    }
}
