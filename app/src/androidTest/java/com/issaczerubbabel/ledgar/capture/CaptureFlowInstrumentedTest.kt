package com.issaczerubbabel.ledgar.capture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.issaczerubbabel.ledgar.capture.categorize.CategorizationPipeline
import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.capture.categorize.KeywordDictionary
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountAlias
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleOrigin
import com.issaczerubbabel.ledgar.data.repository.CaptureEdits
import com.issaczerubbabel.ledgar.data.repository.CaptureRepositoryImpl
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The real ingest -> confirm -> learn flow on device SQLite, against an in-memory database so the
 * app's own data is never touched. It runs the actual DAO queries the unit tests can't.
 */
@RunWith(AndroidJUnit4::class)
class CaptureFlowInstrumentedTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var ingestor: CaptureIngestor
    private lateinit var repository: CaptureRepositoryImpl
    private val settings = FakeSettings()
    private var accountId = 0L

    private class FakeSettings : CaptureSettingsSource {
        var enabled = true
        var mapping = mapOf("fuel" to "Fuel", "food_delivery" to "Food")
        override suspend fun isEnabled() = enabled
        override suspend fun categoryMapping() = mapping
    }

    private val hdfcP2p = "Sent Rs.70.00\nFrom HDFC Bank A/C *1234\nTo Mrs Jane Doe\nOn 26/09/26\nRef 66353123456788\nNot You?"
    private fun axisFuel(ref: String) =
        "INR 100.00 was debited from your A/c no. XX1236 on 19-12-24 at 10:19:54 IST via UPI/P2M/$ref/Shri Ujagar Fuels. Avail. Bal: INR 5,420.10."

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SheetSyncDatabase::class.java).allowMainThreadQueries().build()

        accountId = db.accountDao().insert(AccountRecord(groupName = "Accounts", accountName = "Axis Savings", initialBalance = 0.0))
        db.dropdownOptionDao().insertAll(
            listOf("Food", "Fuel", "Groceries").mapIndexed { i, n -> DropdownOption(optionType = "EXPENSE_CATEGORY", name = n, displayOrder = i) }
        )

        val pipeline = CategorizationPipeline(KeywordDictionary(mapOf("fuel" to listOf("fuels")))) {
            db.merchantRuleDao().getByMerchant(it)
        }
        ingestor = CaptureIngestor(
            db.captureDao(), db.accountAliasDao(), DropdownOptionRepositoryImpl(db.dropdownOptionDao()), pipeline, settings
        )
        repository = CaptureRepositoryImpl(db, db.captureDao(), db.merchantRuleDao(), db.accountAliasDao(), db.expenseDao())
    }

    @After
    fun tearDown() = db.close()

    private suspend fun pending() = db.captureDao().observePending().first()

    @Test
    fun capturesARealHdfcAlertAsPending() = runBlocking {
        val result = ingestor.ingest(CaptureSource.NOTIFICATION, "HDFC Bank", hdfcP2p) as IngestResult.Captured

        val row = pending().single()
        assertEquals(result.id, row.id)
        assertEquals(70.0, row.amount, 0.001)
        assertEquals("1234", row.accountHint)
        assertEquals("MRS JANE DOE", row.merchantNorm)
        assertEquals("PENDING", row.status)
        assertNull(row.accountId)
        assertFalse(result.shouldNotify)
        assertTrue(db.expenseDao().getAllRecordsSnapshot().isEmpty())
    }

    @Test
    fun theSameAlertTwiceIsOneCapture() = runBlocking {
        ingestor.ingest(CaptureSource.NOTIFICATION, "HDFC Bank", hdfcP2p)

        assertEquals(IngestResult.Duplicate, ingestor.ingest(CaptureSource.NOTIFICATION, "HDFC Bank", hdfcP2p))
        assertEquals(1, pending().size)
    }

    @Test
    fun identicalTextWithoutARefIsOnlyARepeatWithinTenMinutes() = runBlocking {
        val chai = "Paid ₹20 to Chai Wala."
        val t0 = 1_000_000_000L
        ingestor.ingest(CaptureSource.NOTIFICATION, "Google Pay", chai, t0)

        assertEquals(IngestResult.Duplicate, ingestor.ingest(CaptureSource.NOTIFICATION, "Google Pay", chai, t0 + 60_000))
        assertTrue(ingestor.ingest(CaptureSource.NOTIFICATION, "Google Pay", chai, t0 + 30 * 60_000) is IngestResult.Captured)
        assertEquals(2, pending().size)
    }

    @Test
    fun aTurnedOffCaptureStoresNothing() = runBlocking {
        settings.enabled = false

        assertTrue(ingestor.ingest(CaptureSource.NOTIFICATION, "HDFC Bank", hdfcP2p) is IngestResult.Ignored)
        assertTrue(pending().isEmpty())
    }

    @Test
    fun textThatIsNotATransactionIsReportedUnparsed() = runBlocking {
        assertEquals(IngestResult.Unparsed, ingestor.ingest(CaptureSource.NOTIFICATION, "Bank", "Your statement is ready."))
    }

    @Test
    fun aHighConfidenceCaptureWithAKnownAccountShouldNotify() = runBlocking {
        db.accountAliasDao().upsert(AccountAlias("1236", accountId))
        db.merchantRuleDao().upsert(
            MerchantRule("SHRI UJAGAR FUELS", "Fuel", "Expense", origin = MerchantRuleOrigin.USER, updatedAt = 0)
        )

        val result = ingestor.ingest(CaptureSource.NOTIFICATION, "AXISBK", axisFuel("435476373861")) as IngestResult.Captured

        assertEquals(ConfidenceBand.HIGH, result.band)
        assertTrue(result.shouldNotify)
        assertEquals(accountId, pending().single().accountId)
    }

    @Test
    fun aHighConfidenceCaptureWithAnUnknownAccountStaysQuiet() = runBlocking {
        db.merchantRuleDao().upsert(
            MerchantRule("SHRI UJAGAR FUELS", "Fuel", "Expense", origin = MerchantRuleOrigin.USER, updatedAt = 0)
        )

        val result = ingestor.ingest(CaptureSource.NOTIFICATION, "AXISBK", axisFuel("435476373861")) as IngestResult.Captured

        assertEquals(ConfidenceBand.HIGH, result.band)
        assertFalse(result.shouldNotify)
    }

    @Test
    fun aKeywordHitUsesTheMappedCategoryButOnlyAsCheck() = runBlocking {
        val result = ingestor.ingest(CaptureSource.NOTIFICATION, "AXISBK", axisFuel("435476373861")) as IngestResult.Captured

        assertEquals(ConfidenceBand.CHECK, result.band)
        assertEquals("Fuel", pending().single().suggestedCategory)
    }

    @Test
    fun confirmingCreatesAnUnsyncedTransactionAndLearnsTheAccount() = runBlocking {
        val id = (ingestor.ingest(CaptureSource.NOTIFICATION, "AXISBK", axisFuel("435476373861")) as IngestResult.Captured).id

        val expenseId = repository.confirm(id, CaptureEdits("Expense", "Fuel", accountId, "Ujagar Fuels"))!!

        val expense = db.expenseDao().getById(expenseId)!!
        assertEquals("Expense", expense.type)
        assertEquals("Fuel", expense.category)
        assertEquals(100.0, expense.amount, 0.001)
        assertEquals(accountId, expense.accountId)
        assertEquals(accountId, expense.fromAccountId)
        assertFalse(expense.isSynced)
        assertEquals("INSERT", expense.syncAction)

        val capture = db.captureDao().getById(id)!!
        assertEquals("CONFIRMED", capture.status)
        assertEquals(expenseId, capture.confirmedExpenseId)
        assertTrue(pending().isEmpty())
        assertEquals(accountId, db.accountAliasDao().getByAlias("1236")?.accountId)
    }

    @Test
    fun confirmingTwiceDoesNotCreateASecondTransaction() = runBlocking {
        val id = (ingestor.ingest(CaptureSource.NOTIFICATION, "AXISBK", axisFuel("435476373861")) as IngestResult.Captured).id
        repository.confirm(id, CaptureEdits("Expense", "Fuel", accountId, "x"))

        assertNull(repository.confirm(id, CaptureEdits("Expense", "Fuel", accountId, "x")))
        assertEquals(1, db.expenseDao().getAllRecordsSnapshot().size)
    }

    @Test
    fun threeConfirmationsEarnARuleThatMakesTheNextOneHighConfidence() = runBlocking {
        val refs = listOf("435476373861", "435476373862", "435476373863")
        refs.forEach { ref ->
            val id = (ingestor.ingest(CaptureSource.NOTIFICATION, "AXISBK", axisFuel(ref)) as IngestResult.Captured).id
            repository.confirm(id, CaptureEdits("Expense", "Fuel", accountId, "Ujagar Fuels"))
        }
        assertEquals(MerchantRuleOrigin.LEARNED, db.merchantRuleDao().getByMerchant("SHRI UJAGAR FUELS")!!.origin)

        val next = ingestor.ingest(CaptureSource.NOTIFICATION, "AXISBK", axisFuel("435476373864")) as IngestResult.Captured

        assertEquals(ConfidenceBand.HIGH, next.band)
        assertTrue(next.shouldNotify)
    }

    @Test
    fun turningCaptureOffClearsQueuedWorkButKeepsWhatWasLearned() = runBlocking {
        db.accountAliasDao().upsert(AccountAlias("1236", accountId))
        db.merchantRuleDao().upsert(
            MerchantRule("SHRI UJAGAR FUELS", "Fuel", "Expense", origin = MerchantRuleOrigin.USER, updatedAt = 0)
        )
        ingestor.ingest(CaptureSource.NOTIFICATION, "AXISBK", axisFuel("435476373861"))
        ingestor.ingest(CaptureSource.NOTIFICATION, "HDFC Bank", hdfcP2p)

        repository.clearPending()

        assertTrue(pending().isEmpty())
        assertNotNull(db.merchantRuleDao().getByMerchant("SHRI UJAGAR FUELS"))
        assertNotNull(db.accountAliasDao().getByAlias("1236"))
    }

    @Test
    fun confirmingTeachesEarlierCapturesOnTheSameAccountNumber() = runBlocking {
        val first = (ingestor.ingest(CaptureSource.NOTIFICATION, "HDFC Bank", hdfcP2p) as IngestResult.Captured).id
        val second = (ingestor.ingest(
            CaptureSource.NOTIFICATION, "HDFC Bank",
            "Sent Rs.80.00 From HDFC Bank A/C *1234 To Bob On 26/09/26 Ref 66353123456789"
        ) as IngestResult.Captured).id
        assertNull(db.captureDao().getById(second)!!.accountId)

        repository.confirm(first, CaptureEdits("Expense", "Food", accountId, "Mrs Jane Doe"))

        assertEquals(accountId, db.captureDao().getById(second)!!.accountId)
    }

    @Test
    fun mappingACategoryLaterFillsInCapturesThatWereWaiting() = runBlocking {
        settings.mapping = emptyMap()
        val id = (ingestor.ingest(CaptureSource.NOTIFICATION, "AXISBK", axisFuel("435476373861")) as IngestResult.Captured).id
        assertNull(db.captureDao().getById(id)!!.suggestedCategory)

        settings.mapping = mapOf("fuel" to "Fuel")
        ingestor.recategorizePending()

        assertEquals("Fuel", db.captureDao().getById(id)!!.suggestedCategory)
    }

    @Test
    fun recategorizingNeverChangesACategoryAlreadySuggested() = runBlocking {
        val id = (ingestor.ingest(CaptureSource.NOTIFICATION, "AXISBK", axisFuel("435476373861")) as IngestResult.Captured).id
        assertEquals("Fuel", db.captureDao().getById(id)!!.suggestedCategory)

        settings.mapping = mapOf("fuel" to "Groceries")
        ingestor.recategorizePending()

        assertEquals("Fuel", db.captureDao().getById(id)!!.suggestedCategory)
    }

    @Test
    fun alwaysUseMakesTheNextCaptureHighConfidenceAfterASingleConfirm() = runBlocking {
        val first = (ingestor.ingest(CaptureSource.NOTIFICATION, "AXISBK", axisFuel("435476373861")) as IngestResult.Captured).id
        repository.confirm(first, CaptureEdits("Expense", "Fuel", accountId, "Ujagar Fuels", alwaysUse = true))

        val rule = db.merchantRuleDao().getByMerchant("SHRI UJAGAR FUELS")!!
        assertEquals(MerchantRuleOrigin.USER, rule.origin)
        assertEquals("Fuel", rule.category)

        val next = ingestor.ingest(CaptureSource.NOTIFICATION, "AXISBK", axisFuel("435476373862")) as IngestResult.Captured
        assertEquals(ConfidenceBand.HIGH, next.band)
        assertTrue(next.shouldNotify)
    }

    @Test
    fun aUserRuleSurvivesALaterConfirmWithADifferentCategory() = runBlocking {
        val first = (ingestor.ingest(CaptureSource.NOTIFICATION, "AXISBK", axisFuel("435476373861")) as IngestResult.Captured).id
        repository.confirm(first, CaptureEdits("Expense", "Fuel", accountId, "x", alwaysUse = true))
        val second = (ingestor.ingest(CaptureSource.NOTIFICATION, "AXISBK", axisFuel("435476373862")) as IngestResult.Captured).id

        repository.confirm(second, CaptureEdits("Expense", "Groceries", accountId, "x"))

        assertEquals("Fuel", db.merchantRuleDao().getByMerchant("SHRI UJAGAR FUELS")!!.category)
    }

    @Test
    fun aliasRowsFollowTheirAccountWhenItIsDeleted() = runBlocking {
        db.accountAliasDao().upsert(AccountAlias("1236", accountId))

        db.accountDao().delete(db.accountDao().getAccountById(accountId)!!)

        assertNull(db.accountAliasDao().getByAlias("1236"))
    }
}
