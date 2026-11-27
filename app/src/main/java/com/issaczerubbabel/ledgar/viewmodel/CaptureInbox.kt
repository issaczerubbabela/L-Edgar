package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.capture.CaptureConfirmation
import com.issaczerubbabel.ledgar.capture.CaptureTrace
import com.issaczerubbabel.ledgar.capture.pair.TransferSuggestions
import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.util.formatRupeesExact
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** To review's filters. Every waiting capture is either Ready or Needs a pick, never both. */
enum class CaptureFilter(val label: String) {
    ALL("All"),
    NEEDS_PICK("Needs a pick"),
    READY("Ready")
}

/** One line of the bulk sheet: [tickedAtStart] is how it opens, before the user changes anything. */
data class BulkItem(val row: CaptureRowUi, val tickedAtStart: Boolean)

/**
 * One day of To review, newest first: the rows the filter shows, how many, and how much money
 * left ([spent]) and came in ([received]). [bulk] is the day's Confirm day sheet, worked out from
 * all of the day's captures, whatever the filter shows.
 */
data class CaptureDay(
    val date: LocalDate,
    val rows: List<CaptureRowUi>,
    val spent: Double,
    val received: Double,
    val bulk: List<BulkItem>
) {
    val count: Int get() = rows.size
}

/** The To review list as days, its filters, the bulk sheet and what TalkBack reads. All pure. */
object CaptureInbox {

    /**
     * Folds each Suggested Transfer ([TransferSuggestions]) into one row: the debit's, carrying the
     * credit as its [CaptureRowUi.transferPartner], with the credit's own row left out. A capture
     * that may already be in the Ledger, or that the user split from a suggestion, stays on its own.
     * The own-account signal is the capture's Account, which only its digits' Account alias sets.
     */
    fun withTransferSuggestions(rows: List<CaptureRowUi>): List<CaptureRowUi> {
        val candidates = rows.filter { it.match == null }
        val split = candidates.filter { CaptureTrace.parse(it.capture.traceJson).splitFromTransfer }.map { it.capture.id }.toSet()
        val suggestions = TransferSuggestions.find(candidates.map { it.capture }, split) { it.accountId }
        if (suggestions.isEmpty()) return rows
        val byId = rows.associateBy { it.capture.id }
        val partnerOf = suggestions.associate { it.debit.id to byId.getValue(it.credit.id) }
        val folded = suggestions.map { it.credit.id }.toSet()
        return rows.filterNot { it.capture.id in folded }.map { row ->
            partnerOf[row.capture.id]?.let { row.copy(transferPartner = it) } ?: row
        }
    }

