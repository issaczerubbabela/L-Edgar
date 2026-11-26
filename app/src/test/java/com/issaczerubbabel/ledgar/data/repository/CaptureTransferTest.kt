package com.issaczerubbabel.ledgar.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.capture.CaptureConfirmation
import com.issaczerubbabel.ledgar.capture.CaptureConfirmer
import com.issaczerubbabel.ledgar.capture.categorize.CategorizationPipeline
import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.capture.categorize.KeywordDictionary
import com.issaczerubbabel.ledgar.capture.notify.CaptureNotifier
import com.issaczerubbabel.ledgar.capture.pair.TransferPairing
import com.issaczerubbabel.ledgar.capture.parse.Channel
import com.issaczerubbabel.ledgar.capture.parse.Direction
import com.issaczerubbabel.ledgar.capture.parse.ParsedTxn
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountAlias
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Transfers from To review: the bank's direction, pairing the other half (ADR-0006), Undo and learning. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CaptureTransferTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var repo: CaptureRepositoryImpl
    private var hdfc = 0L
    private var cub = 0L
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
            db, db.captureDao(), db.merchantRuleDao(), db.accountAliasDao(), db.expenseDao(), db.unparsedAlertDao()
        )
        hdfc = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "HDFC Savings", initialBalance = 0.0))
        cub = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "CUB Savings", initialBalance = 0.0))
        db.accountAliasDao().upsert(AccountAlias("1111", hdfc))
        db.accountAliasDao().upsert(AccountAlias("2222", cub))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun capture(
        direction: String,
        hint: String,
        accountId: Long,
        amount: Double = 5_000.0,
        txnTime: Long = T0,
        merchantNorm: String? = "SELF TRANSFER"
    ): CapturedTransaction {
        val id = db.captureDao().insert(
            CapturedTransaction(
                sources = "NOTIF", sender = "Bank", rawText = "Rs.$amount $direction", rawHash = "hash-${hash++}",
                capturedAt = txnTime, txnTime = txnTime, amount = amount, direction = direction, channel = "IMPS",
                merchantRaw = merchantNorm, merchantNorm = merchantNorm, accountHint = hint, accountId = accountId,
                suggestedType = if (direction == "CREDIT") TransactionType.INCOME else TransactionType.EXPENSE
            )
        )
        return db.captureDao().getById(id)!!
    }

    private fun transferTo(otherAccountId: Long, accountId: Long = hdfc, alwaysUse: Boolean = false) = CaptureEdits(
        type = TransactionType.TRANSFER, category = "", accountId = accountId, description = "Self transfer",
        alwaysUse = alwaysUse, otherAccountId = otherAccountId
    )

    private suspend fun status(c: CapturedTransaction) = db.captureDao().getById(c.id)!!.status

    @Test
    fun aDebitSwitchedToTransferIsOneTransferFromItsAccountToTheOtherOne() = runBlocking {
        val debit = capture("DEBIT", "1111", hdfc)

        val expenseId = repo.confirm(debit.id, transferTo(cub))!!

        val record = db.expenseDao().getById(expenseId)!!
        assertEquals(TransactionType.TRANSFER, record.type)
        assertEquals(TransactionType.TRANSFER, record.category)
        assertEquals(hdfc, record.fromAccountId)
        assertEquals(cub, record.toAccountId)
        assertNull(record.accountId)
        assertEquals(5_000.0, record.amount, 0.0)
        assertTrue("unsynced, so Sync sends it like any Transaction", !record.isSynced)
        assertEquals(1, db.expenseDao().getAllRecordsSnapshot().size)
        assertEquals("CONFIRMED", status(debit))
    }

    @Test
    fun aCreditSwitchedToTransferKeepsItsAccountOnTheToSide() = runBlocking {
        val credit = capture("CREDIT", "2222", cub)

        val expenseId = repo.confirm(credit.id, transferTo(hdfc, accountId = cub))!!

        val record = db.expenseDao().getById(expenseId)!!
        assertEquals(hdfc, record.fromAccountId)
        assertEquals(cub, record.toAccountId)
    }

    @Test
    fun theWaitingCreditOnTheOtherAccountIsPairedWithTheTransfer() = runBlocking {
        val debit = capture("DEBIT", "1111", hdfc)
        val credit = capture("CREDIT", "2222", cub, txnTime = T0 + 20 * hour)

        assertEquals(TransferPairing.One(credit), repo.findTransferPair(debit.id, transferTo(cub)))
        val expenseId = repo.confirm(debit.id, transferTo(cub))!!

        val paired = db.captureDao().getById(credit.id)!!
        assertEquals("PAIRED", paired.status)
        assertEquals(expenseId, paired.confirmedExpenseId)
        assertEquals("the money is counted once", 1, db.expenseDao().getAllRecordsSnapshot().size)
        assertTrue(repo.observePending().first().isEmpty())
    }

    @Test
    fun twoPossibleOtherHalvesMeansNeitherIsPaired() = runBlocking {
        val debit = capture("DEBIT", "1111", hdfc)
        val first = capture("CREDIT", "2222", cub, txnTime = T0 + hour)
        val second = capture("CREDIT", "2222", cub, txnTime = T0 + 2 * hour)

        assertEquals(TransferPairing.Ambiguous(2), repo.findTransferPair(debit.id, transferTo(cub)))
        repo.confirm(debit.id, transferTo(cub))

        assertEquals("PENDING", status(first))
        assertEquals("PENDING", status(second))
    }

    @Test
    fun aCreditOutsideTwoDaysOrOnAnotherAccountIsLeftWaiting() = runBlocking {
        val debit = capture("DEBIT", "1111", hdfc)
        val late = capture("CREDIT", "2222", cub, txnTime = T0 + 3 * 24 * hour)
        val elsewhere = capture("CREDIT", "1111", hdfc, txnTime = T0 + hour)

        repo.confirm(debit.id, transferTo(cub))

        assertEquals("PENDING", status(late))
        assertEquals("PENDING", status(elsewhere))
    }

    @Test
    fun editingTheAmountDateAndRemarksWritesThem() = runBlocking {
        val debit = capture("DEBIT", "1111", hdfc, amount = 499.0)

        val expenseId = repo.confirm(
            debit.id,
            CaptureEdits(
                type = TransactionType.EXPENSE, category = "Food", accountId = hdfc, description = "Dinner",
                amount = 549.5, date = LocalDate.of(2026, 11, 20), remarks = "with tip"
            )
        )!!

        val record = db.expenseDao().getById(expenseId)!!
        assertEquals(549.5, record.amount, 0.0)
        assertEquals("2026-11-20", record.date)
        assertEquals("with tip", record.remarks)
        assertEquals(hdfc, record.fromAccountId)
    }

    @Test
    fun threeTransfersToCubLearnARuleAndTheNextCaptureIsAHighTransfer() = runBlocking {
        repeat(3) { repo.confirm(capture("DEBIT", "1111", hdfc, amount = 100.0 + it).id, transferTo(cub)) }

        val rule = db.merchantRuleDao().get("SELF TRANSFER", "DEBIT")!!
        assertEquals(MerchantRuleOrigin.LEARNED, rule.origin)
        assertEquals(TransactionType.TRANSFER, rule.type)
        assertEquals(cub, rule.toAccountId)

        val pipeline = CategorizationPipeline(KeywordDictionary(emptyMap())) { db.merchantRuleDao().get(it) }
        val next = pipeline.categorize(
            ParsedTxn(amount = 200.0, direction = Direction.DEBIT, merchantRaw = "SELF TRANSFER", accountHint = "1111", refNumber = null, channel = Channel.IMPS),
            emptyMap(), emptyList()
        )
        assertEquals(ConfidenceBand.HIGH, next.band)
        val card = CaptureRows.build(
            capture("DEBIT", "1111", hdfc).copy(
                suggestedType = next.type, suggestedToAccountId = next.transferAccountId, confidence = next.confidence
            ),
            edit = null, renames = emptyMap(), knownAccountIds = setOf(hdfc, cub), why = null
        )
        assertEquals("the switch starts on Transfer", TransactionType.TRANSFER, card.type)
        assertEquals(cub, card.otherAccountId)
        assertEquals(ConfidenceBand.HIGH, card.band)
        assertTrue(card.canConfirm)
    }

    @Test
    fun aDebitAndACreditFromOneMerchantKeepSeparateRules() = runBlocking {
        repo.confirm(capture("DEBIT", "1111", hdfc, merchantNorm = "JANE DOE").id, CaptureEdits(TransactionType.EXPENSE, "Food", hdfc, "Jane"))
        repo.confirm(capture("CREDIT", "1111", hdfc, merchantNorm = "JANE DOE").id, CaptureEdits(TransactionType.INCOME, "Refund", hdfc, "Jane"))

        assertEquals("Food", db.merchantRuleDao().get("JANE DOE", "DEBIT")!!.category)
        assertEquals("Refund", db.merchantRuleDao().get("JANE DOE", "CREDIT")!!.category)
    }

    private fun TestScope.confirmer(actions: UndoableActions) = CaptureConfirmer(
        repo, actions,
        CaptureNotifier(ApplicationProvider.getApplicationContext(), db.captureDao(), db.accountDao(), repo),
        ReminderPreferences(ApplicationProvider.getApplicationContext())
    )

    @Test
    fun undoBringsBackBothHalvesAndWritesNothing() = runTest {
        val actions = UndoableActions(db.expenseDao(), backgroundScope)
        val debit = capture("DEBIT", "1111", hdfc)
        val credit = capture("CREDIT", "2222", cub, txnTime = T0 + hour)

        confirmer(actions).confirm(listOf(CaptureConfirmation(debit, transferTo(cub), "Self transfer")), mapOf(cub to "CUB Savings"))

        val action = actions.pending.value!!
        assertEquals("Transfer saved, and the matching CUB Savings credit too", action.message)
        assertEquals(setOf(debit.id, credit.id), actions.hiddenCaptureIds.value)

        actions.undo(action)
        advanceUntilIdle()

        assertTrue(actions.hiddenCaptureIds.value.isEmpty())
        assertEquals("PENDING", status(debit))
        assertEquals("PENDING", status(credit))
        assertTrue(db.expenseDao().getAllRecordsSnapshot().isEmpty())
    }

    @Test
    fun onceFinalBothHalvesAreResolvedByOneTransfer() = runTest {
        val actions = UndoableActions(db.expenseDao(), backgroundScope)
        val debit = capture("DEBIT", "1111", hdfc)
        val credit = capture("CREDIT", "2222", cub, txnTime = T0 + hour)

        confirmer(actions).confirm(listOf(CaptureConfirmation(debit, transferTo(cub), "Self transfer")), mapOf(cub to "CUB Savings"))
        advanceTimeBy(UndoableActions.UNDO_WINDOW_MS + 1)
        advanceUntilIdle()

        assertEquals("CONFIRMED", status(debit))
        assertEquals("PAIRED", status(credit))
        assertEquals(1, db.expenseDao().getAllRecordsSnapshot().size)
    }

    @Test
    fun theSnackbarSaysWhenSeveralCouldBeTheOtherHalf() = runTest {
        val actions = UndoableActions(db.expenseDao(), backgroundScope)
        val debit = capture("DEBIT", "1111", hdfc)
        val first = capture("CREDIT", "2222", cub, txnTime = T0 + hour)
        val second = capture("CREDIT", "2222", cub, txnTime = T0 + 2 * hour)

        confirmer(actions).confirm(listOf(CaptureConfirmation(debit, transferTo(cub), "Self transfer")), mapOf(cub to "CUB Savings"))

        assertEquals("Transfer saved. 2 credits could be its other half, so they're still waiting", actions.pending.value!!.message)
        assertEquals("only the Transfer hides", setOf(debit.id), actions.hiddenCaptureIds.value)
        assertTrue(first.id != second.id)
    }

    private companion object {
        const val T0 = 1_760_000_000_000L
    }
}
