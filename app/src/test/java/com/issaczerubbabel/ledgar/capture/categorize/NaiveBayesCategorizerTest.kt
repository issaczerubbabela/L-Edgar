package com.issaczerubbabel.ledgar.capture.categorize

import com.issaczerubbabel.ledgar.capture.categorize.PredictorFixtures.MONDAY
import com.issaczerubbabel.ledgar.capture.categorize.PredictorFixtures.facts
import com.issaczerubbabel.ledgar.capture.categorize.PredictorFixtures.past
import com.issaczerubbabel.ledgar.util.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import java.time.LocalDate
import org.junit.Test

class NaiveBayesCategorizerTest {

    private val categories = listOf("Food", "Snacks", "Groceries", "Transportation", "Fuel", "Salary")

    /**
     * Five weeks of one user's spends: confirmed captures (merchant and time known) mixed with
     * hand-typed Transactions (Description and date only), the way `expense_records` looks.
     */
    private val history: List<PastTransaction> = buildList {
        repeat(5) { week ->
            val monday = MONDAY.minusWeeks(week + 1L)
            fun day(offset: Long): LocalDate = monday.plusDays(offset)
            // Weekday lunches, captured and typed.
            add(past("Food", "Lunch", 180.0 + week * 10, merchant = "MEGHANA FOODS", time = "13:05", date = day(0)))
            add(past("Food", "Lunch", 220.0, merchant = "EMPIRE RESTAURANT", time = "13:30", date = day(2)))
            add(past("Food", "Lunch at Meghana Foods", 200.0, date = day(3)))
            // Evening tea.
            add(past("Snacks", "Tea", 30.0, merchant = "CHAI POINT", time = "16:30", date = day(1)))
            add(past("Snacks", "Tea and biscuits", 45.0, merchant = "CHAI POINT", time = "16:45", date = day(3)))
            add(past("Snacks", "Chai", 25.0, date = day(4)))
            // Weekend groceries.
            add(past("Groceries", "Groceries", 1400.0 + week * 100, merchant = "BIGBASKET", time = "10:15", date = day(5)))
            add(past("Groceries", "Ravi Stores", 650.0, date = day(6)))
            // Rides to and from work.
            add(past("Transportation", "Uber to office", 240.0, merchant = "UBER", time = "09:10", date = day(1)))
            add(past("Transportation", "Uber home", 260.0, merchant = "UBER", time = "19:20", date = day(2)))
            add(past("Transportation", "Rapido", 90.0, merchant = "RAPIDO", time = "18:50", date = day(4)))
            // The hard part: one app or cafe for two Categories, told apart only by amount and time.
            if (week < 3) add(past("Food", "Dinner", 380.0, merchant = "SWIGGY", time = "20:40", date = day(3)))
            if (week < 2) add(past("Groceries", "Instamart", 650.0, merchant = "SWIGGY INSTAMART", time = "11:00", date = day(5)))
            if (week % 2 == 0) add(past("Snacks", "Coffee", 160.0, merchant = "CAFE COFFEE DAY", time = "16:10", date = day(2)))
            if (week % 2 == 1) add(past("Food", "Lunch", 420.0, merchant = "CAFE COFFEE DAY", time = "13:20", date = day(4)))
        }
        // One-offs that nothing else resembles.
        add(past("Groceries", "Fruit", 240.0, merchant = "NILGIRIS", time = "18:00", date = MONDAY.minusDays(3)))
        add(past("Food", "Team dinner", 1800.0, merchant = "TOIT", time = "21:30", date = MONDAY.minusDays(12)))
        add(past("Fuel", "Petrol", 800.0, merchant = "INDIAN OIL", time = "08:30", date = MONDAY.minusDays(10)))
        add(past("Fuel", "Petrol", 900.0, merchant = "INDIAN OIL", time = "08:40", date = MONDAY.minusDays(24)))
        add(past("Salary", "Salary", 85000.0, merchant = "ACME", date = MONDAY.minusDays(23), type = TransactionType.INCOME))
    }

    private val model = NaiveBayesCategorizer.train(history)

    @Test
    fun aKnownMerchantWordWithTheUsualAmountAndTimeIsGuessed() {
        val guess = model.predict(facts("BIGBASKET", 1500.0, "10:30", MONDAY.plusDays(5)), categories)!!

        assertEquals("Groceries", guess.category)
        assertTrue(guess.probability >= NaiveBayesCategorizer.THRESHOLD)
    }

    @Test
    fun handTypedDescriptionsTeachMerchantWords() {
        // "Meghana" is only known from Descriptions and one merchant; a new branch still reads as Food.
        val guess = model.predict(facts("MEGHANA BIRYANI JAYANAGAR", 210.0, "13:15"), categories)!!

        assertEquals("Food", guess.category)
    }

