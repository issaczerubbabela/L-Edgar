package com.issaczerubbabel.ledgar.capture.categorize

import com.issaczerubbabel.ledgar.capture.parse.Channel
import com.issaczerubbabel.ledgar.capture.parse.Direction
import com.issaczerubbabel.ledgar.capture.parse.ParsedTxn
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleKey
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleOrigin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CategorizationPipelineTest {

    private val keywords = KeywordDictionary(
        mapOf(
            "food_delivery" to listOf("swiggy"),
            "groceries" to listOf("instamart"),
            "fuel" to listOf("fuels")
        )
    )
    private val mapping = mapOf("food_delivery" to "Food & Snacks", "groceries" to "Groceries")
    private val categories = listOf("Food & Snacks", "Groceries", "Transportation")

    private fun pipeline(rule: MerchantRule? = null) = CategorizationPipeline(keywords) { rule }

    private fun debit(merchant: String?) = ParsedTxn(
        amount = 100.0, direction = Direction.DEBIT, merchantRaw = merchant,
        accountHint = "1234", refNumber = null, channel = Channel.UPI
    )

    private fun rule(origin: String, category: String = "Transportation", type: String = "Expense") = MerchantRule(
        merchantNorm = "UBER", direction = "DEBIT", category = category, type = type, origin = origin, updatedAt = 0
    )

    @Test
    fun savedRuleWinsWithHighConfidence() = runBlocking {
        val result = pipeline(rule(MerchantRuleOrigin.LEARNED)).categorize(debit("Uber"), mapping, categories)

        assertEquals("Transportation", result.category)
        assertEquals("R1", result.decidedBy)
        assertEquals(ConfidenceBand.HIGH, result.band)
    }

    @Test
    fun aCandidateIsNotARule() = runBlocking {
        val result = pipeline(rule(MerchantRuleOrigin.CANDIDATE)).categorize(debit("Uber"), mapping, categories)

        assertNull(result.category)
    }

    @Test
    fun keywordUsesTheUsersMappedCategoryAndIsOnlyCheckConfidence() = runBlocking {
        val result = pipeline().categorize(debit("swiggy.instamart@icici"), mapping, categories)

        assertEquals("Groceries", result.category)
        assertEquals("R7", result.decidedBy)
        assertEquals(ConfidenceBand.CHECK, result.band)
    }

    @Test
    fun unmappedKeywordNeedsACategoryAndSaysWhichKeyToMap() = runBlocking {
        val result = pipeline().categorize(debit("Shri Ujagar Fuels"), mapping, categories)

        assertNull(result.category)
        assertEquals("fuel", result.unmappedCanonical)
        assertEquals(ConfidenceBand.LOW, result.band)
    }

    @Test
    fun mappingToACategoryTheUserNoLongerHasNeedsACategory() = runBlocking {
        val result = pipeline().categorize(debit("swiggy"), mapOf("food_delivery" to "Deleted Category"), categories)

        assertNull(result.category)
    }

    @Test
    fun unknownMerchantAndMissingMerchantNeedACategory() = runBlocking {
        assertNull(pipeline().categorize(debit("Mrs Jane Doe"), mapping, categories).category)
        assertNull(pipeline().categorize(debit(null), mapping, categories).category)
    }

    @Test
    fun creditsAreIncomeAndNeedARule() = runBlocking {
        val credit = debit("WFISPL").copy(direction = Direction.CREDIT)

        val result = pipeline().categorize(credit, mapping, categories)

        assertEquals("Income", result.type)
        assertNull(result.category)
    }

    @Test
    fun aLearnedTransferRuleSuggestsATransferToItsAccountAtHigh() = runBlocking {
        val rule = rule(MerchantRuleOrigin.LEARNED, category = "Transfer", type = "Transfer").copy(toAccountId = 7)

        val result = pipeline(rule).categorize(debit("Uber"), mapping, categories)

        assertEquals("Transfer", result.type)
        assertNull("a Transfer has no Category", result.category)
        assertEquals(7L, result.transferAccountId)
        assertEquals(ConfidenceBand.HIGH, result.band)
    }

    // The learned steps between R1 and R7 (ADR-0010).

    private val at = LocalDateTime.of(2026, 11, 23, 12, 50)

    private fun past(category: String, merchant: String?, description: String = category, type: String = "Expense") =
        PastTransaction(0, type, category, description, 100.0, LocalDate.of(2026, 11, 20), merchant, 13 * 60)

    private fun predictor(history: List<PastTransaction>) =
        HistoryPredictor(history, mapOf("Expense" to categories, "Income" to listOf("Salary", "Refund")))

    /** Twelve "uber" rides, Transportation every time but once, plus some other spends to train on. */
    private val rides = List(11) { past("Transportation", "UBER") } + past("Groceries", "UBER") +
        List(6) { past("Food & Snacks", "MEGHANA FOODS") }

    @Test
    fun aSavedRuleStillWinsOverHistory() = runBlocking {
        val result = pipeline(rule(MerchantRuleOrigin.LEARNED)).categorize(debit("Uber"), mapping, categories, predictor(rides), at)

        assertEquals("R1", result.decidedBy)
        assertEquals(ConfidenceBand.HIGH, result.band)
    }

    @Test
    fun theRuleIsLookedUpByMerchantAndTheBanksDirection() = runBlocking {
        val asked = mutableListOf<MerchantRuleKey>()
        val pipeline = CategorizationPipeline(keywords) { asked += it; null }

        pipeline.categorize(debit("Uber"), mapping, categories)
        pipeline.categorize(debit("Uber").copy(direction = Direction.CREDIT), mapping, categories)

        assertEquals(listOf(MerchantRuleKey("UBER", "DEBIT"), MerchantRuleKey("UBER", "CREDIT")), asked)
    }

    @Test
    fun historyMatchComesBeforeNaiveBayesAndKeywords() = runBlocking {
        // "swiggy" is a keyword for Food & Snacks, but the user files this merchant under Groceries.
        val history = List(3) { past("Groceries", "SWIGGY") } + List(10) { past("Food & Snacks", "MEGHANA FOODS") }

        val result = pipeline().categorize(debit("swiggy"), mapping, categories, predictor(history), at)

        assertEquals("R2", result.decidedBy)
        assertEquals("Groceries", result.category)
        assertEquals(ConfidenceBand.CHECK, result.band)
    }

    @Test
    fun naiveBayesRunsWhenThereIsNoHistoryMatchAndBeforeKeywords() = runBlocking {
        // A new Swiggy outlet shares the word "swiggy" with merchants the user files under Groceries.
        val history = List(6) { past("Groceries", "SWIGGY INSTAMART") } + List(6) { past("Transportation", "UBER") }

        val result = pipeline().categorize(debit("SWIGGY GENIE"), mapping, categories, predictor(history), at)

        assertEquals("NB", result.decidedBy)
        assertEquals("Groceries", result.category)
        assertEquals(ConfidenceBand.CHECK, result.band)
    }

    @Test
    fun keywordsStillRunWhenHistoryHasNothingToSay() = runBlocking {
        val result = pipeline().categorize(debit("swiggy.instamart@icici"), mapping, categories, predictor(rides), at)

        assertEquals("R7", result.decidedBy)
    }

    @Test
    fun aRenameOnACandidateRuleFindsHandTypedTransactions() = runBlocking {
        val candidate = rule(MerchantRuleOrigin.CANDIDATE).copy(description = "Cab")
        val history = List(3) { past("Transportation", merchant = null, description = "Cab") }

        val result = pipeline(candidate).categorize(debit("Uber"), mapping, categories, predictor(history), at)

        assertEquals("R2", result.decidedBy)
        assertEquals("Transportation", result.category)
    }

    @Test
    fun creditsCanBeGuessedFromHistoryButNotFromKeywords() = runBlocking {
        val credit = debit("ACME CORP").copy(direction = Direction.CREDIT)
        val history = List(3) { past("Salary", "ACME CORP", type = "Income") }

        val result = pipeline().categorize(credit, mapping, categories, predictor(history), at)

        assertEquals("Salary", result.category)
        assertEquals(ConfidenceBand.CHECK, result.band)
    }

    @Test
    fun onlyASavedRuleEverReachesHigh() = runBlocking {
        // However consistent the history, nothing but R1 is High, so nothing else notifies or is ticked in bulk.
        val history = List(200) { past("Transportation", "UBER") } + List(200) { past("Food & Snacks", "MEGHANA FOODS") }
        val merchants = listOf("Uber", "UBER TRIP", "Meghana Foods", "MEGHANA BIRYANI", "swiggy", "Shri Ujagar Fuels", "Nobody")

        merchants.forEach { merchant ->
            val result = pipeline().categorize(debit(merchant), mapping, categories, predictor(history), at)
            assertTrue("$merchant via ${result.decidedBy}", result.band != ConfidenceBand.HIGH)
        }
    }

    @Test
    fun aTransferRuleWinsOverHistoryAndTransfersNeverPredictACategory() = runBlocking {
        val transferRule = rule(MerchantRuleOrigin.LEARNED, category = "Transfer", type = "Transfer").copy(toAccountId = 7)
        val history = List(5) { past("Transportation", "UBER") }
        assertEquals(7L, pipeline(transferRule).categorize(debit("Uber"), mapping, categories, predictor(history), at).transferAccountId)

        // Moving money to "MOM" five times is no Category for her, though it still names the payment.
        val transfers = List(5) { past("Transfer", "MOM", description = "Rent share", type = "Transfer") }
        val result = pipeline().categorize(debit("Mom"), mapping, categories + "Transfer", predictor(transfers), at)
        assertNull(result.category)
        val facts = CaptureFacts("MOM", "Transfer", 100.0, at)
        assertEquals("Rent share", predictor(transfers).describe(facts, emptyList()).title)
    }
}
