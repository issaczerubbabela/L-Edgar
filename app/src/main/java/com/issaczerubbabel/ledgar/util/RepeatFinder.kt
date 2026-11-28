package com.issaczerubbabel.ledgar.util

import com.issaczerubbabel.ledgar.capture.categorize.MerchantNormalizer
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * A Transaction the user already pays on a schedule, offered on More > Repeating transactions so
 * they can make it a Repeat rule in a tap. Only a suggestion: nothing is saved until they accept it.
 */
data class RepeatSuggestion(
    /** Type, Account(s) and normalized description: stable as new rows arrive, so Not a repeat sticks. */
    val key: String,
    val type: String,
    /** The latest row's description, as the user (or auto-capture) wrote it. */
    val description: String,
    /** The most common Category among the matching rows. */
    val category: String,
    val accountId: Long?,
    val fromAccountId: Long?,
    val toAccountId: Long?,
    /** The latest amount. */
    val amount: Double,
    /** True when the amounts weren't all the same, so the screen says "about ₹649". */
    val isApproximate: Boolean,
    val frequency: String,
    val interval: Int,
    /** Day of month for monthly, day of week (1 = Monday) for weekly, as on [RecurringRule.anchorDay]. */
    val anchorDay: Int,
    /** How many rows matched. */
    val occurrences: Int,
    /** The date of the latest matching row. */
    val lastDate: LocalDate,
    /** The first scheduled date after the latest row: where the Repeat rule starts. */
    val nextDate: LocalDate
)

/**
 * Finds the bills, subscriptions, salaries and transfers the user already logs on a schedule, in
 * their own Ledger, and turns one into a Repeat rule. Pure and Android-free, like [RepeatSchedule].
 *
 * Rows are grouped by type, Account (the From and To Accounts for a Transfer) and description, cleaned
 * by [MerchantNormalizer] so "NETFLIX.COM 1234" and "Netflix" meet. Within a group, rows whose amounts
 * sit within 10% of each other form a stream, so two subscriptions billed by the same merchant don't
 * hide each other. A stream repeats when it fits a schedule: weekly (4+ rows), monthly or every 3
 * months (3+ rows), each row within a few days of its date on that schedule. Auto-captured bills land
 * a few days either side and slip a month now and then, so a little is forgiven: one late, missing
 * or extra payment for every 6 scheduled dates (at least one).
 */
object RepeatFinder {

    /** How far back the Ledger is read: a year of bills and one more month to see them start. */
    const val LOOKBACK_MONTHS = 13L

    /** Amounts within this share of the stream's median count as the same bill. */
    const val AMOUNT_TOLERANCE = 0.10

    private data class Cadence(
        val frequency: String,
        val interval: Int,
        /** About how long the cadence is, for the staleness check. */
        val days: Int,
        /** How far from its scheduled date a row may land and still be on time. */
        val onTimeDays: Long,
        val minRows: Int
    )

    private val WEEKLY = Cadence(RecurrenceFrequency.WEEKLY, 1, days = 7, onTimeDays = 1, minRows = 4)
    private val MONTHLY = Cadence(RecurrenceFrequency.MONTHLY, 1, days = 30, onTimeDays = 5, minRows = 3)
    private val QUARTERLY = Cadence(RecurrenceFrequency.MONTHLY, 3, days = 91, onTimeDays = 7, minRows = 3)
    private val CADENCES = listOf(WEEKLY, MONTHLY, QUARTERLY)

    /** A stream is still running while its latest row is no older than this many cadences. */
    private const val STALE_AFTER_CADENCES = 1.5

    /** Domain endings left by [MerchantNormalizer] ("NETFLIX COM"), dropped so "Netflix" matches. */
    private val DOMAIN_TOKENS = setOf("WWW", "COM", "IN", "NET", "ORG")