    /** The day a capture's Transaction falls on, in the phone's zone: the date Confirm writes. */
    fun dayOf(row: CaptureRowUi, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(row.capture.txnTime).atZone(zone).toLocalDate()

    fun matches(row: CaptureRowUi, filter: CaptureFilter): Boolean = when (filter) {
        CaptureFilter.ALL -> true
        CaptureFilter.READY -> row.isReady
        CaptureFilter.NEEDS_PICK -> !row.isReady
    }

    fun count(rows: List<CaptureRowUi>, filter: CaptureFilter): Int = rows.count { matches(it, filter) }

    /** Days newest first, each newest capture first. A day with no capture under [filter] is left out. */
    fun days(rows: List<CaptureRowUi>, filter: CaptureFilter, zone: ZoneId): List<CaptureDay> =
        rows.groupBy { dayOf(it, zone) }
            .toSortedMap(compareByDescending { it })
            .mapNotNull { (date, all) ->
                val shown = all.filter { matches(it, filter) }.sortedByDescending { it.capture.txnTime }
                if (shown.isEmpty()) return@mapNotNull null
                CaptureDay(
                    date = date,
                    rows = shown,
                    // A Suggested Transfer only moves money between the user's own Accounts.
                    spent = shown.filter { it.isDebit && !it.isTransferSuggestion }.sumOf { it.capture.amount },
                    received = shown.filterNot { it.isDebit || it.isTransferSuggestion }.sumOf { it.capture.amount },
                    bulk = bulkSheet(all.sortedByDescending { it.capture.txnTime })
                )
            }

    /**
     * The bulk sheet for [rows] (Confirm day or Review all): High rows ticked, Ready rows still
     * worth a check listed unticked, and everything else left out. Every row the sheet confirms is
     * one the user left ticked or ticked themselves (ADR-0005).
     */
    fun bulkSheet(rows: List<CaptureRowUi>): List<BulkItem> = rows.mapNotNull { row ->
        when {
            row.isBulkCandidate -> BulkItem(row, tickedAtStart = true)
            row.isTransferSuggestion -> null
            row.isReady && row.band == ConfidenceBand.CHECK -> BulkItem(row, tickedAtStart = false)
            else -> null
        }
    }

    /**
     * What the sheet confirms: the rows ticked at the start that the user didn't untick, plus the
     * ones they ticked. Kept as [toggled] ids so a row the sheet gains while open starts as it should.
     */
    fun ticked(sheet: List<BulkItem>, toggled: Set<Long>): List<CaptureRowUi> =
        sheet.filter { it.tickedAtStart != (it.row.capture.id in toggled) }.map { it.row }

    /** Tapping a row expands it and collapses any other; tapping the open one closes it. */
    fun toggleExpanded(expandedId: Long?, captureId: Long): Long? = if (expandedId == captureId) null else captureId

    /** What Confirm saves for each of [rows]; a row still missing something is left out. */
    fun confirmations(rows: List<CaptureRowUi>): List<CaptureConfirmation> =
        rows.mapNotNull { row -> row.toEdits()?.let { CaptureConfirmation(row.capture, it, row.title) } }

    private val SPOKEN_DAY = DateTimeFormatter.ofPattern("d MMMM")
    private val SPOKEN_HEADING = DateTimeFormatter.ofPattern("EEEE d MMMM")
    private val SHORT_DAY = DateTimeFormatter.ofPattern("EEE d MMM")

    /** A day header's title: Today, Yesterday, else "Fri 12 Nov". */
    fun dayTitle(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(SHORT_DAY)
    }

    /** "3 waiting · −₹580 · +₹5,000": only the sides that moved. */
    fun daySummary(day: CaptureDay): String = listOfNotNull(
        "${day.count} waiting",
        day.spent.takeIf { it > 0 }?.let { "−" + formatRupeesExact(it) },
        day.received.takeIf { it > 0 }?.let { "+" + formatRupeesExact(it) }
    ).joinToString(" · ")

    /** The day header as TalkBack reads it: "Today, Friday 12 November, 3 waiting, ₹580 out". */
    fun spokenDay(day: CaptureDay, today: LocalDate): String = listOfNotNull(
        dayTitle(day.date, today).takeIf { day.date == today || day.date == today.minusDays(1) },
        day.date.format(SPOKEN_HEADING),
        "${day.count} waiting",
        day.spent.takeIf { it > 0 }?.let { formatRupeesExact(it) + " out" },
        day.received.takeIf { it > 0 }?.let { formatRupeesExact(it) + " in" }
    ).joinToString(", ")

    /**
     * A Suggested Transfer as one sentence: "Suggested transfer, ₹5,000 from HDFC Savings to CUB
     * Savings, 12 November". A side still to pick is said so, and a guessed one as suggested.
     */
    fun spokenTransferSuggestion(row: CaptureRowUi, fromName: String?, toName: String?, zone: ZoneId): String {
        val credit = row.transferPartner ?: return spokenLabel(row, fromName, null, zone)
        fun side(name: String?, guess: Boolean, missing: String) = when {
            name == null -> missing
            guess -> "suggested $name"
            else -> name
        }
        return listOf(
            "Suggested transfer",
            formatRupeesExact(row.capture.amount) +
                " from " + side(fromName, row.accountIsGuess, "an account still to pick") +
                " to " + side(toName, credit.accountIsGuess, "an account still to pick"),
            dayOf(row, zone).format(SPOKEN_DAY)
        ).joinToString(", ")
    }

    /**
     * A compact row as one sentence: "Lunch, ₹80, Food, HDFC, check, 12 November". Says what's
     * missing, a guessed Account, and a possible Ledger match, which the row shows only as marks.
     */
    fun spokenLabel(row: CaptureRowUi, accountName: String?, otherAccountName: String?, zone: ZoneId): String {
        val amount = formatRupeesExact(row.capture.amount) + if (row.isDebit) "" else " received"
        val outcome = when {
            row.isTransfer -> {
                val side = if (row.isDebit) "to" else "from"
                otherAccountName?.let { "Transfer $side $it" } ?: "Transfer, needs the ${side.replaceFirstChar { it.uppercase() }} account"
            }
            else -> row.category?.takeIf { it.isNotBlank() } ?: "needs a category"
        }
        val account = when {
            accountName == null -> "needs an account"
            row.accountIsGuess -> "suggested account $accountName"
            else -> accountName
        }
        val band = when (row.band) {
            ConfidenceBand.HIGH -> "high confidence"
            ConfidenceBand.CHECK -> "check"
            ConfidenceBand.LOW -> "low confidence"
        }
        return listOfNotNull(
            row.title,
            amount,
            outcome,
            account,
            band,
            "possibly already logged".takeIf { row.match != null },
            dayOf(row, zone).format(SPOKEN_DAY)
        ).joinToString(", ")
    }
}
