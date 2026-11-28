package com.issaczerubbabel.ledgar.capture

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.capture.categorize.CategorizationPipeline
import com.issaczerubbabel.ledgar.capture.categorize.KeywordDictionary
import com.issaczerubbabel.ledgar.capture.categorize.MerchantNormalizer
import com.issaczerubbabel.ledgar.capture.pair.LedgerMatchFinder
import com.issaczerubbabel.ledgar.capture.source.AlertSource
import com.issaczerubbabel.ledgar.capture.source.AlertSourceKind
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountAlias
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleOrigin
import com.issaczerubbabel.ledgar.data.repository.CaptureEdits
import com.issaczerubbabel.ledgar.data.repository.CaptureRepositoryImpl
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepositoryImpl
import com.issaczerubbabel.ledgar.util.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * ADR-0012 end to end on Room: one payment's Alerts from different Alert sources become one
 * Captured transaction. The alert texts are real formats with names, digits and refs blanked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CaptureSamePaymentTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var ingestor: CaptureIngestor
    private lateinit var repository: CaptureRepositoryImpl
    private var hdfcSavings = 0L

    private val settings = object : CaptureSettingsSource {
        override suspend fun isEnabled() = true
        override suspend fun readsBankEmails() = true
        override suspend fun categoryMapping() = emptyMap<String, String>()
    }

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        hdfcSavings = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "HDFC Savings", initialBalance = 0.0))
        db.accountAliasDao().upsert(AccountAlias("1234", hdfcSavings))
        db.dropdownOptionDao().insertAll(
            listOf(
                DropdownOption(optionType = TransactionType.EXPENSE_CATEGORY_OPTION, name = "Investments", displayOrder = 0),
                DropdownOption(optionType = TransactionType.INCOME_CATEGORY_OPTION, name = "Gifts", displayOrder = 0)
            )
        )
        val pipeline = CategorizationPipeline(KeywordDictionary(emptyMap())) { db.merchantRuleDao().get(it) }
        ingestor = CaptureIngestor(
            db, db.captureDao(), db.accountAliasDao(), DropdownOptionRepositoryImpl(db.dropdownOptionDao()), pipeline, settings,
            db.unparsedAlertDao(), RoomCaptureHistorySource(db.expenseDao(), db.accountAliasDao()),
            LedgerMatchFinder(db.expenseDao(), db.captureDao())
        )
        repository = CaptureRepositoryImpl(
            db, db.captureDao(), db.merchantRuleDao(), db.accountAliasDao(), db.expenseDao(), db.unparsedAlertDao(), ingestor, ingestor
        )
    }

    @After
    fun tearDown() = db.close()

    private suspend fun sms(text: String, at: Long) = ingestor.ingest(CaptureSource.NOTIFICATION, HDFC_SMS, text, at)
    private suspend fun googlePay(text: String, at: Long) = ingestor.ingest(CaptureSource.NOTIFICATION, "Google Pay", text, at)
    private suspend fun pending() = db.captureDao().getPendingSnapshot()
    private suspend fun alertsOf(id: Long) = db.captureDao().getAlerts(id)

    private suspend fun rule(merchant: String, direction: String, type: String, category: String) = db.merchantRuleDao().upsert(
        MerchantRule(
            merchantNorm = MerchantNormalizer.normalize(merchant).norm, direction = direction, category = category, type = type,
            origin = MerchantRuleOrigin.USER, hitCount = 1, streak = 0, updatedAt = 0
        )
    )

    @Test
    fun aBankCreditSmsAndGooglePaysPaidYouTwoSecondsApartAreOneCredit() = runBlocking {
        val first = sms(HDFC_CREDIT, T0) as IngestResult.Captured
        val second = googlePay(PAID_YOU, T0 + 2_000)

        assertTrue(second is IngestResult.Merged)
        val capture = pending().single()
        assertEquals(first.id, capture.id)
        assertEquals("CREDIT", capture.direction)
        assertEquals(1.0, capture.amount, 0.001)
        assertEquals("1234", capture.accountHint)
        assertEquals(hdfcSavings, capture.accountId)
        assertEquals("A B KUMAR", capture.merchantRaw)
        assertEquals(listOf(HDFC_SMS, "Google Pay"), alertsOf(capture.id).map { it.sender })
    }

    @Test
    fun theOrderTheyArriveInDoesNotMatter() = runBlocking {
        googlePay(PAID_YOU, T0)
        sms(HDFC_CREDIT, T0 + 2_000)

        val capture = pending().single()
        assertEquals(hdfcSavings, capture.accountId)
        assertEquals("A B KUMAR", capture.merchantRaw)
        // The date is the earliest Alert's.
        assertEquals(T0, capture.txnTime)
    }

    @Test
    fun elevenMinutesApartTheyAreTwoCaptures() = runBlocking {
        sms(HDFC_CREDIT, T0)
        googlePay(PAID_YOU, T0 + 11 * MINUTE)

        assertEquals(2, pending().size)
    }

    @Test
    fun theSameRefMergesAnHourApartAndADifferentRefNever() = runBlocking {
        sms(hdfcSent("111111111111"), T0)
        val app = ingestor.ingest(CaptureSource.NOTIFICATION, "HDFC Bank", hdfcSent("111111111111"), T0 + 60 * MINUTE)
        assertTrue(app is IngestResult.Merged)
        assertEquals(1, pending().size)

        // Another source, the same amount two seconds later, but its own reference: another payment.
        ingestor.ingest(
            CaptureSource.EMAIL, "HDFC Bank InstaAlerts",
            "You have done a UPI txn. Check details!\nDear Customer, Greetings from HDFC Bank! Rs.70.00 is debited from your " +
                "account ending 1234 towards VPA xxxx@okaxis (SHOP) on 26-09-26. UPI transaction reference no.: 222222222222.",
            T0 + 2_000
        )
        assertEquals(2, pending().size)
    }

    @Test
    fun twoIdenticalGooglePayNotificationsForTwoPaymentsNeverMerge() = runBlocking {
        // Either side of a ten-minute boundary, so the rawHash doesn't take the second for a repeat.
        val boundary = (T0 / (10 * MINUTE) + 1) * 10 * MINUTE
        sms(HDFC_CREDIT, boundary - 2 * MINUTE)
        assertTrue(googlePay(PAID_YOU, boundary - MINUTE) is IngestResult.Merged)

        val again = googlePay(PAID_YOU, boundary + MINUTE)

        assertTrue(again is IngestResult.Captured)
        assertEquals(2, pending().size)
        assertEquals(listOf(1, 2), pending().map { alertsOf(it.id).size }.sorted())
    }

    @Test
    fun aRuleSavedUnderTheBanksTextStillMatchesAfterGooglePayAddsThePayee() = runBlocking {
        rule("ACME BROKING LTD", "DEBIT", TransactionType.EXPENSE, "Investments")
        sms(HDFC_MANDATE, T0)

        googlePay(AUTOPAY, T0 + 2_000)

        val capture = pending().single()
        assertEquals("Zerodha Broking Ltd", capture.merchantRaw)
        assertEquals("Investments", capture.suggestedCategory)
        assertEquals("R1", capture.decidedBy)

        // The rename saved under the bank's text still titles the card.
        db.merchantRuleDao().upsert(
            db.merchantRuleDao().get(MerchantNormalizer.normalize("ACME BROKING LTD").norm, "DEBIT")!!.copy(description = "SIP")
        )
        assertEquals("SIP", repository.titleFor(capture))

        // A re-guess reads every Alert again, so it still agrees.
        ingestor.reguessPending()
        assertEquals("Investments", pending().single().suggestedCategory)
    }

    @Test
    fun aLateAlertAfterConfirmMakesNoCardAndLeavesTheTransactionAlone() = runBlocking {
        val id = (sms(HDFC_CREDIT, T0) as IngestResult.Captured).id
        val expenseId = repository.confirm(id, CaptureEdits(TransactionType.INCOME, "Gifts", hdfcSavings, "From a friend"))!!
        val transaction = db.expenseDao().getById(expenseId)
        val confirmed = db.captureDao().getById(id)

        val late = googlePay(PAID_YOU, T0 + MINUTE)

        assertEquals(IngestResult.AttachedLate(id), late)
        assertTrue(pending().isEmpty())
        assertEquals(transaction, db.expenseDao().getById(expenseId))
        assertEquals(confirmed, db.captureDao().getById(id))
        assertEquals(2, alertsOf(id).size)
        // Read again from the shade, it is still only a repeat.
        assertEquals(IngestResult.Duplicate, googlePay(PAID_YOU, T0 + MINUTE))
    }

    @Test
    fun aPaymentNotifiesOnceNotOncePerAlert() = runBlocking {
        rule("SHOP", "DEBIT", TransactionType.EXPENSE, "Investments")
        val first = sms(hdfcSent("111111111111"), T0) as IngestResult.Captured
        assertTrue(first.shouldNotify)

        val merged = ingestor.ingest(CaptureSource.NOTIFICATION, "HDFC Bank", hdfcSent("111111111111"), T0 + 2_000) as IngestResult.Merged
        assertFalse(merged.shouldNotify)
    }

    @Test
    fun theBanksAlertCanMakeAMergeWorthItsFirstNotification() = runBlocking {
        rule("A B KUMAR", "CREDIT", TransactionType.INCOME, "Gifts")
        val alone = googlePay(PAID_YOU, T0) as IngestResult.Captured
        assertFalse("Google Pay never names the Account", alone.shouldNotify)

        val merged = sms(HDFC_CREDIT, T0 + 2_000) as IngestResult.Merged
        assertTrue(merged.shouldNotify)
    }

    @Test
    fun notTheSamePaymentSplitsAMergeIntoCapturesThatNeverMergeAgain() = runBlocking {
        val id = (sms(HDFC_CREDIT, T0) as IngestResult.Captured).id
        googlePay(PAID_YOU, T0 + 2_000)

        repository.splitSamePayment(id)

        val (bank, upi) = pending().sortedBy { it.id }
        assertEquals(id, bank.id)
        assertEquals(listOf(HDFC_SMS), alertsOf(bank.id).map { it.sender })
        assertEquals(hdfcSavings, bank.accountId)
        assertEquals(null, bank.merchantRaw)
        assertEquals(listOf("Google Pay"), alertsOf(upi.id).map { it.sender })
        assertEquals("A B KUMAR", upi.merchantRaw)
        assertEquals(null, upi.accountId)
        assertEquals(setOf(GOOGLE_PAY), CaptureTrace.parse(bank.traceJson).keptApartFrom)

        // Another Google Pay Alert in the window doesn't join the bank's capture again.
        googlePay("A B KUMAR paid you ₹1.00\nTap to view this payment.", T0 + MINUTE)
        assertEquals(listOf(1, 1, 1), pending().map { alertsOf(it.id).size })
        // A re-guess keeps the mark.
        ingestor.reguessPending()
        assertEquals(setOf(GOOGLE_PAY), CaptureTrace.parse(db.captureDao().getById(id)!!.traceJson).keptApartFrom)
    }

    @Test
    fun theInboxSeesEveryAlertOfAWaitingCapture() = runBlocking {
        val id = (sms(HDFC_CREDIT, T0) as IngestResult.Captured).id
        googlePay(PAID_YOU, T0 + 2_000)

        val alerts = repository.observePendingAlerts().first().getValue(id)

        assertEquals(listOf("SMS from HDFC", "Google Pay"), alerts.map { AlertSource.of(it.source, it.sender).label })
        assertEquals(listOf(HDFC_CREDIT, PAID_YOU), alerts.map { it.rawText })
    }

    private companion object {
        const val MINUTE = 60_000L
        const val T0 = 1_795_000_123_000L
        const val HDFC_SMS = "AD-HDFCBK-S"
        val GOOGLE_PAY = AlertSource(AlertSourceKind.UPI_APP, "Google Pay")

        const val HDFC_CREDIT = "Credit Alert! Rs.1.00 credited to HDFC Bank A/c XX1234 on 28-11-26 from VPA *00-0@okaxis (UPI 111111111111)"
        const val PAID_YOU = "A B KUMAR paid you ₹1.00\nTap to view."
        const val HDFC_MANDATE =
            "UPI Mandate: Sent Rs.2000.00 from HDFC Bank A/c 1234 To ACME BROKING LTD On 05/11/26 Ref 111111111111 Not You? Call 18002586161"
        const val AUTOPAY = "Payment successful\nPayment for Autopay of ₹2,000.00 to Zerodha Broking Ltd was successful. Tap to view."

        fun hdfcSent(ref: String) = "Sent Rs.70.00\nFrom HDFC Bank A/C *1234\nTo SHOP\nOn 26/09/26\nRef $ref\nNot You?"
    }
}
