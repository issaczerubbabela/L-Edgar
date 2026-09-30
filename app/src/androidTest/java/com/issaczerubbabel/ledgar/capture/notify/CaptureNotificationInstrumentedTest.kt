package com.issaczerubbabel.ledgar.capture.notify

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.issaczerubbabel.ledgar.capture.CaptureIngestor
import com.issaczerubbabel.ledgar.capture.CaptureSettingsSource
import com.issaczerubbabel.ledgar.capture.CaptureSource
import com.issaczerubbabel.ledgar.capture.IngestResult
import com.issaczerubbabel.ledgar.capture.categorize.CategorizationPipeline
import com.issaczerubbabel.ledgar.capture.categorize.KeywordDictionary
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountAlias
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleOrigin
import com.issaczerubbabel.ledgar.data.repository.CaptureRepositoryImpl
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The notification's buttons and the notification itself, on a real device. */
@RunWith(AndroidJUnit4::class)
class CaptureNotificationInstrumentedTest {

    @get:Rule
    val permission: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    private lateinit var context: Context
    private lateinit var db: SheetSyncDatabase
    private lateinit var ingestor: CaptureIngestor
    private lateinit var handler: CaptureActionHandler
    private lateinit var notifier: CaptureNotifier
    private var accountId = 0L

    private val settings = object : CaptureSettingsSource {
        override suspend fun isEnabled() = true
        override suspend fun categoryMapping() = emptyMap<String, String>()
    }

    private fun axisFuel(ref: String) =
        "INR 100.50 was debited from your A/c no. XX1236 on 19-12-24 at 10:19:54 IST via UPI/P2M/$ref/Shri Ujagar Fuels. Avail. Bal: INR 5,420.10."

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, SheetSyncDatabase::class.java).allowMainThreadQueries().build()
        accountId = db.accountDao().insert(AccountRecord(groupName = "Accounts", accountName = "Axis Savings", initialBalance = 0.0))
        db.dropdownOptionDao().insertAll(listOf(DropdownOption(optionType = "EXPENSE_CATEGORY", name = "Fuel", displayOrder = 0)))
        db.merchantRuleDao().upsert(
            MerchantRule("SHRI UJAGAR FUELS", "Fuel", "Expense", origin = MerchantRuleOrigin.USER, updatedAt = 0)
        )
        ingestor = CaptureIngestor(
            db.captureDao(), db.accountAliasDao(), DropdownOptionRepositoryImpl(db.dropdownOptionDao()),
            CategorizationPipeline(KeywordDictionary(emptyMap())) { db.merchantRuleDao().getByMerchant(it) }, settings
        )
        val repository = CaptureRepositoryImpl(db, db.captureDao(), db.merchantRuleDao(), db.accountAliasDao(), db.expenseDao())
        handler = CaptureActionHandler(db.captureDao(), repository)
        notifier = CaptureNotifier(context, db.captureDao(), db.accountDao())
    }

    @After
    fun tearDown() {
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()
        db.close()
    }

    private suspend fun readyCapture(ref: String, withAccount: Boolean = true): Long {
        if (withAccount) db.accountAliasDao().upsert(AccountAlias("1236", accountId))
        return (ingestor.ingest(CaptureSource.NOTIFICATION, "AXISBK", axisFuel(ref)) as IngestResult.Captured).id
    }

    private fun activeCaptureNotifications() =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).activeNotifications
            .filter { it.tag == CaptureNotifier.TAG }

    /** The system posts and cancels notifications asynchronously, so wait for the list to settle. */
    private fun awaitCaptureNotifications(untilCount: Int) : List<android.service.notification.StatusBarNotification> {
        val deadline = System.currentTimeMillis() + 5_000
        var current = activeCaptureNotifications()
        while (current.size != untilCount && System.currentTimeMillis() < deadline) {
            Thread.sleep(50)
            current = activeCaptureNotifications()
        }
        return current
    }

    @Test
    fun confirmSavesTheTransactionWithExactPaise() = runBlocking {
        val id = readyCapture("435476373861")

        assertTrue(handler.confirm(id))

        val expense = db.expenseDao().getAllRecordsSnapshot().single()
        assertEquals(100.5, expense.amount, 0.001)
        assertEquals("Fuel", expense.category)
        assertEquals(accountId, expense.accountId)
        assertEquals("CONFIRMED", db.captureDao().getById(id)!!.status)
    }

    @Test
    fun confirmDoesNothingWithoutAKnownAccount() = runBlocking {
        val id = readyCapture("435476373861", withAccount = false)

        assertFalse(handler.confirm(id))

        assertTrue(db.expenseDao().getAllRecordsSnapshot().isEmpty())
        assertEquals("PENDING", db.captureDao().getById(id)!!.status)
    }

    @Test
    fun confirmingTwiceSavesOnlyOnce() = runBlocking {
        val id = readyCapture("435476373861")
        handler.confirm(id)

        assertFalse(handler.confirm(id))
        assertEquals(1, db.expenseDao().getAllRecordsSnapshot().size)
    }

    @Test
    fun notMineDismissesTheCapture() = runBlocking {
        val id = readyCapture("435476373861")

        handler.dismiss(id)

        assertEquals("DISMISSED", db.captureDao().getById(id)!!.status)
        assertTrue(db.expenseDao().getAllRecordsSnapshot().isEmpty())
    }

    @Test
    fun aReadyCaptureGetsANotificationWithTwoButtonsAndAPrivateLockScreenVersion() = runBlocking {
        val id = readyCapture("435476373861")

        notifier.notify(id)

        val posted = awaitCaptureNotifications(untilCount = 1).single().notification
        assertEquals(listOf("Confirm", "Not mine"), posted.actions.map { it.title.toString() })
        assertTrue(posted.extras.getCharSequence("android.title").toString().contains("100.50"))
        assertEquals(android.app.Notification.VISIBILITY_PRIVATE, posted.visibility)
        assertEquals("A captured transaction is waiting", posted.publicVersion.extras.getCharSequence("android.title").toString())
    }

    @Test
    fun aCaptureWithoutAKnownAccountGetsNoNotification() = runBlocking {
        val id = readyCapture("435476373861", withAccount = false)

        notifier.notify(id)

        // Nothing should ever appear, so give a wrongly-posted one time to show up before judging.
        Thread.sleep(1_000)
        assertTrue(activeCaptureNotifications().isEmpty())
    }

    @Test
    fun cancelRemovesTheNotification() = runBlocking {
        val id = readyCapture("435476373861")
        notifier.notify(id)
        assertEquals(1, awaitCaptureNotifications(untilCount = 1).size)

        notifier.cancel(id)

        assertEquals(0, awaitCaptureNotifications(untilCount = 0).size)
    }
}
