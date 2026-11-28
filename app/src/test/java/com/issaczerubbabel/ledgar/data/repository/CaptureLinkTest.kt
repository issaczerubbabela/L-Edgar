package com.issaczerubbabel.ledgar.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.capture.CaptureConfirmer
import com.issaczerubbabel.ledgar.capture.CaptureIngestor
import com.issaczerubbabel.ledgar.capture.CaptureSettingsSource
import com.issaczerubbabel.ledgar.capture.CaptureSource
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
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleOrigin
import com.issaczerubbabel.ledgar.data.preferences.ReminderPreferences
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.viewmodel.CaptureRows
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.ZoneId

/** Same one (#125): a capture for a Transaction already logged links to it instead of adding it twice. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CaptureLinkTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var repo: CaptureRepositoryImpl
    private var hdfc = 0L
    private var cub = 0L
    private var hash = 0
    private var reguessed = 0

    private val today: String get() = Instant.ofEpochMilli(T0).atZone(ZoneId.systemDefault()).toLocalDate().toString()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        repo = CaptureRepositoryImpl(
            db, db.captureDao(), db.merchantRuleDao(), db.accountAliasDao(), db.expenseDao(), db.unparsedAlertDao(), { reguessed++ }
        )
        hdfc = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "HDFC Savings", initialBalance = 0.0))
        cub = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "CUB Savings", initialBalance = 0.0))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun capture(
        amount: Double = 450.0,
        accountId: Long? = hdfc,
        hint: String? = "1234",
        merchantNorm: String? = "SWIGGY",
        status: String = "PENDING"
    ): CapturedTransaction {
        val id = db.captureDao().insert(
            CapturedTransaction(
                sources = "NOTIF", sender = "HDFC Bank", rawText = "Sent Rs.$amount", rawHash = "hash-${hash++}",
                capturedAt = T0, txnTime = T0, amount = amount, direction = "DEBIT", channel = "UPI",
                merchantRaw = merchantNorm, merchantNorm = merchantNorm, accountHint = hint, accountId = accountId,
                suggestedType = TransactionType.EXPENSE, suggestedCategory = "Groceries", confidence = 0.98, status = status
            )
        )
        return db.captureDao().getById(id)!!
    }

    /** A Transaction typed in by hand: Add Transaction, Quick Add or a Repeating transaction look the same. */
    private suspend fun logged(
        amount: Double = 450.0,
        accountId: Long? = hdfc,
        category: String = "Food",
        syncAction: String = "INSERT"
    ): ExpenseRecord {
        val id = db.expenseDao().insert(
            ExpenseRecord(
                date = today, type = TransactionType.EXPENSE, category = category, description = "Dinner",
                amount = amount, accountId = accountId, remarks = "", syncAction = syncAction
            )
        )
        return db.expenseDao().getById(id)!!
    }

    private suspend fun status(c: CapturedTransaction) = db.captureDao().getById(c.id)!!.status

    // --- the repository's link ---

    @Test
    fun sameOneAddsNoTransactionLeavesTheLoggedOneAloneAndMarksTheCaptureLinked() = runBlocking {
        val swiggy = logged()
        val alert = capture()

        assertTrue(repo.link(alert.id, swiggy.id))

        assertEquals("no second Transaction", listOf(swiggy), db.expenseDao().getAllRecordsSnapshot())
        val linked = db.captureDao().getById(alert.id)!!
        assertEquals("LINKED", linked.status)
        assertEquals(swiggy.id, linked.confirmedExpenseId)
        assertTrue(repo.observePending().first().isEmpty())
        assertEquals("a link sharpens the waiting guesses like a Confirm", 1, reguessed)
    }

    @Test
    fun aRefusedLinkDoesNotReguess() = runBlocking {
        assertFalse(repo.link(capture().id, 9_999))
        assertEquals(0, reguessed)
    }

    @Test
    fun aTransactionLinkedOrConfirmedFromOneCaptureIsNeverOfferedForASecond() = runBlocking {
        val swiggy = logged()
        val first = capture()
        val second = capture()
        assertEquals(swiggy.id, repo.findLedgerMatch(second.id)?.id)

        repo.link(first.id, swiggy.id)

        assertNull(repo.findLedgerMatch(second.id))
        assertFalse("the race: a second link to the same Transaction is refused", repo.link(second.id, swiggy.id))
        assertEquals("PENDING", status(second))

        // A Transaction made by a Confirm has absorbed its alert too.
        val confirmedId = repo.confirm(capture(amount = 99.0).id, CaptureEdits(TransactionType.EXPENSE, "Food", hdfc, "Tea"))!!
        val next = capture(amount = 99.0)
        assertNull(repo.findLedgerMatch(next.id))
        assertFalse(repo.link(next.id, confirmedId))
    }

    @Test
    fun linkReChecksTheCaptureAndTheTransactionBeforeSaving() = runBlocking {
        val swiggy = logged()
        val alreadyDismissed = capture(status = "DISMISSED")
        assertFalse(repo.link(alreadyDismissed.id, swiggy.id))

        val alert = capture()
        val deleted = logged().also { db.expenseDao().markTransactionDeletedById(it.id) }
        assertFalse("waiting to be deleted", repo.link(alert.id, deleted.id))

        val edited = logged().also { db.expenseDao().update(it.copy(amount = 451.0)) }
        assertFalse("its amount changed since the card showed it", repo.link(alert.id, edited.id))

        assertFalse("not a Transaction at all", repo.link(alert.id, 9_999))
        assertEquals("PENDING", status(alert))
    }

    @Test
    fun aCaptureOnAnotherAccountOrForAnotherAmountHasNoMatch() = runBlocking {
        logged(accountId = cub)
        logged(amount = 450.5)
        logged(syncAction = "DELETE")

        assertNull(repo.findLedgerMatch(capture().id))
    }

    @Test
    fun sameOneTeachesTheMerchantRuleAndAccountAliasLikeConfirm() = runBlocking {
        val alert = capture(accountId = null, hint = "1234")
        val waitingSameDigits = capture(amount = 10.0, accountId = null, hint = "1234")
        val swiggy = logged(accountId = hdfc, category = "Food")

        repo.link(alert.id, swiggy.id)

        assertEquals(hdfc, db.accountAliasDao().getByAlias("1234")!!.accountId)
        assertEquals("waiting captures with those digits pick the Account up", hdfc, db.captureDao().getById(waitingSameDigits.id)!!.accountId)
        val rule = db.merchantRuleDao().get("SWIGGY", "DEBIT")!!
        assertEquals("Food", rule.category)
        assertEquals(TransactionType.EXPENSE, rule.type)
        assertEquals(MerchantRuleOrigin.CANDIDATE, rule.origin)
        assertEquals(1, rule.hitCount)
        assertEquals("Food", db.captureDao().getById(alert.id)!!.finalCategory)
    }

    @Test
    fun sameOneKeepsTheMerchantsRenameInsteadOfTakingTheHandTypedDescription() = runBlocking {
        db.merchantRuleDao().upsert(
            MerchantRule("SWIGGY", "DEBIT", "Food", TransactionType.EXPENSE, origin = MerchantRuleOrigin.CANDIDATE, hitCount = 1, streak = 1, updatedAt = 0, description = "Swiggy")
        )

        repo.link(capture().id, logged().id)

        val rule = db.merchantRuleDao().get("SWIGGY", "DEBIT")!!
        assertEquals("Swiggy", rule.description)
        assertEquals(2, rule.hitCount)
    }

    // --- Undo ---

    private fun TestScope.confirmer(actions: UndoableActions) = CaptureConfirmer(
        repo, actions,
        CaptureNotifier(ApplicationProvider.getApplicationContext(), db.captureDao(), db.accountDao(), repo),
        ReminderPreferences(ApplicationProvider.getApplicationContext())
    )

    @Test
    fun undoWithinFiveSecondsLeavesTheCaptureWaiting() = runTest {
        val actions = UndoableActions(db.expenseDao(), backgroundScope)
        val swiggy = logged()
        val alert = capture()

        confirmer(actions).link(alert, swiggy)

        val action = actions.pending.value!!
        assertTrue(action.message.startsWith("Linked to Dinner, "))
        assertEquals(setOf(alert.id), actions.hiddenCaptureIds.value)
        actions.undo(action)
        advanceUntilIdle()

        assertEquals("PENDING", status(alert))
        assertTrue(actions.hiddenCaptureIds.value.isEmpty())
        assertEquals(listOf(swiggy), db.expenseDao().getAllRecordsSnapshot())
    }

    @Test
    fun onceFinalTheCaptureIsLinkedAndTheTransactionUntouched() = runTest {
        val actions = UndoableActions(db.expenseDao(), backgroundScope)
        val swiggy = logged()
        val alert = capture()

        confirmer(actions).link(alert, swiggy)
        advanceTimeBy(UndoableActions.UNDO_WINDOW_MS + 1)
        advanceUntilIdle()

        assertEquals("LINKED", status(alert))
        val after = db.expenseDao().getById(swiggy.id)!!
        assertEquals("unchanged, so nothing new to Sync", swiggy, after)
    }

    // --- ingest and the notification ---

    private val settings = object : CaptureSettingsSource {
        override suspend fun isEnabled() = true
        override suspend fun categoryMapping() = emptyMap<String, String>()
    }

    private fun ingestor() = CaptureIngestor(
        db, db.captureDao(), db.accountAliasDao(), DropdownOptionRepositoryImpl(db.dropdownOptionDao()),
        CategorizationPipeline(KeywordDictionary(emptyMap())) { db.merchantRuleDao().get(it) }, settings, db.unparsedAlertDao(),
        RoomCaptureHistorySource(db.expenseDao(), db.accountAliasDao()), LedgerMatchFinder(db.expenseDao(), db.captureDao())
    )

    private val hdfcAlert = "Sent Rs.450.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 14/11/26\nRef 66353123456788\nNot You?"

    private suspend fun highConfidenceSwiggy() {
        db.accountAliasDao().upsert(AccountAlias("1234", hdfc))
        // A rule only counts while its Category is one of the user's (#158).
        db.dropdownOptionDao().insert(DropdownOption(optionType = TransactionType.EXPENSE_CATEGORY_OPTION, name = "Food", displayOrder = 0))
        db.merchantRuleDao().upsert(
            MerchantRule("SWIGGY", "DEBIT", "Food", TransactionType.EXPENSE, origin = MerchantRuleOrigin.USER, updatedAt = 0)
        )
    }

    private suspend fun loggedOn(date: String) = db.expenseDao().insert(
        ExpenseRecord(date = date, type = TransactionType.EXPENSE, category = "Food", description = "Swiggy", amount = 450.0, accountId = hdfc, remarks = "")
    )

    @Test
    fun anAlertForAPaymentAlreadyLoggedWaitsAsAPossibleDuplicateWithoutANotification() = runBlocking {
        highConfidenceSwiggy()
        loggedOn("2026-11-14")

        val result = ingestor().ingest(CaptureSource.NOTIFICATION, "HDFC Bank", hdfcAlert, nowMillis = NOV_14_EVENING) as IngestResult.Captured

        assertEquals(ConfidenceBand.HIGH, result.band)
        assertFalse(result.shouldNotify)
        assertEquals("POSSIBLE_DUPLICATE", db.captureDao().getById(result.id)!!.status)
        assertEquals("it still waits in To review and counts", 1, repo.observePendingCount().first())
    }

    @Test
    fun withoutALoggedMatchTheSameAlertNotifiesAsBefore() = runBlocking {
        highConfidenceSwiggy()
        loggedOn("2026-11-09")

        val result = ingestor().ingest(CaptureSource.NOTIFICATION, "HDFC Bank", hdfcAlert, nowMillis = NOV_14_EVENING) as IngestResult.Captured

        assertTrue(result.shouldNotify)
        assertEquals("PENDING", db.captureDao().getById(result.id)!!.status)
    }

    @Test
    fun theNotificationsConfirmDoesNothingOnceTheCaptureHasGainedAMatch() = runBlocking {
        highConfidenceSwiggy()
        val result = ingestor().ingest(CaptureSource.NOTIFICATION, "HDFC Bank", hdfcAlert, nowMillis = NOV_14_EVENING) as IngestResult.Captured
        assertTrue(result.shouldNotify)
        // Logged by hand after the notification was posted.
        loggedOn("2026-11-14")
        val handler = CaptureActionHandler(db.captureDao(), repo, ReminderPreferences(ApplicationProvider.getApplicationContext()))

        assertFalse(handler.confirm(result.id))

        assertEquals("PENDING", db.captureDao().getById(result.id)!!.status)
        assertEquals(1, db.expenseDao().getAllRecordsSnapshot().size)
    }

    // --- the inbox ---

    @Test
    fun aMatchedCardIsLeftOutOfReviewAllAndAddAnywayIsAnOrdinaryConfirm() = runBlocking {
        val swiggy = logged()
        val alert = capture()
        val candidates = repo.observeLedgerCandidates().first()

        val card = CaptureRows.build(alert, null, emptyMap(), setOf(hdfc, cub), null, match = candidates.bestMatch(alert))
        assertEquals(swiggy, card.match)
        assertEquals(ConfidenceBand.HIGH, card.band)
        assertTrue(card.canConfirm)
        assertFalse(card.isBulkCandidate)

        // Add anyway is today's Confirm: a second Transaction, on purpose.
        val added = repo.confirm(alert.id, card.toEdits()!!)!!
        assertEquals(2, db.expenseDao().getAllRecordsSnapshot().size)
        assertEquals("CONFIRMED", status(alert))
        assertEquals(added, db.captureDao().getById(alert.id)!!.confirmedExpenseId)
    }

    @Test
    fun savingATransactionByHandGivesAWaitingCaptureItsMatchLive() = runBlocking {
        val alert = capture()
        assertNull(repo.observeLedgerCandidates().first().bestMatch(alert))

        val swiggy = logged()

        assertEquals(swiggy, repo.observeLedgerCandidates().first().bestMatch(alert))
    }

    private companion object {
        /** 14 Nov 2026, 12:00 UTC. */
        const val T0 = 1_794_657_600_000L

        /** 14 Nov 2026, 20:00 in India. */
        val NOV_14_EVENING: Long = java.time.LocalDateTime.of(2026, 11, 14, 20, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
