package com.issaczerubbabel.ledgar.capture

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.capture.categorize.CategorizationPipeline
import com.issaczerubbabel.ledgar.capture.categorize.KeywordDictionary
import com.issaczerubbabel.ledgar.capture.categorize.MerchantNormalizer
import com.issaczerubbabel.ledgar.capture.pair.LedgerMatchFinder
import com.issaczerubbabel.ledgar.capture.source.BankEmails
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Amazon Pay's emails on Room (#181): the balance has no digits, so its Account alias is by source,
 * set on the first Confirm; a cashback's kind maps to one of the user's Income Categories. Real
 * formats, blanked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CaptureAmazonPayTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var ingestor: CaptureIngestor
    private lateinit var repository: CaptureRepositoryImpl
    private var balance = 0L
    private var hdfcSavings = 0L
    private var mapping = emptyMap<String, String>()

    private val settings = object : CaptureSettingsSource {
        override suspend fun isEnabled() = true
        override suspend fun readsBankEmails() = true
        override suspend fun categoryMapping() = mapping
    }

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        balance = db.accountDao().insert(AccountRecord(groupName = "Wallet", accountName = "Amazon Pay Balance", initialBalance = 0.0))
        hdfcSavings = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "HDFC Savings", initialBalance = 0.0))
        db.dropdownOptionDao().insertAll(
            listOf(
                DropdownOption(optionType = TransactionType.EXPENSE_CATEGORY_OPTION, name = "Shopping", displayOrder = 0),
                DropdownOption(optionType = TransactionType.INCOME_CATEGORY_OPTION, name = "Rewards", displayOrder = 0)
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

    private suspend fun email(text: String, at: Long) = ingestor.ingest(CaptureSource.EMAIL, BankEmails.AMAZON_PAY.displayName, text, at)
    private suspend fun pending() = db.captureDao().getPendingSnapshot()

    @Test
    fun aBalancePaymentShowsMapAccountUntilItsFirstConfirmThenLandsOnTheBalance() = runBlocking {
        val first = email(PAID, T0) as IngestResult.Captured
        val second = email(PAID.replace("903.00", "120.00").replace("000-0000000-0000000", "000-0000000-0000001"), T0 + HOUR)
        assertTrue(second.toString(), second is IngestResult.Captured)

        pending().forEach { capture ->
            assertEquals(AccountHint.AMAZON_PAY_BALANCE, capture.accountHint)
            assertNull(capture.accountId)
        }
        val firstCapture = db.captureDao().getById(first.id)!!
        assertEquals("Amazon.in", firstCapture.merchantRaw)
        assertEquals("000-0000000-0000000", firstCapture.refNumber)
        assertEquals("DEBIT", firstCapture.direction)

        repository.confirm(first.id, CaptureEdits(TransactionType.EXPENSE, "Shopping", balance, "Amazon order"))

        assertEquals(balance, db.accountAliasDao().getByAlias(AccountHint.AMAZON_PAY_BALANCE)!!.accountId)
        // The capture already waiting picks the Account up, and so does the next one.
        assertEquals(balance, pending().single().accountId)
        val third = email(PAID.replace("903.00", "45.00").replace("000-0000000-0000000", "000-0000000-0000002"), T0 + 2 * HOUR)
        assertEquals(balance, db.captureDao().getById((third as IngestResult.Captured).id)!!.accountId)
    }

    @Test
    fun aCashbackSuggestsTheCategoryMappedToCashback() = runBlocking {
        mapping = mapOf(CaptureKinds.CASHBACK to "Rewards")
        val result = email(CASHBACK, T0) as IngestResult.Captured

        val capture = db.captureDao().getById(result.id)!!
        assertEquals("CREDIT", capture.direction)
        assertEquals(25.0, capture.amount, 0.001)
        assertEquals(TransactionType.INCOME, capture.suggestedType)
        assertEquals("Rewards", capture.suggestedCategory)
    }

    @Test
    fun anUnmappedCashbackNeedsACategory() = runBlocking {
        val result = email(CASHBACK, T0) as IngestResult.Captured

        val capture = db.captureDao().getById(result.id)!!
        assertEquals(TransactionType.INCOME, capture.suggestedType)
        assertNull(capture.suggestedCategory)
        assertEquals(CaptureKinds.CASHBACK, CaptureTrace.parse(capture.traceJson).unmappedCanonical)
    }

    @Test
    fun aGiftCardIsIncomeUnlessAMerchantRuleSaysTransfer() = runBlocking {
        val plain = email(GIFT_CARD, T0) as IngestResult.Captured
        assertEquals(TransactionType.INCOME, db.captureDao().getById(plain.id)!!.suggestedType)
        assertEquals("CREDIT", db.captureDao().getById(plain.id)!!.direction)

        db.merchantRuleDao().upsert(
            MerchantRule(
                merchantNorm = MerchantNormalizer.normalize("Amazon Pay eGift Card").norm, direction = "CREDIT", category = "",
                type = TransactionType.TRANSFER, toAccountId = hdfcSavings, origin = MerchantRuleOrigin.USER, hitCount = 1, streak = 0,
                updatedAt = 0
            )
        )
        val ruled = email(GIFT_CARD.replace("833.00", "500.00"), T0 + HOUR) as IngestResult.Captured

        val capture = db.captureDao().getById(ruled.id)!!
        assertEquals(TransactionType.TRANSFER, capture.suggestedType)
        assertEquals(hdfcSavings, capture.suggestedToAccountId)
    }

    @Test
    fun anAmazonPayEmailNoParserReadsIsKeptUnderAlertsWeCouldntRead() = runBlocking {
        val result = email("Get ₹100 back on your next bill\nHi NAME, Pay your bill with Amazon Pay and get Rs 100 back", T0)

        assertEquals(IngestResult.Unparsed, result)
        assertEquals(1, db.unparsedAlertDao().observeCount().first())
    }

    private companion object {
        const val HOUR = 60 * 60_000L
        const val T0 = 1_795_000_123_000L

        const val PAID = "Rs 903.00 was paid on Amazon.in\nHi NAME, Thank you for using Amazon Pay Balance. Your payment was " +
            "successful. Paid on Amount Amazon.in ₹903.00 To report any unauthorised transaction, please click here Order ID 000-0000000-0000000"

        const val GIFT_CARD = "Amazon Pay Gift Card added to your Amazon Pay balance\nHi NAME, Amazon Pay Gift Card has been " +
            "added to your Amazon Pay balance. Your balance now reflects the additional funds added to your account. Received " +
            "Amount Amazon Pay eGift Card ₹833.00 Reference"

        const val CASHBACK = "Your cashback of ₹25.00 is here!\nHi NAME, Yay! Here’s ₹25.00 cashback! You’ve got this cashback " +
            "for PRODUCT placed during Amazon Shopping. Sometimes your cashback could get credited in"
    }
}
