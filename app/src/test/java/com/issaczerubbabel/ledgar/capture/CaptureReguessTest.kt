package com.issaczerubbabel.ledgar.capture

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.capture.categorize.CategorizationPipeline
import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.capture.categorize.KeywordDictionary
import com.issaczerubbabel.ledgar.capture.pair.LedgerMatchFinder
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
            db.unparsedAlertDao(), RoomCaptureHistorySource(db.expenseDao(), db.accountAliasDao()),
            LedgerMatchFinder(db.expenseDao(), db.captureDao())
        )
        repository = CaptureRepositoryImpl(
            db, db.captureDao(), db.merchantRuleDao(), db.accountAliasDao(), db.expenseDao(), db.unparsedAlertDao(), ingestor
        )
    }

    @After
    fun tearDown() = db.close()

    /** An HDFC alert for ₹[amount] paid to [payee] on 26 Sep 2026 at [time]. */
    private suspend fun alert(amount: Int, time: String, payee: String = "OFFICE CANTEEN", account: String = "1234"): Long =
        captured(amount, time, payee, account).id

    private suspend fun captured(amount: Int, time: String, payee: String = "OFFICE CANTEEN", account: String = "1234"): IngestResult.Captured {
        val (h, m) = time.split(":").map(String::toInt)
        val arrived = LocalDate.of(2026, 9, 26).atTime(LocalTime.of(h, m)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val text = "Sent Rs.$amount.00\nFrom HDFC Bank A/C *$account\nTo $payee\nOn 26/09/26\nRef ${ref++}\nNot You?"
        return ingestor.ingest(CaptureSource.NOTIFICATION, "HDFC Bank", text, arrived) as IngestResult.Captured
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

    // A saved rule naming a Category the user no longer has (#158).

    private suspend fun saveRule(category: String) = db.merchantRuleDao().upsert(
        MerchantRule(
            merchantNorm = "OFFICE CANTEEN", direction = "DEBIT", category = category, type = TransactionType.EXPENSE,
            origin = MerchantRuleOrigin.LEARNED, updatedAt = 0
        )
    )

    private suspend fun removeCategory(name: String) {
        val option = db.dropdownOptionDao().getOptionsByType(TransactionType.EXPENSE_CATEGORY_OPTION).first().first { it.name == name }
        db.dropdownOptionDao().delete(option)
    }

    @Test
    fun aRuleNamingARemovedCategoryIsNeverHighAndNeverNotifies() = runBlocking {
        saveRule("Canteen") // renamed to Food in the Sheet since

        val captured = captured(80, "12:50")

        assertEquals(ConfidenceBand.LOW, captured.band)
        assertFalse(captured.shouldNotify)
        assertNull(db.captureDao().getById(captured.id)!!.suggestedCategory)
        assertEquals("R11 · Saved rule's Category Canteen no longer exists", trace(captured.id).whyText)
    }

    @Test
    fun aWaitingCaptureLosesARulesCategoryOnTheNextPassOnceItIsRemoved() = runBlocking {
        saveRule("Snacks")
        val waiting = captured(80, "12:50")
        assertTrue("a current Category is High and notifies", waiting.shouldNotify)

        removeCategory("Snacks")
        ingestor.reguessPending()

        val capture = db.captureDao().getById(waiting.id)!!
        assertNull(capture.suggestedCategory)
        assertEquals("R11", capture.decidedBy)
        assertEquals("Saved rule's Category Snacks no longer exists", trace(waiting.id).why)
    }

    @Test
    fun confirmingWithACurrentCategoryReteachesTheRule() = runBlocking {
        saveRule("Canteen")

        // Two overrides in a row replace a learned rule's outcome, and three agreeing Confirms learn the new one.
        repeat(4) { confirm(alert(80 + it, "12:5$it"), "Food", "Lunch") }

        val rule = db.merchantRuleDao().get("OFFICE CANTEEN", "DEBIT")!!
        assertEquals("Food", rule.category)
        assertEquals(MerchantRuleOrigin.LEARNED, rule.origin)
        assertEquals("the rename is kept", "Lunch", rule.description)
        val next = captured(90, "13:30")
        assertEquals(ConfidenceBand.HIGH, next.band)
        assertEquals("Food", db.captureDao().getById(next.id)!!.suggestedCategory)
    }
}
