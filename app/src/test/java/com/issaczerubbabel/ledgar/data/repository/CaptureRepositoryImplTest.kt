package com.issaczerubbabel.ledgar.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.capture.notify.CaptureActionHandler
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleOrigin
import com.issaczerubbabel.ledgar.data.preferences.ReminderPreferences
import com.issaczerubbabel.ledgar.util.TransactionType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CaptureRepositoryImplTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var repo: CaptureRepositoryImpl
    private var accountId = 0L
    private var hash = 0

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = CaptureRepositoryImpl(
            db, db.captureDao(), db.merchantRuleDao(), db.accountAliasDao(), db.expenseDao(), db.unparsedAlertDao()
        )
        accountId = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "HDFC Savings", initialBalance = 0.0))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun capture(
        merchantRaw: String? = "SWIGGY*BLR INSTAMART",
        merchantNorm: String? = "SWIGGY INSTAMART",
        amount: Double = 349.0,
        status: String = "PENDING"
    ): CapturedTransaction {
        val id = db.captureDao().insert(
            CapturedTransaction(
                sources = "NOTIF", sender = "HDFC Bank", rawText = "Sent Rs.$amount", rawHash = "hash-${hash++}",
                capturedAt = 1, txnTime = 1_760_000_000_000, amount = amount, direction = "DEBIT", channel = "UPI",
                merchantRaw = merchantRaw, merchantNorm = merchantNorm, accountId = accountId,
                suggestedType = TransactionType.EXPENSE, suggestedCategory = "Groceries", confidence = 0.9, status = status
            )
        )
        return db.captureDao().getById(id)!!
    }

    private suspend fun confirm(capture: CapturedTransaction, description: String, category: String = "Groceries") =
        repo.confirm(capture.id, CaptureEdits(TransactionType.EXPENSE, category, accountId, description))

    @Test
    fun confirmingWithAnEditedDescriptionWritesItAndRemembersItOnTheMerchantRule() = runBlocking {
        val first = capture()

        val expenseId = confirm(first, "Instamart")

        assertEquals("Instamart", db.expenseDao().getById(expenseId!!)!!.description)
        assertEquals("Instamart", db.merchantRuleDao().getByMerchant("SWIGGY INSTAMART")!!.description)
    }

    @Test
    fun theNextCaptureFromTheSameMerchantStartsWithTheRename() = runBlocking {
        confirm(capture(), "Instamart")
        val next = capture(amount = 120.0)

        assertEquals("Instamart", repo.titleFor(next))
        assertEquals(mapOf("SWIGGY INSTAMART" to "Instamart"), repo.observeRenames().first())
    }

    @Test
    fun aRenameAlsoRetitlesCapturesAlreadyWaiting() = runBlocking {
        val waiting = capture(amount = 120.0)
        assertEquals("SWIGGY*BLR INSTAMART", repo.titleFor(waiting))

        confirm(capture(), "Instamart")

        assertEquals("Instamart", repo.titleFor(waiting))
    }

    @Test
    fun aLaterRenameReplacesTheEarlierOne() = runBlocking {
        confirm(capture(), "Instamart")
        confirm(capture(amount = 50.0), "Swiggy Instamart")

        assertEquals("Swiggy Instamart", repo.titleFor(capture(amount = 1.0)))
    }

    @Test
    fun confirmingWithTheBanksTextClearsTheRename() = runBlocking {
        confirm(capture(), "Instamart")
        confirm(capture(amount = 50.0), "SWIGGY*BLR INSTAMART")

        assertNull(db.merchantRuleDao().getByMerchant("SWIGGY INSTAMART")!!.description)
        assertEquals("SWIGGY*BLR INSTAMART", repo.titleFor(capture(amount = 1.0)))
    }

    @Test
    fun aCaptureWithoutAMerchantFallsBackToAPlainTitle() = runBlocking {
        assertEquals("Payment", repo.titleFor(capture(merchantRaw = null, merchantNorm = null)))
    }

    @Test
    fun aRenameSurvivesTheLearnedCategoryBeingDropped() = runBlocking {
        db.merchantRuleDao().upsert(
            MerchantRule(
                "SWIGGY INSTAMART", "Groceries", TransactionType.EXPENSE, origin = MerchantRuleOrigin.LEARNED,
                hitCount = 5, streak = 1, updatedAt = 0, description = "Instamart"
            )
        )

        // The second override in a row would delete a learned rule.
        confirm(capture(), "Instamart", category = "Food")

        val rule = db.merchantRuleDao().getByMerchant("SWIGGY INSTAMART")
        assertNotNull("the rule is kept for its description", rule)
        assertEquals(MerchantRuleOrigin.CANDIDATE, rule!!.origin)
        assertEquals("Instamart", rule.description)
        assertEquals("Instamart", repo.titleFor(capture(amount = 1.0)))
    }

    @Test
    fun aUserRuleKeepsItsCategoryButTakesTheRename() = runBlocking {
        db.merchantRuleDao().upsert(
            MerchantRule("SWIGGY INSTAMART", "Groceries", TransactionType.EXPENSE, origin = MerchantRuleOrigin.USER, updatedAt = 0)
        )

        confirm(capture(), "Instamart", category = "Food")

        val rule = db.merchantRuleDao().getByMerchant("SWIGGY INSTAMART")!!
        assertEquals("Groceries", rule.category)
        assertEquals(MerchantRuleOrigin.USER, rule.origin)
        assertEquals("Instamart", rule.description)
    }

    @Test
    fun dismissingMarksTheCaptureDismissed() = runBlocking {
        val c = capture()

        repo.dismiss(c.id)

        assertEquals("DISMISSED", db.captureDao().getById(c.id)!!.status)
        assertTrue(repo.observePending().first().isEmpty())
    }

    @Test
    fun confirmingSeveralWritesOneTransactionEach() = runBlocking {
        val captures = listOf(capture(amount = 10.0), capture(amount = 20.0), capture(amount = 30.0))

        captures.forEach { confirm(it, "Instamart") }

        assertEquals(listOf(10.0, 20.0, 30.0), db.expenseDao().getAllRecordsSnapshot().map { it.amount }.sorted())
        assertTrue(captures.all { db.captureDao().getById(it.id)!!.status == "CONFIRMED" })
    }

    @Test
    fun theNotificationsConfirmUsesTheRenamedTitle() = runBlocking {
        confirm(capture(), "Instamart")
        val next = capture(amount = 120.0)
        val handler = CaptureActionHandler(db.captureDao(), repo, ReminderPreferences(ApplicationProvider.getApplicationContext()))

        assertTrue(handler.confirm(next.id))

        val expenseId = db.captureDao().getById(next.id)!!.confirmedExpenseId!!
        assertEquals("Instamart", db.expenseDao().getById(expenseId)!!.description)
    }
}
