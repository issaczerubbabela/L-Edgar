package com.issaczerubbabel.ledgar.capture.categorize

import com.issaczerubbabel.ledgar.capture.parse.Channel
import com.issaczerubbabel.ledgar.capture.parse.Direction
import com.issaczerubbabel.ledgar.capture.parse.ParsedTxn
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleOrigin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

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
        merchantNorm = "UBER", category = category, type = type, origin = origin, updatedAt = 0
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
}
