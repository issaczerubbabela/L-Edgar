package com.issaczerubbabel.ledgar.capture.categorize

import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleOrigin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MerchantRuleLearnerTest {

    private fun confirm(existing: MerchantRule?, category: String = "Food", alwaysUse: Boolean = false) =
        MerchantRuleLearner.onConfirmed(existing, "SWIGGY", "Expense", category, alwaysUse, now = 1)

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
        val learned = MerchantRule("SWIGGY", "Food", "Expense", origin = MerchantRuleOrigin.LEARNED, hitCount = 5, updatedAt = 0)

        val first = confirm(learned, category = "Groceries")
        assertTrue(first is RuleChange.Upsert)
        val afterFirst = (first as RuleChange.Upsert).rule
        assertEquals(1, afterFirst.streak)
        assertEquals("Food", afterFirst.category)

        assertEquals(RuleChange.Delete("SWIGGY"), confirm(afterFirst, category = "Groceries"))
    }

    @Test
    fun agreeingResetsTheOverrideCount() {
        val overridden = MerchantRule("SWIGGY", "Food", "Expense", origin = MerchantRuleOrigin.LEARNED, streak = 1, updatedAt = 0)

        val change = confirm(overridden, category = "Food") as RuleChange.Upsert

        assertEquals(0, change.rule.streak)
    }

    @Test
    fun aUserRuleIsNeverChangedAutomatically() {
        val user = MerchantRule("SWIGGY", "Food", "Expense", origin = MerchantRuleOrigin.USER, updatedAt = 0)

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
            MerchantRuleLearner.onConfirmed(null, "", "Expense", "Food", alwaysUse = true, now = 1)
        )
    }
}