    @Test
    fun itNeverYieldsHighAndExplainsItself() {
        val result = model.categorize(facts("UBER", 250.0, "09:00", MONDAY.plusDays(1)), categories)!!

        assertEquals("Transportation", result.category)
        assertEquals("NB", result.decidedBy)
        assertTrue(result.confidence <= NaiveBayesCategorizer.CAP)
        assertEquals(ConfidenceBand.CHECK, result.band)
        assertEquals("Guessed from similar spends", result.why)
    }

    @Test
    fun aMerchantWithNoKnownWordIsNotGuessedFromAmountAndTimeAlone() {
        assertNull(model.predict(facts("ZORBA TRADERS", 30.0, "16:30"), categories))
    }

    @Test
    fun anAmbiguousGuessUnderTheThresholdSaysNothing() {
        val mixed = List(6) { past("Food", "Cafe", 120.0, merchant = "CAFE NOIR", time = "15:00") } +
            List(6) { past("Snacks", "Cafe", 120.0, merchant = "CAFE NOIR", time = "15:00") }

        assertNull(NaiveBayesCategorizer.train(mixed).predict(facts("CAFE NOIR", 120.0, "15:00"), categories))
    }

    @Test
    fun tooLittleHistoryOrOnlyOneCategorySaysNothing() {
        val few = List(NaiveBayesCategorizer.MIN_EXAMPLES - 1) { i -> past(if (i % 2 == 0) "Food" else "Snacks", merchant = "M") }
        assertNull(NaiveBayesCategorizer.train(few).predict(facts("M"), categories))

        val one = List(20) { past("Food", merchant = "M") }
        assertNull(NaiveBayesCategorizer.train(one).predict(facts("M"), categories))
    }

    @Test
    fun onlyTheUsersCurrentCategoriesOfTheSameTypeAreGuessed() {
        val guess = model.predict(facts("BIGBASKET", 1500.0, "10:30", MONDAY.plusDays(5)), categories - "Groceries")

        assertTrue(guess == null || guess.category != "Groceries")
        assertNull(model.predict(facts("ACME", 85000.0, type = TransactionType.INCOME), categories))
    }

    @Test
    fun amountsAreBucketedOnADoublingScaleAndTimeInThreeHourSlots() {
        val lunch = NaiveBayesCategorizer.contextFeatures(80.0, 13 * 60, MONDAY)
        val tea = NaiveBayesCategorizer.contextFeatures(30.0, 16 * 60 + 30, MONDAY)
        val dinner = NaiveBayesCategorizer.contextFeatures(90.0, null, MONDAY.plusDays(6))

        assertEquals(listOf("amt:6", "tod:4", "day:weekday"), lunch)
        assertEquals(listOf("amt:4", "tod:5", "day:weekday"), tea)
        assertEquals(listOf("amt:6", "day:weekend"), dinner)
    }

    /**
     * Tunes [NaiveBayesCategorizer.THRESHOLD] on the fixture: leave each captured row out, train on
     * the rest, and guess it as if it were a new capture. A wrong guess costs a tap to fix and a
     * missing one a tap to pick, but a wrong one is also easy to confirm by mistake, so the bar is
     * set where answers stay right while most rows still get one. The sweep is printed to the test
     * report for retuning on other data. On this fixture 0.4 to 0.6 all give 96% coverage and 96%
     * accuracy (the miss is a lone Café Coffee Day lunch among coffees, which R2 would also call
     * Snacks), and 0.7 drops coverage to 91% for no gain, so 0.6 stays.
     */
    @Test
    fun theThresholdKeepsLeaveOneOutGuessesRightOnTheFixture() {
        val captured = history.filter { it.merchantNorm != null && it.type == TransactionType.EXPENSE }
        val guesses = captured.map { row ->
            val at = row.date.atStartOfDay().plusMinutes((row.minuteOfDay ?: 720).toLong())
            row to NaiveBayesCategorizer.train(history - row).best(CaptureFacts(row.merchantNorm!!, row.type, row.amount, at), categories)
        }
        fun scoreAt(threshold: Double): Pair<Double, Double> {
            val answered = guesses.filter { (_, guess) -> guess != null && guess.probability >= threshold }
            val right = answered.count { (row, guess) -> guess!!.category == row.category }
            return answered.size.toDouble() / captured.size to right.toDouble() / answered.size.coerceAtLeast(1)
        }
        listOf(0.4, 0.5, 0.6, 0.7, 0.8).forEach { t ->
            val (coverage, accuracy) = scoreAt(t)
            println("Naive Bayes leave-one-out at $t: coverage %.2f, accuracy %.2f".format(coverage, accuracy))
        }

        val (coverage, accuracy) = scoreAt(NaiveBayesCategorizer.THRESHOLD)
        assertTrue("coverage $coverage", coverage >= 0.8)
        assertTrue("accuracy $accuracy", accuracy >= 0.95)
    }
}
