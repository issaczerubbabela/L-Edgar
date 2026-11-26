package com.issaczerubbabel.ledgar.capture.categorize

import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantRuleLearnerTest {

    private fun confirm(existing: MerchantRule?, category: String = "Food", alwaysUse: Boolean = false) =
        MerchantRuleLearner.onConfirmed(existing, "SWIGGY", "DEBIT", "Expense", category, alwaysUse, now = 1)

    private fun applied(change: RuleChange, previous: MerchantRule?): MerchantRule? = when (change) {
        is RuleChange.Upsert -> change.rule
        is RuleChange.Delete -> null
        RuleChange.None -> previous
    }

    @Test
    fun threeSameCategoryConfirmationsEarnALearnedRule() {
        var rule: MerchantRule? = null
        repeat(2) {
            rule = applied(confirm(rule), rule)
            assertEquals(MerchantRuleOrigin.CANDIDATE, rule!!.origin)
        }

        rule = applied(confirm(rule), rule)

        assertEquals(MerchantRuleOrigin.LEARNED, rule?.origin)
        assertEquals("Food", rule?.category)
        assertEquals(3, rule?.hitCount)
    }

    @Test
    fun aDifferentCategoryRestartsTheRun() {
        var rule: MerchantRule? = null
        repeat(2) { rule = applied(confirm(rule), rule) }

        rule = applied(confirm(rule, category = "Groceries"), rule)
        assertEquals("Groceries", rule?.category)
        assertEquals(1, rule?.streak)

        rule = applied(confirm(rule, category = "Groceries"), rule)
        assertEquals(MerchantRuleOrigin.CANDIDATE, rule?.origin)
    }

    @Test
    fun twoOverridesInARowDeleteALearnedRule() {
        val learned = MerchantRule("SWIGGY", "DEBIT", "Food", "Expense", origin = MerchantRuleOrigin.LEARNED, hitCount = 5, updatedAt = 0)

        val first = confirm(learned, category = "Groceries")
        assertTrue(first is RuleChange.Upsert)
        val afterFirst = (first as RuleChange.Upsert).rule
        assertEquals(1, afterFirst.streak)
        assertEquals("Food", afterFirst.category)

        assertEquals(RuleChange.Delete("SWIGGY", "DEBIT"), confirm(afterFirst, category = "Groceries"))
    }

    @Test
    fun agreeingResetsTheOverrideCount() {
        val overridden = MerchantRule("SWIGGY", "DEBIT", "Food", "Expense", origin = MerchantRuleOrigin.LEARNED, streak = 1, updatedAt = 0)

        val change = confirm(overridden, category = "Food") as RuleChange.Upsert

        assertEquals(0, change.rule.streak)
    }

    @Test
    fun aUserRuleIsNeverChangedAutomatically() {
        val user = MerchantRule("SWIGGY", "DEBIT", "Food", "Expense", origin = MerchantRuleOrigin.USER, updatedAt = 0)

        assertEquals(RuleChange.None, confirm(user, category = "Groceries"))
        assertEquals(RuleChange.None, confirm(user, category = "Groceries"))
    }

    @Test
    fun alwaysUseWritesAUserRuleImmediately() {
        val change = confirm(null, alwaysUse = true) as RuleChange.Upsert

        assertEquals(MerchantRuleOrigin.USER, change.rule.origin)
        assertEquals("Food", change.rule.category)
    }

    @Test
    fun blankMerchantsAreNeverLearned() {
        assertEquals(
            RuleChange.None,
            MerchantRuleLearner.onConfirmed(null, "", "DEBIT", "Expense", "Food", alwaysUse = true, now = 1)
        )
    }

    private fun transfer(existing: MerchantRule?, toAccountId: Long = CUB) =
        MerchantRuleLearner.onConfirmed(existing, "CRED", "DEBIT", "Transfer", "", alwaysUse = false, now = 1, toAccountId = toAccountId)

    @Test
    fun threeTransfersToTheSameAccountEarnALearnedTransferRule() {
        var rule: MerchantRule? = null
        repeat(3) { rule = applied(transfer(rule), rule) }

        assertEquals(MerchantRuleOrigin.LEARNED, rule?.origin)
        assertEquals("Transfer", rule?.type)
        assertEquals("Transfer", rule?.category)
        assertEquals(CUB, rule?.toAccountId)
        assertEquals("DEBIT", rule?.direction)
    }

    @Test
    fun aTransferToAnotherAccountRestartsTheRun() {
        var rule: MerchantRule? = null
        repeat(2) { rule = applied(transfer(rule), rule) }

        rule = applied(transfer(rule, toAccountId = AXIS), rule)

        assertEquals(MerchantRuleOrigin.CANDIDATE, rule?.origin)
        assertEquals(AXIS, rule?.toAccountId)
        assertEquals(1, rule?.streak)
    }

    @Test
    fun confirmingALearnedExpenseMerchantAsATransferIsAnOverride() {
        val learned = MerchantRule("CRED", "DEBIT", "Bills", "Expense", origin = MerchantRuleOrigin.LEARNED, hitCount = 5, updatedAt = 0)

        val afterFirst = (transfer(learned) as RuleChange.Upsert).rule
        assertEquals(1, afterFirst.streak)
        assertEquals("Expense", afterFirst.type)

        assertEquals(RuleChange.Delete("CRED", "DEBIT"), transfer(afterFirst))
    }

    @Test
    fun aUserRuleIsNeverTurnedIntoATransfer() {
        val user = MerchantRule("CRED", "DEBIT", "Bills", "Expense", origin = MerchantRuleOrigin.USER, updatedAt = 0)

        assertEquals(RuleChange.None, transfer(user))
    }

    @Test
    fun alwaysUseCanSaveATransferRule() {
        val change = MerchantRuleLearner.onConfirmed(null, "CRED", "DEBIT", "Transfer", "", alwaysUse = true, now = 1, toAccountId = CUB)

        val rule = (change as RuleChange.Upsert).rule
        assertEquals(MerchantRuleOrigin.USER, rule.origin)
        assertEquals(CUB, rule.toAccountId)
    }

    private companion object {
        const val CUB = 2L
        const val AXIS = 3L
    }
}
