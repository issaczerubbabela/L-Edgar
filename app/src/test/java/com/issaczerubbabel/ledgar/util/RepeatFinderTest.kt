package com.issaczerubbabel.ledgar.util

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RepeatFinderTest {

    private val today = LocalDate.of(2026, 11, 20)
    private var nextId = 1L

    private fun row(
        date: String,
        amount: Double = 649.0,
        description: String = "Netflix",
        type: String = TransactionType.EXPENSE,
        category: String = "Subscriptions",
        accountId: Long? = 1,
        fromAccountId: Long? = null,
        toAccountId: Long? = null,
        recurringRuleId: Long? = null,
        syncAction: String = "INSERT"
    ) = ExpenseRecord(
        id = nextId++, date = date, type = type, category = category, description = description, amount = amount,
        accountId = accountId, remarks = "", fromAccountId = fromAccountId, toAccountId = toAccountId,
        syncAction = syncAction, recurringRuleId = recurringRuleId
    )

    private fun monthly(day: Int, months: List<String>, amount: Double = 649.0, description: String = "Netflix") =
        months.map { row(date = "$it-${day.toString().padStart(2, '0')}", amount = amount, description = description) }

    private val sixMonths = listOf("2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11")

    private fun find(records: List<ExpenseRecord>, rules: List<RecurringRule> = emptyList(), dismissed: Set<String> = emptySet()) =
        RepeatFinder.suggestions(records, rules, dismissed, today)

    private fun rule(description: String = "Netflix", type: String = TransactionType.EXPENSE, accountId: Long? = 1,
                     fromAccountId: Long? = null, toAccountId: Long? = null) = RecurringRule(
        id = 9, type = type, category = "Subscriptions", description = description, amount = 649.0, accountId = accountId,
        remarks = "", fromAccountId = fromAccountId, toAccountId = toAccountId, frequency = RecurrenceFrequency.MONTHLY,
        anchorDay = 5, startDate = "2026-01-05", nextDate = "2026-12-05", createdAt = "2026-01-05"
    )

    @Test
    fun `three monthly rows on the same day are one monthly suggestion starting the next month`() {
        val found = find(monthly(5, listOf("2026-09", "2026-10", "2026-11")))

        assertEquals(1, found.size)
        val s = found.single()
        assertEquals(RecurrenceFrequency.MONTHLY, s.frequency)
        assertEquals(1, s.interval)
        assertEquals(5, s.anchorDay)
        assertEquals(3, s.occurrences)
        assertEquals(649.0, s.amount, 0.0)
        assertFalse(s.isApproximate)
        assertEquals(LocalDate.of(2026, 11, 5), s.lastDate)
        assertEquals(LocalDate.of(2026, 12, 5), s.nextDate)
        assertEquals("Netflix", s.description)
        assertEquals("Subscriptions", s.category)
    }

    @Test
    fun `two monthly rows are not enough`() {
        assertTrue(find(monthly(5, listOf("2026-10", "2026-11"))).isEmpty())
    }

    @Test
    fun `a captured bill a few days either side of its day still counts as monthly`() {
        val records = listOf("2026-07-03", "2026-08-07", "2026-09-05", "2026-10-02", "2026-11-08").map { row(it) }

        val s = find(records).single()

        assertEquals(RecurrenceFrequency.MONTHLY, s.frequency)
        assertEquals(LocalDate.of(2026, 11, 8), s.lastDate)
        assertEquals(LocalDate.of(2026, 12, s.anchorDay), s.nextDate)
        assertTrue(s.anchorDay in 3..7)
    }

    @Test
    fun `weekly needs four rows`() {
        val four = listOf("2026-10-30", "2026-11-06", "2026-11-13", "2026-11-20").map { row(it, amount = 300.0, description = "Maid") }
        val s = find(four).single()
        assertEquals(RecurrenceFrequency.WEEKLY, s.frequency)
        assertEquals(LocalDate.of(2026, 11, 20).dayOfWeek.value, s.anchorDay)
        assertEquals(LocalDate.of(2026, 11, 27), s.nextDate)

        assertTrue(find(four.drop(1)).isEmpty())
    }

    @Test
    fun `every three months is found from three rows`() {
        val records = listOf("2026-02-10", "2026-05-10", "2026-08-10", "2026-11-10").map { row(it, amount = 2400.0, description = "Gym") }

        val s = find(records).single()

        assertEquals(RecurrenceFrequency.MONTHLY, s.frequency)
        assertEquals(3, s.interval)
        assertEquals(10, s.anchorDay)
        assertEquals(LocalDate.of(2027, 2, 10), s.nextDate)
    }

    @Test
    fun `one late payment is forgiven, two are not`() {
        val oneLate = monthly(5, sixMonths).map { if (it.date == "2026-08-05") it.copy(date = "2026-08-19") else it }
        assertEquals(1, find(oneLate).size)

        val twoLate = oneLate.map { if (it.date == "2026-10-05") it.copy(date = "2026-10-19") else it }
        assertTrue(find(twoLate).isEmpty())
    }

    @Test
    fun `one missed month is forgiven, two are not`() {
        assertEquals(1, find(monthly(5, sixMonths - "2026-08")).size)
        assertTrue(find(monthly(5, sixMonths - "2026-08" - "2026-09")).isEmpty())
    }

    @Test
    fun `amounts within 10 percent of the median are approximate, beyond it they are not the same bill`() {
        val within = listOf(100.0, 110.0, 90.0, 100.0).mapIndexed { i, amount ->
            row(date = "2026-${(8 + i).toString().padStart(2, '0')}-05", amount = amount, description = "Electricity")
        }
        val s = find(within).single()
        assertTrue(s.isApproximate)
        assertEquals(100.0, s.amount, 0.0)

        val beyond = listOf(100.0, 111.0, 100.0).mapIndexed { i, amount ->
            row(date = "2026-${(9 + i).toString().padStart(2, '0')}-05", amount = amount, description = "Electricity")
        }
        assertTrue(find(beyond).isEmpty())
    }

    @Test
    fun `the suggestion uses the latest amount`() {
        val records = monthly(5, listOf("2026-08", "2026-09", "2026-10")) +
            row(date = "2026-11-05", amount = 699.0)

        val s = find(records).single()

        assertEquals(699.0, s.amount, 0.0)
        assertTrue(s.isApproximate)
    }

    @Test
    fun `two subscriptions from the same merchant are told apart by amount`() {
        val records = monthly(2, sixMonths, amount = 130.0, description = "Google") +
            monthly(15, sixMonths, amount = 199.0, description = "Google")

        val s = find(records).single()

        // One suggestion per group, so one Not a repeat hides it; the run seen most often (then latest) wins.
        assertEquals(6, s.occurrences)
        assertEquals(199.0, s.amount, 0.0)
    }

    @Test
    fun `a stream whose latest row is older than one and a half cadences is dropped`() {
        // Latest on 5 Oct, 46 days before 20 Nov.
        assertTrue(find(monthly(5, listOf("2026-07", "2026-08", "2026-09", "2026-10"))).isEmpty())
        // Latest on 6 Oct, 45 days before: still running.
        assertEquals(1, find(monthly(6, listOf("2026-07", "2026-08", "2026-09", "2026-10"))).size)
    }

    @Test
    fun `a 31st anchor lands on the 30th and the 28th`() {
        val records = listOf("2026-07-31", "2026-08-31", "2026-09-30", "2026-10-31").map { row(it, amount = 15000.0, description = "Rent") }

        val s = find(records).single()

        assertEquals(31, s.anchorDay)
        assertEquals(LocalDate.of(2026, 11, 30), s.nextDate)

        val intoFebruary = listOf("2026-10-31", "2026-11-30", "2026-12-31", "2027-01-31").map { row(it, amount = 15000.0, description = "Rent") }
        val february = RepeatFinder.suggestions(intoFebruary, emptyList(), emptySet(), LocalDate.of(2027, 2, 10)).single()
        assertEquals(LocalDate.of(2027, 2, 28), february.nextDate)
    }

    @Test
    fun `rows a rule added, Balance adjustments, deleted, blank and P2P rows never suggest`() {
        val excluded = monthly(5, sixMonths).map { it.copy(recurringRuleId = 4) } +
            monthly(6, sixMonths).map { it.copy(type = TransactionType.ADJUSTMENT, description = "Reconcile") } +
            monthly(7, sixMonths).map { it.copy(syncAction = "DELETE", description = "Deleted bill") } +
            monthly(8, sixMonths).map { it.copy(description = "  ") } +
            monthly(9, sixMonths).map { it.copy(description = "9876543210@ybl") } +
            monthly(10, sixMonths).map { it.copy(accountId = null, description = "No account") }

        assertTrue(find(excluded).isEmpty())
    }

    @Test
    fun `NETFLIX COM 1234 and Netflix are the same merchant`() {
        val records = listOf(
            row("2026-09-05", description = "NETFLIX.COM 1234"),
            row("2026-10-05", description = "Netflix"),
            row("2026-11-05", description = "netflix.com")
        )

        val s = find(records).single()

        assertEquals(3, s.occurrences)
        assertEquals("netflix.com", s.description)
    }

    @Test
    fun `a group an existing rule covers is not suggested`() {
        val records = monthly(5, sixMonths, description = "NETFLIX.COM")

        assertTrue(find(records, rules = listOf(rule(description = "Netflix"))).isEmpty())
        // Another Account, or another type, is another group.
        assertEquals(1, find(records, rules = listOf(rule(accountId = 2))).size)
        assertEquals(1, find(records, rules = listOf(rule(type = TransactionType.INCOME))).size)
    }

    @Test
    fun `a dismissed key is not suggested`() {
        val records = monthly(5, sixMonths)
        val key = find(records).single().key

        assertTrue(find(records, dismissed = setOf(key)).isEmpty())
    }

    @Test
    fun `Transfers key on their From and To Accounts, whatever the description`() {
        val sip = listOf("2026-09-10" to "SIP", "2026-10-10" to "ICCL SIP 0042", "2026-11-10" to "").map { (date, text) ->
            row(date, amount = 5000.0, description = text, type = TransactionType.TRANSFER, category = "Transfer",
                accountId = 1, fromAccountId = 1, toAccountId = 7)
        }
        val toAnother = listOf("2026-09-10", "2026-10-10").map {
            row(it, amount = 5000.0, description = "SIP", type = TransactionType.TRANSFER, accountId = 1, fromAccountId = 1, toAccountId = 8)
        }

        val s = find(sip + toAnother).single()

        assertEquals(TransactionType.TRANSFER, s.type)
        assertEquals(1L, s.fromAccountId)
        assertEquals(7L, s.toAccountId)
        assertEquals(3, s.occurrences)
        assertTrue(find(sip, rules = listOf(rule(type = TransactionType.TRANSFER, fromAccountId = 1, toAccountId = 7))).isEmpty())
    }

    @Test
    fun `everyday spending at one merchant is not a repeat`() {
        val tea = (0 until 60).map { row(date = today.minusDays(it.toLong()).toString(), amount = 20.0, description = "Tea") }

        assertTrue(find(tea).isEmpty())
    }

    @Test
    fun `rows older than 13 months are ignored`() {
        // Counted, the row from October 2025 would leave seven missed months before June.
        val records = monthly(5, sixMonths) + row("2025-10-05")

        assertEquals(6, find(records).single().occurrences)
    }

    @Test
    fun `the most common Category wins`() {
        val records = monthly(5, listOf("2026-08", "2026-09", "2026-10")) +
            row(date = "2026-11-05", category = "Entertainment")

        assertEquals("Subscriptions", find(records).single().category)
    }

    @Test
    fun `toRule makes an Ask me first rule from the next date`() {
        val s = find(monthly(5, sixMonths)).single()

        val rule = RepeatFinder.toRule(s, today = today)

        assertFalse(rule.autoAdd)
        assertEquals(0L, rule.id)
        assertEquals("2026-12-05", rule.nextDate)
        assertEquals("2026-11-05", rule.startDate)
        assertEquals(RecurrenceFrequency.MONTHLY, rule.frequency)
        assertEquals(5, rule.anchorDay)
        assertEquals(649.0, rule.amount, 0.0)
        assertEquals(1L, rule.accountId)
        assertEquals("Netflix", rule.description)
        assertEquals(today.toString(), rule.createdAt)
        assertEquals(s.key, RepeatFinder.keyOf(rule.type, rule.accountId, rule.fromAccountId, rule.toAccountId, rule.description))
        assertNull(rule.endDate)
        assertTrue(RepeatFinder.toRule(s, autoAdd = true, today = today).autoAdd)
    }
}