    private val ELIGIBLE_TYPES = setOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)

    /**
     * Every repeat found in [records] as of [today], soonest next date first. Rows a Repeat rule
     * added, Balance adjustments, deleted rows, rows without the Account(s) a rule needs, and
     * Expenses or Incomes with a blank or phone-number (P2P) description are ignored. Groups an
     * existing rule in [rules] already covers, and keys in [dismissed], are left out. At most one
     * suggestion per group, so one Not a repeat hides it.
     */
    fun suggestions(
        records: List<ExpenseRecord>,
        rules: List<RecurringRule>,
        dismissed: Set<String>,
        today: LocalDate
    ): List<RepeatSuggestion> {
        val since = today.minusMonths(LOOKBACK_MONTHS)
        val covered = rules.mapNotNullTo(HashSet()) { keyOf(it.type, it.accountId, it.fromAccountId, it.toAccountId, it.description) }

        return records.asSequence()
            .filter { it.recurringRuleId == null && it.syncAction != "DELETE" && it.type in ELIGIBLE_TYPES && it.amount > 0 }
            .mapNotNull { record ->
                val date = runCatching { LocalDate.parse(record.date) }.getOrNull() ?: return@mapNotNull null
                if (date.isBefore(since) || date.isAfter(today)) return@mapNotNull null
                val key = keyOf(record.type, record.accountId, record.fromAccountId, record.toAccountId, record.description)
                    ?: return@mapNotNull null
                Row(record, date, key)
            }
            .groupBy { it.key }
            .filterKeys { it !in covered && it !in dismissed }
            .mapNotNull { (key, rows) -> bestStream(key, rows, today) }
            .sortedWith(compareBy({ it.nextDate }, { it.description.lowercase() }, { it.key }))
    }

    /** The Ask me first (by default) Repeat rule [suggestion] becomes, starting on its next date. */
    fun toRule(suggestion: RepeatSuggestion, autoAdd: Boolean = false, today: LocalDate): RecurringRule = RecurringRule(
        type = suggestion.type,
        category = suggestion.category,
        description = suggestion.description,
        amount = suggestion.amount,
        accountId = suggestion.accountId,
        remarks = "",
        fromAccountId = suggestion.fromAccountId,
        toAccountId = suggestion.toAccountId,
        frequency = suggestion.frequency,
        interval = suggestion.interval,
        anchorDay = suggestion.anchorDay,
        startDate = suggestion.lastDate.toString(),
        nextDate = suggestion.nextDate.toString(),
        autoAdd = autoAdd,
        createdAt = today.toString()
    )

    /**
     * The group key for a row or rule, or null when it can't be suggested: an Expense or Income
     * keys on its Account and cleaned description (none when blank or a phone-number handle); a
     * Transfer keys on its From and To Accounts alone, since what the user calls a SIP or RD
     * instalment varies more than where the money goes.
     */
    fun keyOf(type: String, accountId: Long?, fromAccountId: Long?, toAccountId: Long?, description: String): String? =
        when (type) {
            TransactionType.TRANSFER ->
                if (fromAccountId == null || toAccountId == null) null else "$type|$fromAccountId>$toAccountId"
            TransactionType.EXPENSE, TransactionType.INCOME -> {
                val name = normalizedName(description)
                if (accountId == null || name == null) null else "$type|$accountId|$name"
            }
            else -> null
        }

    private fun normalizedName(description: String): String? {
        val merchant = MerchantNormalizer.normalize(description)
        if (merchant.isP2p || merchant.norm.isBlank()) return null
        val tokens = merchant.norm.split(' ')
        val kept = tokens.filter { it !in DOMAIN_TOKENS }
        return (if (kept.isEmpty()) tokens else kept).joinToString(" ")
    }

    private data class Row(val record: ExpenseRecord, val date: LocalDate, val key: String) {
        val amount: Double get() = record.amount
    }

    /** The stream in a group with the most rows that fits a schedule and is still running. */
    private fun bestStream(key: String, rows: List<Row>, today: LocalDate): RepeatSuggestion? =
        amountStreams(rows)
            .mapNotNull { stream -> detect(key, stream, today) }
            .maxWithOrNull(compareBy({ it.occurrences }, { it.lastDate }))

    /**
     * Splits a group into runs of similar amounts, latest first: each takes the rows within 10% of
     * the newest remaining amount, then keeps those within 10% of their own median (or all, if equal).
     */
    private fun amountStreams(rows: List<Row>): List<List<Row>> {
        var remaining = rows.sortedWith(compareByDescending<Row> { it.date }.thenByDescending { it.record.id })
        val streams = mutableListOf<List<Row>>()
        while (remaining.isNotEmpty()) {
            val newest = remaining.first().amount
            val near = remaining.filter { isNear(it.amount, newest) }
            remaining = remaining.filterNot { isNear(it.amount, newest) }
            val median = median(near.map { it.amount })
            streams += near.filter { isNear(it.amount, median) }
        }
        return streams
    }

    private fun isNear(amount: Double, reference: Double): Boolean =
        abs(amount - reference) <= reference * AMOUNT_TOLERANCE + EPSILON

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[mid] else (sorted[mid - 1] + sorted[mid]) / 2
    }

    private fun detect(key: String, stream: List<Row>, today: LocalDate): RepeatSuggestion? {
        val latest = stream.maxWithOrNull(compareBy<Row> { it.date }.thenBy { it.record.id }) ?: return null
        for (cadence in CADENCES) {
            if (stream.size < cadence.minRows) continue
            val age = ChronoUnit.DAYS.between(latest.date, today)
            if (age > cadence.days * STALE_AFTER_CADENCES) continue
            val fit = bestFit(stream, cadence) ?: continue
            val anchorDay = fit.anchorDay
            val amounts = stream.map { it.amount }
            return RepeatSuggestion(
                key = key,
                type = latest.record.type,
                description = latest.record.description.trim(),
                category = mostCommonCategory(stream, latest),
                accountId = latest.record.accountId,
                fromAccountId = latest.record.fromAccountId,
                toAccountId = latest.record.toAccountId,
                amount = latest.amount,
                isApproximate = amounts.any { abs(it - amounts.first()) > EPSILON },
                frequency = cadence.frequency,
                interval = cadence.interval,
                anchorDay = anchorDay,
                occurrences = stream.size,
                lastDate = latest.date,
                nextDate = RecurrenceCalculator.nextAfter(fit.lastScheduled, cadence.frequency, cadence.interval, anchorDay)
            )
        }
        return null
    }

    private fun mostCommonCategory(stream: List<Row>, latest: Row): String {
        val counts = stream.groupingBy { it.record.category }.eachCount()
        val top = counts.values.max()
        // On a tie, the latest row's Category wins.
        return latest.record.category.takeIf { counts[it] == top }
            ?: stream.sortedByDescending { it.date }.first { counts[it.record.category] == top }.record.category
    }

    /** Where a row lands on a schedule: which scheduled date it's for, and how many days off it is. */
    private data class Slot(val period: Long, val scheduled: LocalDate, val distance: Long)

    private data class Fit(val anchorDay: Int, val irregular: Int, val totalDistance: Long, val lastScheduled: LocalDate)

    /**
     * Tries each row's own day (and, every 3 months, its month) as the schedule's anchor and keeps
     * the one that fits with the fewest late, missing or extra payments, then the smallest drift.
     */
    private fun bestFit(stream: List<Row>, cadence: Cadence): Fit? {
        val byDate = stream.sortedBy { it.date }
        val anchors = byDate.asReversed().map { anchorOf(it.date, cadence) }.distinct()
        var best: Fit? = null
        for ((anchorDay, phase) in anchors) {
            val fit = evaluate(byDate, cadence, anchorDay, phase) ?: continue
            val current = best
            if (current == null || fit.irregular < current.irregular ||
                (fit.irregular == current.irregular && fit.totalDistance < current.totalDistance)
            ) {
                best = fit
            }
        }
        return best
    }

    private fun anchorOf(date: LocalDate, cadence: Cadence): Pair<Int, Int> =
        if (cadence.frequency == RecurrenceFrequency.WEEKLY) date.dayOfWeek.value to 0
        else date.dayOfMonth to Math.floorMod(monthIndex(YearMonth.from(date)), cadence.interval)

    /**
     * Places every row on its nearest scheduled date. Each date's closest row is its payment (late
     * when more than a few days off); any other row on the same date is extra; dates between the
     * first and last with no row are missed. Null unless enough dates were paid and the late,
     * extra and missed ones together are within the allowance.
     */
    private fun evaluate(byDate: List<Row>, cadence: Cadence, anchorDay: Int, phase: Int): Fit? {
        val slots = byDate.map { slotOf(it.date, cadence, anchorDay, phase) }
        val paid = slots.groupBy { it.period }.mapValues { (_, inPeriod) -> inPeriod.minBy { it.distance } }
        if (paid.size < cadence.minRows) return null

        val first = paid.keys.min()
        val last = paid.keys.max()
        val span = (last - first + 1).toInt()
        val late = paid.values.count { it.distance > cadence.onTimeDays }
        val extra = slots.size - paid.size
        val missed = span - paid.size
        val irregular = late + extra + missed
        if (irregular > maxOf(1, span / IRREGULAR_EVERY)) return null

        return Fit(
            anchorDay = anchorDay,
            irregular = irregular,
            totalDistance = paid.values.sumOf { it.distance },
            lastScheduled = paid.getValue(last).scheduled
        )
    }

    private fun slotOf(date: LocalDate, cadence: Cadence, anchorDay: Int, phase: Int): Slot {
        if (cadence.frequency == RecurrenceFrequency.WEEKLY) {
            var offset = Math.floorMod(date.dayOfWeek.value - anchorDay, 7)
            if (offset > 3) offset -= 7
            val scheduled = date.minusDays(offset.toLong())
            return Slot(Math.floorDiv(scheduled.toEpochDay(), 7L), scheduled, abs(offset).toLong())
        }
        val step = cadence.interval
        val month = monthIndex(YearMonth.from(date))
        var best: Slot? = null
        for (candidate in (month - step)..(month + step)) {
            if (Math.floorMod(candidate - phase, step) != 0) continue
            val ym = YearMonth.of(Math.floorDiv(candidate, 12), Math.floorMod(candidate, 12) + 1)
            val scheduled = ym.atDay(anchorDay.coerceIn(1, ym.lengthOfMonth()))
            val distance = abs(ChronoUnit.DAYS.between(scheduled, date))
            if (best == null || distance < best.distance) {
                best = Slot(Math.floorDiv(candidate - phase, step).toLong(), scheduled, distance)
            }
        }
        return checkNotNull(best)
    }

    private fun monthIndex(ym: YearMonth): Int = ym.year * 12 + ym.monthValue - 1

    /** One late, missing or extra payment is forgiven for every this many scheduled dates. */
    private const val IRREGULAR_EVERY = 6

    private const val EPSILON = 1e-6
}
