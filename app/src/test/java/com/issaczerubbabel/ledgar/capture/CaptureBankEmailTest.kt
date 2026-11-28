package com.issaczerubbabel.ledgar.capture

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.capture.categorize.CategorizationPipeline
import com.issaczerubbabel.ledgar.capture.categorize.KeywordDictionary
import com.issaczerubbabel.ledgar.capture.pair.LedgerMatchFinder
import com.issaczerubbabel.ledgar.capture.source.BankEmails
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountAlias
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepositoryImpl
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * HDFC's alert emails on Room (#180): read only while Read bank emails is on and only from a known
 * sender, and merged with the SMS for the same payment (ADR-0012). Real formats, blanked, except
 * where marked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CaptureBankEmailTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var ingestor: CaptureIngestor
    private var hdfcSavings = 0L
    private var readsBankEmails = true

    private val settings = object : CaptureSettingsSource {
        override suspend fun isEnabled() = true
        override suspend fun readsBankEmails() = readsBankEmails
        override suspend fun categoryMapping() = emptyMap<String, String>()
    }

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        hdfcSavings = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "HDFC Savings", initialBalance = 0.0))
        db.accountAliasDao().upsert(AccountAlias("1234", hdfcSavings))
        val pipeline = CategorizationPipeline(KeywordDictionary(emptyMap())) { db.merchantRuleDao().get(it) }
        ingestor = CaptureIngestor(
            db, db.captureDao(), db.accountAliasDao(), DropdownOptionRepositoryImpl(db.dropdownOptionDao()), pipeline, settings,
            db.unparsedAlertDao(), RoomCaptureHistorySource(db.expenseDao(), db.accountAliasDao()),
            LedgerMatchFinder(db.expenseDao(), db.captureDao())
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun email(text: String, at: Long, sender: String = BankEmails.HDFC.displayName) =
        ingestor.ingest(CaptureSource.EMAIL, sender, text, at)

    private suspend fun sms(text: String, at: Long) = ingestor.ingest(CaptureSource.NOTIFICATION, HDFC_SMS, text, at)
    private suspend fun pending() = db.captureDao().getPendingSnapshot()
    private suspend fun unparsedCount() = db.unparsedAlertDao().observeCount().first()

    @Test
    fun anHdfcSmsAndItsEmailAreOneCapturedTransactionWithTheEmailsPayee() = runBlocking {
        val first = sms(UPI_SMS, T0) as IngestResult.Captured
        // The email can come much later; the reference still ties it to the SMS.
        val second = email(UPI_EMAIL, T0 + 45 * MINUTE)

        assertTrue(second.toString(), second is IngestResult.Merged)
        val capture = pending().single()
        assertEquals(first.id, capture.id)
        assertEquals(2000.0, capture.amount, 0.001)
        assertEquals("1234", capture.accountHint)
        assertEquals(hdfcSavings, capture.accountId)
        assertEquals("111111111111", capture.refNumber)
        assertEquals("Indian Clearing Corporation Ltd", capture.merchantRaw)
        assertEquals(listOf(HDFC_SMS, BankEmails.HDFC.displayName), db.captureDao().getAlerts(capture.id).map { it.sender })
    }

    @Test
    fun anEmailAloneIsACapturedTransaction() = runBlocking {
        val result = email(UPI_EMAIL, T0)

        assertTrue(result.toString(), result is IngestResult.Captured)
        assertEquals(hdfcSavings, pending().single().accountId)
    }

    @Test
    fun withReadBankEmailsOffNoEmailIsRead() = runBlocking {
        readsBankEmails = false

        assertTrue(email(UPI_EMAIL, T0) is IngestResult.Ignored)
        assertTrue(email(UNREADABLE_EMAIL, T0) is IngestResult.Ignored)
        assertEquals(0, pending().size)
        assertEquals(0, unparsedCount())
    }

    @Test
    fun anEmailFromAnyOtherSenderIsNeverReadOrKept() = runBlocking {
        val result = email(UPI_EMAIL, T0, sender = "A Friend")

        assertTrue(result is IngestResult.Ignored)
        assertEquals(0, pending().size)
        assertEquals(0, unparsedCount())
    }

    @Test
    fun theEmailSendersNameFromAnotherAppIsNotItsEmail() = runBlocking {
        // A chat from a contact saved under that name, say.
        val result = ingestor.ingest(CaptureSource.NOTIFICATION, BankEmails.HDFC.displayName, UPI_EMAIL, T0)

        assertTrue(result is IngestResult.Ignored)
        assertEquals(0, pending().size)
        assertEquals(0, unparsedCount())
    }

    @Test
    fun anHdfcEmailNobodyCanReadIsKeptUnderAlertsWeCouldntRead() = runBlocking {
        assertEquals(IngestResult.Unparsed, email(UNREADABLE_EMAIL, T0))
        assertEquals(1, unparsedCount())
    }

    private companion object {
        const val MINUTE = 60_000L
        const val T0 = 1_795_000_123_000L
        const val HDFC_SMS = "AD-HDFCBK-S"

        const val UPI_SMS = "Sent Rs.2000.00\nFrom HDFC Bank A/C *1234\nTo Indian Clearing Corporation Ltd\nOn 28/11/26\nRef 111111111111\nNot You?"
        const val UPI_EMAIL = "You have done a UPI txn. Check details!\nDear Customer, Greetings from HDFC Bank! Rs.2000.00 is debited " +
            "from your account ending 1234 towards VPA xxxxxxxx@validyes (Indian Clearing Corporation Ltd) on 28-11-26. UPI " +
            "transaction reference no.: 111111111111."

        /** Made up: a wording the parser doesn't know, with an amount. */
        const val UNREADABLE_EMAIL = "View: Account update for your HDFC Bank A/c\nDear Customer, Rs.500.00 has moved in a way we " +
            "have not seen before."
    }
}
