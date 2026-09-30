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
            db.captureDao(), db.accountAliasDao(), DropdownOptionRepositoryImpl(db.dropdownOptionDao()), pipeline, settings, db.unparsedAlertDao()
        )
        repository = CaptureRepositoryImpl(db, db.captureDao(), db.merchantRuleDao(), db.accountAliasDao(), db.expenseDao(), db.unparsedAlertDao())
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
    fun aLateAlertIsFiledUnderTheDateItStates() = runBlocking {
        // The Axis alert says 19-12-24, but it is only read on 5 Oct 2026.
        val arrivedAt = java.time.LocalDateTime.of(2026, 10, 5, 10, 30)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val id = (ingestor.ingest(CaptureSource.NOTIFICATION, "AXISBK", axisFuel("435476373861"), arrivedAt) as IngestResult.Captured).id

        val expenseId = repository.confirm(id, CaptureEdits("Expense", "Fuel", accountId, "Ujagar Fuels"))!!

        assertEquals("2024-12-19", db.expenseDao().getById(expenseId)!!.date)
        assertEquals(arrivedAt, db.captureDao().getById(id)!!.capturedAt)
    }

    @Test
    fun aTodayAlertKeepsTodaysDate() = runBlocking {
        val now = java.time.LocalDate.now()
        val text = "Sent Rs.70.00 From HDFC Bank A/C *1234 To Mrs Jane Doe On %02d/%02d/%02d Ref 66353123456790"
            .format(now.dayOfMonth, now.monthValue, now.year % 100)
        val id = (ingestor.ingest(CaptureSource.NOTIFICATION, "HDFC Bank", text) as IngestResult.Captured).id

        val expenseId = repository.confirm(id, CaptureEdits("Expense", "Food", accountId, "Mrs Jane Doe"))!!

        assertEquals(now.toString(), db.expenseDao().getById(expenseId)!!.date)
    }

    @Test
    fun theInboxListsWhatArrivedLastFirstEvenIfItsDateIsOlder() = runBlocking {
        val zone = java.time.ZoneId.systemDefault()
        fun at(hour: Int) = java.time.LocalDateTime.of(2026, 9, 30, hour, 0).atZone(zone).toInstant().toEpochMilli()
        val early = (ingestor.ingest(
            CaptureSource.NOTIFICATION, "HDFC Bank",
            "Sent Rs.10.00 From HDFC Bank A/C *1234 To Shop A On 30/09/26 Ref 11111111111", at(10)
        ) as IngestResult.Captured).id
        val late = (ingestor.ingest(
            CaptureSource.NOTIFICATION, "HDFC Bank",
            "Sent Rs.20.00 From HDFC Bank A/C *1234 To Shop B On 25/09/26 Ref 22222222222", at(11)
        ) as IngestResult.Captured).id

        assertEquals(listOf(late, early), pending().map { it.id })
    }

    @Test
    fun readingTheSameNotificationAgainAtTheSamePostTimeIsNotASecondCapture() = runBlocking {
        val postedAt = 1_800_000_000_000L
        val text = "Paid Rs.500 to Ramesh Kumar."
        ingestor.ingest(CaptureSource.NOTIFICATION, "Google Pay", text, postedAt)

        // What a listener reconnect does: the same notification, read again much later.
        assertEquals(IngestResult.Duplicate, ingestor.ingest(CaptureSource.NOTIFICATION, "Google Pay", text, postedAt))
        assertEquals(1, pending().size)
    }

    private suspend fun unparsed() = db.unparsedAlertDao().getAllSnapshot()

    @Test
    fun anUnreadableBankAlertWithAnAmountIsKept() = runBlocking {
        val text = "Your HDFC Bank statement shows Rs.1,200 dues. Open the app for details."

        assertEquals(IngestResult.Unparsed, ingestor.ingest(CaptureSource.NOTIFICATION, "AD-HDFCBK-T", text))

        val kept = unparsed().single()
        assertEquals("AD-HDFCBK-T", kept.sender)
        assertEquals(text, kept.rawText)
    }

    @Test
    fun otpsChatsAndAmountlessAlertsAreNeverKept() = runBlocking {
        ingestor.ingest(CaptureSource.NOTIFICATION, "AD-HDFCBK-T", "123456 is your OTP for txn of Rs.2,500 at FLIPKART.")
        ingestor.ingest(CaptureSource.NOTIFICATION, "Mom", "Send me Rs.500 for groceries when you can")
        ingestor.ingest(CaptureSource.NOTIFICATION, "AD-HDFCBK-T", "Your statement is ready to view.")
        ingestor.ingest(CaptureSource.NOTIFICATION, "Some Shop", "Get Rs.500 off your next order")

        assertTrue(unparsed().isEmpty())
    }

    @Test
    fun readingTheSameUnreadableAlertAgainKeepsOneCopy() = runBlocking {
        val text = "Your HDFC Bank statement shows Rs.1,200 dues."
        ingestor.ingest(CaptureSource.NOTIFICATION, "AD-HDFCBK-T", text, 5_000_000_000L)
        ingestor.ingest(CaptureSource.NOTIFICATION, "AD-HDFCBK-T", text, 5_000_000_000L)

        assertEquals(1, unparsed().size)
    }

    @Test
    fun unreadableAlertsAreDeletedAfterThirtyDays() = runBlocking {
        val day = 24L * 60 * 60 * 1000
        ingestor.ingest(CaptureSource.NOTIFICATION, "AD-HDFCBK-T", "Your HDFC Bank statement shows Rs.100 dues.", 100 * day)

        ingestor.ingest(CaptureSource.NOTIFICATION, "AD-HDFCBK-T", "Your HDFC Bank statement shows Rs.200 dues.", 131 * day)

        assertEquals(listOf("Your HDFC Bank statement shows Rs.200 dues."), unparsed().map { it.rawText })
    }

    @Test
    fun nothingIsKeptWhileCaptureIsOff() = runBlocking {
        settings.enabled = false

        ingestor.ingest(CaptureSource.NOTIFICATION, "AD-HDFCBK-T", "Your HDFC Bank statement shows Rs.1,200 dues.")

        assertTrue(unparsed().isEmpty())
    }

    @Test
    fun turningCaptureOffAlsoClearsTheUnreadableList() = runBlocking {
        ingestor.ingest(CaptureSource.NOTIFICATION, "AD-HDFCBK-T", "Your HDFC Bank statement shows Rs.1,200 dues.")
        assertEquals(1, unparsed().size)

        repository.clearPending()

        assertTrue(unparsed().isEmpty())
    }

    @Test
    fun aliasRowsFollowTheirAccountWhenItIsDeleted() = runBlocking {
        db.accountAliasDao().upsert(AccountAlias("1236", accountId))

        db.accountDao().delete(db.accountDao().getAccountById(accountId)!!)

        assertNull(db.accountAliasDao().getByAlias("1236"))
    }
}
