package com.issaczerubbabel.ledgar.ledger

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayMonthFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** One date in Search's results: its header names the month too, since results span all time. */
data class SearchDay(
    /** Null for results whose date can't be read, listed last under "No date". */
    val date: LocalDate?,
    /** "Fri, 9 Oct 2026". */
    val header: String,
    /** Newest logged first, as on the Ledger. */
    val rows: List<LedgerRow>
)

/** Search's results grouped by date, newest first; any with an unreadable date come last, never dropped. */
fun Ledger.searchDays(results: List<ExpenseRecord>, accounts: List<AccountRecord>): List<SearchDay> {
    val names = accounts.associate { it.id to it.accountName }
    val (dated, undated) = results.map { dateOf(it) to it }.partition { it.first != null }
    val days = dated.groupBy({ it.first!! }, { it.second })
        .toSortedMap(reverseOrder())
        .map { (date, onDay) -> SearchDay(date, date.format(sheetDateFormat), rowsNewestFirst(onDay, names)) }
    return if (undated.isEmpty()) days else days + SearchDay(null, "No date", rowsNewestFirst(undated.map { it.second }, names))
}

/** The date-range button's text: "Any time", "9 Oct 2026", "1 Sep – 9 Oct 2026" or "28 Dec 2025 – 3 Jan 2026". */
fun Ledger.dateRangeLabel(start: LocalDate?, end: LocalDate?): String = when {
    start == null || end == null -> "Any time"
    start == end -> start.format(shortDateFormat)
    start.year == end.year -> "${start.format(dayMonthFormat)} – ${end.format(shortDateFormat)}"
    else -> "${start.format(shortDateFormat)} – ${end.format(shortDateFormat)}"
}

/** Where [query] appears in [text], ignoring case: what Search highlights. */
fun Ledger.matchRanges(text: String, query: String): List<IntRange> {
    val needle = query.trim()
    if (needle.isEmpty()) return emptyList()
    val ranges = mutableListOf<IntRange>()
    var from = text.indexOf(needle, ignoreCase = true)
    while (from >= 0) {
        ranges += from until from + needle.length
        from = text.indexOf(needle, from + needle.length, ignoreCase = true)
    }
    return ranges
}
