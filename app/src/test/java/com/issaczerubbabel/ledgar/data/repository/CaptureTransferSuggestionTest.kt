package com.issaczerubbabel.ledgar.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.capture.CaptureConfirmer
import com.issaczerubbabel.ledgar.capture.CaptureIngestor
import com.issaczerubbabel.ledgar.capture.CaptureSettingsSource
import com.issaczerubbabel.ledgar.capture.CaptureSource
import com.issaczerubbabel.ledgar.capture.CaptureTrace
import com.issaczerubbabel.ledgar.capture.IngestResult
import com.issaczerubbabel.ledgar.capture.RoomCaptureHistorySource
import com.issaczerubbabel.ledgar.capture.categorize.CategorizationPipeline
import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.capture.categorize.KeywordDictionary
import com.issaczerubbabel.ledgar.capture.notify.CaptureActionHandler
import com.issaczerubbabel.ledgar.capture.notify.CaptureNotifier
import com.issaczerubbabel.ledgar.capture.pair.LedgerMatchFinder
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountAlias
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleOrigin
import com.issaczerubbabel.ledgar.data.preferences.ReminderPreferences
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.viewmodel.CaptureInbox
import com.issaczerubbabel.ledgar.viewmodel.CaptureRowEdit
import com.issaczerubbabel.ledgar.viewmodel.CaptureRowUi
import com.issaczerubbabel.ledgar.viewmodel.CaptureRows
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Suggested Transfers in To review and Transfers confirmed from the notification (#143): one row
 * for a debit and its credit, Confirm Transfer resolving both with one Undo, and Split.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CaptureTransferSuggestionTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var repo: CaptureRepositoryImpl
    private var hdfc = 0L
    private var cub = 0L
    private var card = 0L
    private var hash = 0
    private val hour = 60 * 60 * 1000L

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        repo = CaptureRepositoryImpl(
            db, db.captureDao(), db.merchantRuleDao(), db.accountAliasDao(), db.expenseDao(), db.unparsedAlertDao(), {}
        )
        hdfc = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "HDFC Savings", initialBalance = 0.0))
        cub = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "CUB Savings", initialBalance = 0.0))
        card = db.accountDao().insert(AccountRecord(groupName = "Card", accountName = "HDFC Credit Card", initialBalance = 0.0))
        db.accountAliasDao().upsert(AccountAlias("1111", hdfc))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun capture(
        direction: String,
        hint: String,
        accountId: Long?,
        amount: Double = 5_000.0,
        txnTime: Long = T0,
        merchant: String = if (direction == "CREDIT") "ISSAC" else "SELF"
    ): CapturedTransaction {
        val id = db.captureDao().insert(
            CapturedTransaction(
                sources = "NOTIF", sender = "Bank", rawText = "Rs.$amount $direction", rawHash = "hash-${hash++}",
                capturedAt = txnTime, txnTime = txnTime, amount = amount, direction = direction, channel = "IMPS",
                merchantRaw = merchant, merchantNorm = merchant, accountHint = hint, accountId = accountId,
                suggestedType = if (direction == "CREDIT") TransactionType.INCOME else TransactionType.EXPENSE
            )
        )
        return db.captureDao().getById(id)!!
    }

    /** To review's rows, as the screen's view model builds them. */
    private suspend fun rows(edits: Map<Long, CaptureRowEdit> = emptyMap()): List<CaptureRowUi> =
        CaptureInbox.withTransferSuggestions(
            db.captureDao().getPendingSnapshot().map { CaptureRows.build(it, edits[it.id], emptyMap(), setOf(hdfc, cub, card)) }
        )

    private suspend fun status(c: CapturedTransaction) = db.captureDao().getById(c.id)!!.status

    private fun TestScope.confirmer(actions: UndoableActions) = CaptureConfirmer(
        repo, actions,
        CaptureNotifier(ApplicationProvider.getApplicationContext(), db.captureDao(), db.accountDao(), repo),
        ReminderPreferences(ApplicationProvider.getApplicationContext())
    )

    // --- To review ---

    @Test
    fun anAliasedDebitAndAnUnknownCreditOnTheSameDayShowAsOneSuggestedTransferHdfcToUnknown() = runBlocking {
        val debit = capture("DEBIT", "1111", hdfc)
        val credit = capture("CREDIT", "9999", null, txnTime = T0 + 2 * hour)

        val shown = rows()

        val row = shown.single()
        assertEquals(debit.id, row.capture.id)
        assertEquals(credit.id, row.transferPartner!!.capture.id)
        assertEquals(hdfc, row.accountId)
        assertNull("the To side is still to pick", row.transferPartner!!.accountId)
        assertFalse(row.canConfirm)
        assertFalse(row.isReady)
        assertEquals("Suggested transfer, ₹5,000 from HDFC Savings to an account still to pick, ${spokenDay()}",
            CaptureInbox.spokenTransferSuggestion(row, "HDFC Savings", null, java.time.ZoneId.systemDefault()))
    }

    @Test
    fun withNeitherSideAliasedTheyStayTwoCaptures() = runBlocking {
        capture("DEBIT", "8888", null)
        capture("CREDIT", "9999", null)

        val shown = rows()

        assertEquals(2, shown.size)
        assertTrue(shown.none { it.isTransferSuggestion })
    }

    @Test
    fun confirmingTheSuggestionWritesOneTransferResolvesBothAndLearnsTheCreditsDigits() = runBlocking {
        val debit = capture("DEBIT", "1111", hdfc)
        val credit = capture("CREDIT", "9999", null, txnTime = T0 + hour)
        val row = rows(mapOf(credit.id to CaptureRowEdit(accountId = cub))).single()
        assertTrue(row.canConfirm)

        val expenseId = repo.confirm(debit.id, row.toEdits()!!)!!

        val transfer = db.expenseDao().getById(expenseId)!!
        assertEquals(TransactionType.TRANSFER, transfer.type)
        assertEquals(hdfc, transfer.fromAccountId)
        assertEquals(cub, transfer.toAccountId)
        assertEquals(1, db.expenseDao().getAllRecordsSnapshot().size)
        assertEquals("CONFIRMED", status(debit))
        assertEquals("PAIRED", status(credit))
        assertEquals(expenseId, db.captureDao().getById(credit.id)!!.confirmedExpenseId)
        assertEquals("the credit's digits are CUB's now", cub, db.accountAliasDao().getByAlias("9999")!!.accountId)
    }

    @Test
    fun oneUndoBringsBackBothCapturesAndWritesNothing() = runTest {
        val actions = UndoableActions(db.expenseDao(), backgroundScope)
        val debit = capture("DEBIT", "1111", hdfc)
        val credit = capture("CREDIT", "9999", null, txnTime = T0 + hour)
        val row = rows(mapOf(credit.id to CaptureRowEdit(accountId = cub))).single()

        confirmer(actions).confirm(CaptureInbox.confirmations(listOf(row)), mapOf(cub to "CUB Savings"))
        val action = actions.pending.value!!
        assertEquals("Transfer saved, and the matching CUB Savings credit too", action.message)
        assertEquals(setOf(debit.id, credit.id), actions.hiddenCaptureIds.value)

        actions.undo(action)
        advanceUntilIdle()

        assertEquals(listOf("PENDING", "PENDING"), listOf(status(debit), status(credit)))
        assertTrue(db.expenseDao().getAllRecordsSnapshot().isEmpty())
        assertNull("no alias is learned until it's final", db.accountAliasDao().getByAlias("9999"))
        assertEquals("the suggestion is back as one row", 1, rows().size)
    }

    @Test
    fun onceFinalTheSuggestionIsOneTransferForBoth() = runTest {
        val actions = UndoableActions(db.expenseDao(), backgroundScope)
        val debit = capture("DEBIT", "1111", hdfc)
        val credit = capture("CREDIT", "9999", null, txnTime = T0 + hour)
        val row = rows(mapOf(credit.id to CaptureRowEdit(accountId = cub))).single()

        confirmer(actions).confirm(CaptureInbox.confirmations(listOf(row)), mapOf(cub to "CUB Savings"))
        advanceTimeBy(UndoableActions.UNDO_WINDOW_MS + 1)
        advanceUntilIdle()

        assertEquals("CONFIRMED", status(debit))
        assertEquals("PAIRED", status(credit))
        assertEquals(1, db.expenseDao().getAllRecordsSnapshot().size)
    }

    @Test
    fun splitShowsTwoCapturesAgainAndTheyAreNeverSuggestedAgain() = runBlocking {
        val debit = capture("DEBIT", "1111", hdfc)
        val credit = capture("CREDIT", "9999", null)

        repo.splitTransferSuggestion(debit.id, credit.id)

        val shown = rows()
        assertEquals(2, shown.size)
        assertTrue(shown.none { it.isTransferSuggestion })
        assertEquals(listOf("PENDING", "PENDING"), listOf(status(debit), status(credit)))
        assertTrue(CaptureTrace.parse(db.captureDao().getById(credit.id)!!.traceJson).splitFromTransfer)
    }

    @Test
    fun aSuggestionStaysOutOfTheBulkSheetAndTheDaysTotals() = runBlocking {
        capture("DEBIT", "1111", hdfc)
        val credit = capture("CREDIT", "2222", null)
        val shown = rows(mapOf(credit.id to CaptureRowEdit(accountId = cub)))

        assertTrue(CaptureInbox.bulkSheet(shown).isEmpty())
        val day = CaptureInbox.days(shown, com.issaczerubbabel.ledgar.viewmodel.CaptureFilter.ALL, java.time.ZoneId.systemDefault()).single()
        assertEquals(0.0, day.spent, 0.0)
        assertEquals(0.0, day.received, 0.0)
    }

    // --- the notification ---

    private val settings = object : CaptureSettingsSource {
        override suspend fun isEnabled() = true
        override suspend fun categoryMapping() = emptyMap<String, String>()
    }

    private fun ingestor() = CaptureIngestor(
        db.captureDao(), db.accountAliasDao(), DropdownOptionRepositoryImpl(db.dropdownOptionDao()),
        CategorizationPipeline(KeywordDictionary(emptyMap())) { db.merchantRuleDao().get(it) }, settings, db.unparsedAlertDao(),
        RoomCaptureHistorySource(db.expenseDao(), db.accountAliasDao()), LedgerMatchFinder(db.expenseDao(), db.captureDao())
    )

    private val credAlert = "Sent Rs.5000.00\nFrom HDFC Bank A/C *1111\nTo CRED\nOn 14/11/26\nRef 66353123456788\nNot You?"

    private suspend fun transferRule(toAccountId: Long, origin: String = MerchantRuleOrigin.USER) = db.merchantRuleDao().upsert(
        MerchantRule(
            "CRED", "DEBIT", TransactionType.TRANSFER, TransactionType.TRANSFER, origin = origin, updatedAt = 0,
            toAccountId = toAccountId
        )
    )

    @Test
    fun aHighTransferRuleWithAKnownAccountPostsTheNotification() = runBlocking {
        transferRule(card)

        val result = ingestor().ingest(CaptureSource.NOTIFICATION, "HDFC Bank", credAlert, nowMillis = NOV_14_EVENING) as IngestResult.Captured

        assertEquals(ConfidenceBand.HIGH, result.band)
        assertTrue(result.shouldNotify)
        val captured = db.captureDao().getById(result.id)!!
        assertEquals(TransactionType.TRANSFER, captured.suggestedType)
        assertEquals(card, captured.suggestedToAccountId)
    }

    @Test
    fun aTransferRuleToTheCapturesOwnAccountOrAnUnknownAccountDoesNotNotify() = runBlocking {
        transferRule(hdfc)
        val own = ingestor().ingest(CaptureSource.NOTIFICATION, "HDFC Bank", credAlert, nowMillis = NOV_14_EVENING) as IngestResult.Captured
        assertFalse(own.shouldNotify)

        transferRule(card)
        val unknownDigits = credAlert.replace("*1111", "*7777").replace("66353123456788", "66353123456789")
        val unknown = ingestor().ingest(CaptureSource.NOTIFICATION, "HDFC Bank", unknownDigits, nowMillis = NOV_14_EVENING) as IngestResult.Captured
        assertFalse("the capture's own Account isn't known", unknown.shouldNotify)
    }

    @Test
    fun theNotificationsConfirmWritesTheTransferAndPairsItsOtherHalf() = runBlocking {
        transferRule(card)
        db.accountAliasDao().upsert(AccountAlias("4444", card))
        val result = ingestor().ingest(CaptureSource.NOTIFICATION, "HDFC Bank", credAlert, nowMillis = NOV_14_EVENING) as IngestResult.Captured
        val captured = db.captureDao().getById(result.id)!!
        val cardCredit = capture("CREDIT", "4444", card, txnTime = captured.txnTime + hour)
        val handler = CaptureActionHandler(db.captureDao(), repo, ReminderPreferences(ApplicationProvider.getApplicationContext()))

        assertTrue(handler.confirm(result.id))

        val expenseId = db.captureDao().getById(result.id)!!.confirmedExpenseId
        assertNotNull(expenseId)
        val transfer = db.expenseDao().getById(expenseId!!)!!
        assertEquals(TransactionType.TRANSFER, transfer.type)
        assertEquals(hdfc, transfer.fromAccountId)
        assertEquals(card, transfer.toAccountId)
        assertEquals(5_000.0, transfer.amount, 0.0)
        assertEquals("the same pairing as the inbox", "PAIRED", status(cardCredit))
        assertEquals(1, db.expenseDao().getAllRecordsSnapshot().size)
    }

    private fun spokenDay(): String = java.time.Instant.ofEpochMilli(T0).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM"))

    private companion object {
        const val T0 = 1_760_000_000_000L

        /** 14 Nov 2026, 8pm IST: the alerts say 14/11/26. */
        const val NOV_14_EVENING = 1_794_667_800_000L
    }
}
