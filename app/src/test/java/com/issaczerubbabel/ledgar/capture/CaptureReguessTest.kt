package com.issaczerubbabel.ledgar.capture

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.capture.categorize.CategorizationPipeline
import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.capture.categorize.KeywordDictionary
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountAlias
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.repository.CaptureEdits
import com.issaczerubbabel.ledgar.data.repository.CaptureRepositoryImpl
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepositoryImpl
import com.issaczerubbabel.ledgar.util.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** The predictor end to end on Room: ingest, Confirm, and the re-guess that follows. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CaptureReguessTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var ingestor: CaptureIngestor
    private lateinit var repository: CaptureRepositoryImpl
    private var accountId = 0L
    private var ref = 66353123456700L

    private val settings = object : CaptureSettingsSource {
        override suspend fun isEnabled() = true
        override suspend fun categoryMapping() = emptyMap<String, String>()
    }

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        accountId = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "HDFC Savings", initialBalance = 0.0))
        db.accountAliasDao().upsert(AccountAlias("1234", accountId))
        db.dropdownOptionDao().insertAll(
            listOf("Food", "Snacks", "Groceries").mapIndexed { i, n ->
                DropdownOption(optionType = TransactionType.EXPENSE_CATEGORY_OPTION, name = n, displayOrder = i)
            }
        )
        val pipeline = CategorizationPipeline(KeywordDictionary(emptyMap())) { db.merchantRuleDao().get(it) }
        ingestor = CaptureIngestor(
            db.captureDao(), db.accountAliasDao(), DropdownOptionRepositoryImpl(db.dropdownOptionDao()), pipeline, settings,
            db.unparsedAlertDao(), RoomCaptureHistorySource(db.expenseDao(), db.accountAliasDao())
        )
        repository = CaptureRepositoryImpl(
            db, db.captureDao(), db.merchantRuleDao(), db.accountAliasDao(), db.expenseDao(), db.unparsedAlertDao(), ingestor
        )
    }

    @After
    fun tearDown() = db.close()

    /** An HDFC alert for ₹[amount] paid to [payee] on 26 Sep 2026 at [time]. */
    private suspend fun alert(amount: Int, time: String, payee: String = "OFFICE CANTEEN", account: String = "1234"): Long {
        val (h, m) = time.split(":").map(String::toInt)
        val arrived = LocalDate.of(2026, 9, 26).atTime(LocalTime.of(h, m)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val text = "Sent Rs.$amount.00\nFrom HDFC Bank A/C *$account\nTo $payee\nOn 26/09/26\nRef ${ref++}\nNot You?"
        return (ingestor.ingest(CaptureSource.NOTIFICATION, "HDFC Bank", text, arrived) as IngestResult.Captured).id
    }

    private suspend fun confirm(id: Long, category: String, description: String) =
        repository.confirm(id, CaptureEdits(TransactionType.EXPENSE, category, accountId, description))

    private suspend fun trace(id: Long) = CaptureTrace.parse(db.captureDao().getById(id)!!.traceJson)

    @Test
    fun confirmingOneCaptureReguessesTheOthersFromTheSameMerchant() = runBlocking {
        val first = alert(80, "12:50")
        val waiting = alert(80, "13:05")
        assertNull(db.captureDao().getById(waiting)!!.suggestedCategory)

        confirm(first, "Food", "Lunch")

        val reguessed = db.captureDao().getById(waiting)!!
        assertEquals("Food", reguessed.suggestedCategory)
        assertEquals("R2", reguessed.decidedBy)
        assertEquals(ConfidenceBand.CHECK, ConfidenceBand.of(reguessed.confidence, true))
        assertEquals("Lunch", trace(waiting).descriptionGuess)
        assertEquals("Lunch", repository.titleFor(reguessed))
    }

    @Test
    fun lunchAndTeaAtOneMerchantAreToldApartByAmountAndTime() = runBlocking {
        confirm(alert(80, "13:00"), "Food", "Lunch")
        confirm(alert(30, "16:30"), "Snacks", "Tea")

        val lunch = alert(80, "12:50")
        val tea = alert(30, "16:40")

        assertEquals("Lunch", trace(lunch).descriptionGuess)
        assertEquals(listOf("Tea"), trace(lunch).descriptionAlternatives)
        // "Tea" was typed last, so it is the merchant's rename, but this merchant is described per payment.
        assertEquals("Lunch", repository.titleFor(db.captureDao().getById(lunch)!!))
        assertEquals("Tea", repository.titleFor(db.captureDao().getById(tea)!!))
    }

    @Test
    fun keepingTheSuggestedDescriptionDoesNotRenameTheMerchant() = runBlocking {
        confirm(alert(80, "13:00"), "Food", "Lunch")
        confirm(alert(30, "16:30"), "Snacks", "Tea")
        val lunch = alert(80, "12:50")

        confirm(lunch, "Food", "Lunch")

        assertEquals("Tea", db.merchantRuleDao().get("OFFICE CANTEEN", "DEBIT")!!.description)
    }

    @Test
    fun aReguessNeverTouchesACaptureConfirmedMeanwhile() = runBlocking {
        val id = alert(80, "12:50")
        confirm(id, "Food", "Lunch")
        val confirmed = db.captureDao().getById(id)!!

        db.captureDao().updateGuesses(id, TransactionType.EXPENSE, "Snacks", null, 0.5, "NB", null)

        assertEquals(confirmed, db.captureDao().getById(id))
    }

    @Test
    fun anUnknownAccountNumberFromTheBanksAppGuessesItsOnlyAccount() = runBlocking {
        alert(50, "09:00") // an earlier HDFC alert on the aliased *1234
        val id = alert(80, "12:50", account = "9999")

        val capture = db.captureDao().getById(id)!!
        assertNull("never resolved, only suggested", capture.accountId)
        assertEquals(accountId, trace(id).guessedAccountId)
        assertNull("no alias until Confirm", db.accountAliasDao().getByAlias("9999"))
    }
}
