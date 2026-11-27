package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** To review grouped by day (#142): the days, the filters, the bulk sheet and what TalkBack reads. */
class CaptureInboxTest {

    // Not UTC, so a day boundary in the phone's zone differs from one in UTC.
    private val zone = ZoneId.of("Asia/Kolkata")
    private var nextId = 1L

    private fun at(date: String, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(LocalDate.parse(date), java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun row(
        time: Long,
        amount: Double = 100.0,
        direction: String = "DEBIT",
        band: ConfidenceBand = ConfidenceBand.CHECK,
        category: String? = "Food",
        accountId: Long? = HDFC,
        accountIsGuess: Boolean = false,
        match: ExpenseRecord? = null,
        type: String = if (direction == "CREDIT") TransactionType.INCOME else TransactionType.EXPENSE,
        otherAccountId: Long? = null,
        title: String = "Lunch"
    ): CaptureRowUi {
        val capture = CapturedTransaction(
            id = nextId++, sources = "NOTIF", sender = "HDFC Bank", rawText = "", rawHash = "h$nextId",
            capturedAt = time, txnTime = time, amount = amount, direction = direction, channel = "UPI", accountId = accountId
        )
        return CaptureRowUi(
            capture = capture, type = type, category = category, accountId = accountId, otherAccountId = otherAccountId,
            band = band, why = null, title = title, bankTitle = title, accountIsGuess = accountIsGuess, match = match
        )
    }

    private val logged = ExpenseRecord(
        id = 9, date = "2026-11-12", type = TransactionType.EXPENSE, category = "Food", description = "Lunch",
        amount = 80.0, accountId = HDFC, remarks = ""
    )

    // --- days ---

    @Test
    fun capturesFromThreeDaysShowUnderThreeDaysNewestFirstWithTheirCountsAndTotals() {
        val rows = listOf(
            row(at("2026-11-10", 9), amount = 50.0),
            row(at("2026-11-12", 13), amount = 80.0),
            row(at("2026-11-11", 8), amount = 200.0),
            row(at("2026-11-12", 20), amount = 500.0),
            row(at("2026-11-12", 10), amount = 5_000.0, direction = "CREDIT", category = "Salary"),
            row(at("2026-11-10", 18), amount = 25.5)
        )

        val days = CaptureInbox.days(rows, CaptureFilter.ALL, zone)

        assertEquals(listOf("2026-11-12", "2026-11-11", "2026-11-10").map(LocalDate::parse), days.map { it.date })
        assertEquals(listOf(3, 1, 2), days.map { it.count })
        assertEquals(listOf(580.0, 200.0, 75.5), days.map { it.spent })
        assertEquals(listOf(5_000.0, 0.0, 0.0), days.map { it.received })
        assertEquals("newest capture first", listOf(500.0, 80.0, 5_000.0), days[0].rows.map { it.capture.amount })
        assertEquals("3 waiting · −₹580 · +₹5,000", CaptureInbox.daySummary(days[0]))
        assertEquals("2 waiting · −₹75.50", CaptureInbox.daySummary(days[2]))
    }

    @Test
    fun aCaptureFallsOnItsDayInThePhonesZoneNotInUtc() {
        // 00:30 in India is still the day before in UTC.
        val justAfterMidnight = row(at("2026-11-12", 0, 30))

        assertEquals(LocalDate.parse("2026-11-12"), CaptureInbox.dayOf(justAfterMidnight, zone))
        assertEquals(LocalDate.parse("2026-11-11"), CaptureInbox.dayOf(justAfterMidnight, ZoneId.of("UTC")))
    }

    @Test
    fun dayTitlesSayTodayAndYesterday() {
        val today = LocalDate.parse("2026-11-13")
        assertEquals("Today", CaptureInbox.dayTitle(today, today))
        assertEquals("Yesterday", CaptureInbox.dayTitle(today.minusDays(1), today))
        assertEquals("Wed 11 Nov", CaptureInbox.dayTitle(today.minusDays(2), today))
    }

    @Test
    fun aDayHeaderReadsAsOneHeadingSentence() {
        val day = CaptureInbox.days(listOf(row(at("2026-11-12", 13), amount = 80.0)), CaptureFilter.ALL, zone).single()

        assertEquals("Today, Thursday 12 November, 1 waiting, ₹80 out", CaptureInbox.spokenDay(day, LocalDate.parse("2026-11-12")))
        assertEquals("Thursday 12 November, 1 waiting, ₹80 out", CaptureInbox.spokenDay(day, LocalDate.parse("2026-11-20")))
    }

    // --- filters ---

    @Test
    fun eachFilterShowsExactlyTheMatchingRowsAndHidesADayWithNone() {
        val ready = row(at("2026-11-12", 13))
        val noCategory = row(at("2026-11-12", 14), category = null, band = ConfidenceBand.LOW)
        val guessedAccount = row(at("2026-11-11", 9), accountIsGuess = true)
        val noAccount = row(at("2026-11-11", 10), accountId = null)
        val matched = row(at("2026-11-11", 11), match = logged)
        val transferReady = row(at("2026-11-10", 9), type = TransactionType.TRANSFER, category = null, otherAccountId = CUB)
        val rows = listOf(ready, noCategory, guessedAccount, noAccount, matched, transferReady)

        fun shown(filter: CaptureFilter) = CaptureInbox.days(rows, filter, zone).flatMap { it.rows }.map { it.capture.id }.toSet()

        assertEquals(rows.map { it.capture.id }.toSet(), shown(CaptureFilter.ALL))
        assertEquals(setOf(ready, transferReady).map { it.capture.id }.toSet(), shown(CaptureFilter.READY))
        assertEquals(
            "a guessed Account or a Ledger match still needs the user",
            setOf(noCategory, guessedAccount, noAccount, matched).map { it.capture.id }.toSet(),
            shown(CaptureFilter.NEEDS_PICK)
        )
        assertEquals(
            "the 11th has nothing Ready, so its header goes too",
            listOf("2026-11-12", "2026-11-10").map(LocalDate::parse),
            CaptureInbox.days(rows, CaptureFilter.READY, zone).map { it.date }
        )
        assertEquals(2, CaptureInbox.count(rows, CaptureFilter.READY))
        assertEquals(4, CaptureInbox.count(rows, CaptureFilter.NEEDS_PICK))
    }

    @Test
    fun aFilteredDayCountsAndTotalsOnlyWhatItShows() {
        val rows = listOf(row(at("2026-11-12", 13), amount = 80.0), row(at("2026-11-12", 14), amount = 300.0, category = null))

        val day = CaptureInbox.days(rows, CaptureFilter.READY, zone).single()

        assertEquals(1, day.count)
        assertEquals(80.0, day.spent, 0.0)
    }

    // --- the bulk sheet ---

    @Test
    fun confirmDayTicksHighRowsListsReadyCheckRowsUntickedAndLeavesTheRestOut() {
        val high = row(at("2026-11-12", 9), band = ConfidenceBand.HIGH)
        val check = row(at("2026-11-12", 10), band = ConfidenceBand.CHECK)
        val low = row(at("2026-11-12", 11), band = ConfidenceBand.LOW)
        val checkGuessedAccount = row(at("2026-11-12", 12), band = ConfidenceBand.CHECK, accountIsGuess = true)
        val highMatched = row(at("2026-11-12", 13), band = ConfidenceBand.HIGH, match = logged)
        val otherDay = row(at("2026-11-11", 9), band = ConfidenceBand.HIGH)

        val days = CaptureInbox.days(listOf(high, check, low, checkGuessedAccount, highMatched, otherDay), CaptureFilter.ALL, zone)
        val sheet = days.first().bulk

        assertEquals(listOf(check.capture.id to false, high.capture.id to true), sheet.map { it.row.capture.id to it.tickedAtStart })
        assertEquals("only the High row is ticked as it opens", listOf(high), CaptureInbox.ticked(sheet, emptySet()))
    }

    @Test
    fun confirmDayOffersTheWholeDayWhateverTheFilterShows() {
        val high = row(at("2026-11-12", 9), band = ConfidenceBand.HIGH)
        val needsPick = row(at("2026-11-12", 10), category = null, band = ConfidenceBand.LOW)

        val day = CaptureInbox.days(listOf(high, needsPick), CaptureFilter.NEEDS_PICK, zone).single()

        assertEquals(listOf(needsPick), day.rows)
        assertEquals(listOf(high), day.bulk.map { it.row })
    }

    @Test
    fun tickingAndUntickingChangesOnlyThoseRows() {
        val high = row(at("2026-11-12", 9), band = ConfidenceBand.HIGH)
        val check = row(at("2026-11-12", 10))
        val sheet = CaptureInbox.bulkSheet(listOf(high, check))

        assertEquals(listOf(high, check), CaptureInbox.ticked(sheet, setOf(check.capture.id)))
        assertEquals(listOf(check), CaptureInbox.ticked(sheet, setOf(high.capture.id, check.capture.id)))
        assertTrue(CaptureInbox.ticked(sheet, setOf(high.capture.id)).isEmpty())
    }

    @Test
    fun confirmationsCarryWhatEachTickedRowSaves() {
        val high = row(at("2026-11-12", 9), band = ConfidenceBand.HIGH, amount = 80.0)
        val missing = row(at("2026-11-12", 10), category = null)

        val confirmations = CaptureInbox.confirmations(listOf(high, missing))

        assertEquals(listOf(high.capture.id), confirmations.map { it.capture.id })
        assertEquals("Food", confirmations.single().edits.category)
        assertEquals("Lunch", confirmations.single().title)
    }

    // --- one expanded row ---

    @Test
    fun tappingARowExpandsItAndCollapsesAnyOther() {
        var expanded: Long? = null
        expanded = CaptureInbox.toggleExpanded(expanded, 1)
        assertEquals(1L, expanded)
        expanded = CaptureInbox.toggleExpanded(expanded, 2)
        assertEquals("one row at a time", 2L, expanded)
        expanded = CaptureInbox.toggleExpanded(expanded, 2)
        assertNull(expanded)
    }

    // --- TalkBack ---

    @Test
    fun aCompactRowReadsAsOneSentence() {
        val lunch = row(at("2026-11-12", 13), amount = 80.0)

        assertEquals("Lunch, ₹80, Food, HDFC, check, 12 November", CaptureInbox.spokenLabel(lunch, "HDFC", null, zone))
    }

    @Test
    fun theSentenceSaysWhatsMissingGuessedOrPossiblyLogged() {
        val missing = row(at("2026-11-12", 13), amount = 80.0, category = null, accountId = null, band = ConfidenceBand.LOW)
        assertEquals(
            "Lunch, ₹80, needs a category, needs an account, low confidence, 12 November",
            CaptureInbox.spokenLabel(missing, null, null, zone)
        )

        val guessed = row(at("2026-11-12", 13), amount = 80.0, accountIsGuess = true, match = logged, band = ConfidenceBand.HIGH)
        assertEquals(
            "Lunch, ₹80, Food, suggested account HDFC, high confidence, possibly already logged, 12 November",
            CaptureInbox.spokenLabel(guessed, "HDFC", null, zone)
        )

        val salary = row(at("2026-11-12", 9), amount = 5_000.0, direction = "CREDIT", category = "Salary", title = "ACME")
        assertEquals("ACME, ₹5,000 received, Salary, HDFC, check, 12 November", CaptureInbox.spokenLabel(salary, "HDFC", null, zone))

        val transfer = row(at("2026-11-12", 9), type = TransactionType.TRANSFER, category = null, otherAccountId = CUB, title = "CUB")
        assertEquals("CUB, ₹100, Transfer to CUB Savings, HDFC, check, 12 November", CaptureInbox.spokenLabel(transfer, "HDFC", "CUB Savings", zone))
        val noOther = row(at("2026-11-12", 9), type = TransactionType.TRANSFER, category = null, title = "CUB")
        assertFalse(noOther.isReady)
        assertEquals(
            "CUB, ₹100, Transfer, needs the To account, HDFC, check, 12 November",
            CaptureInbox.spokenLabel(noOther, "HDFC", null, zone)
        )
    }

    private companion object {
        const val HDFC = 1L
        const val CUB = 2L
    }
}
