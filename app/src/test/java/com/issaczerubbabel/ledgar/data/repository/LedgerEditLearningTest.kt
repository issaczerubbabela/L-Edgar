package com.issaczerubbabel.ledgar.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.capture.LedgerEditLearner
import com.issaczerubbabel.ledgar.capture.categorize.MerchantRuleTeacher
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRuleOrigin
import com.issaczerubbabel.ledgar.data.preferences.SyncStateRepository
import com.issaczerubbabel.ledgar.data.preferences.ThemePreferenceRepository
import com.issaczerubbabel.ledgar.data.remote.ApiService
import com.issaczerubbabel.ledgar.sync.FundsRestorer
import com.issaczerubbabel.ledgar.sync.RoomTransactionSyncStore
import com.issaczerubbabel.ledgar.sync.SheetTransactionMapper
import com.issaczerubbabel.ledgar.sync.TransactionSyncer
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
import java.lang.reflect.Proxy

/**
 * Ledger edits teach (#143): changing the type or Category of a Transaction that came from a
 * capture counts on its merchant's rule exactly as a Confirm with that outcome would. Driven through
 * ExpenseRepositoryImpl, which every Ledger edit goes through.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class LedgerEditLearningTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var captures: CaptureRepositoryImpl
    private lateinit var ledger: ExpenseRepositoryImpl
    private var hdfc = 0L
    private var cub = 0L
    private var hash = 0
    private var reguessed = 0
    private var failReguess = false

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        captures = CaptureRepositoryImpl(
            db, db.captureDao(), db.merchantRuleDao(), db.accountAliasDao(), db.expenseDao(), db.unparsedAlertDao(), {}
        )
        // Nothing here reaches the network: any call to the Apps Script fails the test.
        val api = Proxy.newProxyInstance(ApiService::class.java.classLoader, arrayOf(ApiService::class.java)) { _, method, _ ->
            error("No network in this test: ${method.name}")
        } as ApiService
        val store = RoomTransactionSyncStore(db, SheetTransactionMapper(db.accountDao()))
        ledger = ExpenseRepositoryImpl(
            db.expenseDao(), db.accountDao(), api, DropdownOptionRepositoryImpl(db.dropdownOptionDao()), db.budgetDao(),
            BucketBudgetRepositoryImpl(db, db.bucketBudgetDao()),
            FundsRestorer(api, FundRepositoryImpl(db, db.fundDao()), db), ThemePreferenceRepository(context),
            TransactionSyncer(store, api), store, SyncStateRepository(context),
            LedgerEditLearner(db, db.captureDao(), db.expenseDao(), MerchantRuleTeacher(db.merchantRuleDao())) {
                if (failReguess) error("re-guess failed")
                reguessed++
            }
        )
        hdfc = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "HDFC Savings", initialBalance = 0.0))
        cub = db.accountDao().insert(AccountRecord(groupName = "Bank", accountName = "CUB Savings", initialBalance = 0.0))
    }

    @After
    fun tearDown() = db.close()

    private suspend fun learnedFoodRule() = db.merchantRuleDao().upsert(
        MerchantRule("SWIGGY", "DEBIT", "Food", TransactionType.EXPENSE, origin = MerchantRuleOrigin.LEARNED, hitCount = 3, updatedAt = 1)
    )

    private suspend fun capture(amount: Double = 450.0): CapturedTransaction {
        val id = db.captureDao().insert(
            CapturedTransaction(
                sources = "NOTIF", sender = "HDFC Bank", rawText = "Sent Rs.$amount", rawHash = "hash-${hash++}",
                capturedAt = T0, txnTime = T0, amount = amount, direction = "DEBIT", channel = "UPI",
                merchantRaw = "SWIGGY", merchantNorm = "SWIGGY", accountHint = "1234", accountId = hdfc,
                suggestedType = TransactionType.EXPENSE, suggestedCategory = "Food", confidence = 0.98
            )
        )
        return db.captureDao().getById(id)!!
    }

    /**
     * A captured Transaction confirmed as Food from To review, keeping the bank's text: a rename
     * would keep the rule, as a Candidate holding the name, once it loses its outcome.
     */
    private suspend fun confirmedSwiggy(amount: Double = 450.0): ExpenseRecord {
        val expenseId = captures.confirm(capture(amount).id, CaptureEdits(TransactionType.EXPENSE, "Food", hdfc, "SWIGGY"))!!
        return db.expenseDao().getById(expenseId)!!
    }

    private suspend fun rule() = db.merchantRuleDao().get("SWIGGY", "DEBIT")

    private suspend fun recategorise(record: ExpenseRecord, category: String) =
        ledger.update(db.expenseDao().getById(record.id)!!.copy(category = category))

    @Test
    fun recategorisingACapturedTransactionTwiceDropsALearnedRuleAsTwoInboxOverridesWould() = runBlocking {
        learnedFoodRule()
        val swiggy = confirmedSwiggy()
        assertEquals(MerchantRuleOrigin.LEARNED, rule()!!.origin)

        recategorise(swiggy, "Groceries")
        assertEquals("one override keeps it", MerchantRuleOrigin.LEARNED, rule()!!.origin)
        assertEquals("Food", rule()!!.category)

        recategorise(swiggy, "Snacks")
        assertNull("two overrides drop it", rule())
        assertEquals("Snacks", db.expenseDao().getById(swiggy.id)!!.category)
        assertEquals(2, reguessed)
    }

    @Test
    fun aFailureAfterLearningNeverFailsTheEditOrLosesTheLesson() = runBlocking {
        learnedFoodRule()
        val swiggy = confirmedSwiggy()
        failReguess = true

        recategorise(swiggy, "Groceries")

        assertEquals("Groceries", db.expenseDao().getById(swiggy.id)!!.category)
        assertEquals("the rule's own transaction committed: one override", 1, rule()!!.streak)
    }

    @Test
    fun theLedgersBatchChangeCategoryTeachesForEachCapturedTransaction() = runBlocking {
        learnedFoodRule()
        val first = confirmedSwiggy(450.0)
        val second = confirmedSwiggy(300.0)

        ledger.updateTransactionsCategoryByIds(listOf(first.id, second.id), "Groceries", TransactionType.EXPENSE)

        assertNull(rule())
    }

    @Test
    fun movingBackToTheRulesCategoryIsAConfirmationOfIt() = runBlocking {
        learnedFoodRule()
        val swiggy = confirmedSwiggy()

        recategorise(swiggy, "Groceries")
        recategorise(swiggy, "Food")
        recategorise(swiggy, "Groceries")

        assertEquals("the streak of overrides restarted", MerchantRuleOrigin.LEARNED, rule()!!.origin)
    }

    @Test
    fun amountDateAndDescriptionEditsTeachNothing() = runBlocking {
        learnedFoodRule()
        val swiggy = confirmedSwiggy()
        val before = rule()

        ledger.update(db.expenseDao().getById(swiggy.id)!!.copy(amount = 999.0))
        ledger.update(db.expenseDao().getById(swiggy.id)!!.copy(date = "2025-01-01"))
        ledger.update(db.expenseDao().getById(swiggy.id)!!.copy(description = "Dinner with friends"))
        ledger.updateTransactionsDescriptionByIds(listOf(swiggy.id), "Late dinner")
        ledger.updateTransactionsDateByIds(listOf(swiggy.id), "2025-02-01")

        assertEquals(before, rule())
        assertEquals(0, reguessed)
    }

    @Test
    fun switchingItToATransferTeachesATransferToTheOtherAccount() = runBlocking {
        val swiggy = confirmedSwiggy()

        ledger.update(
            db.expenseDao().getById(swiggy.id)!!.copy(
                type = TransactionType.TRANSFER, category = TransactionType.TRANSFER, accountId = null,
                fromAccountId = hdfc, toAccountId = cub
            )
        )

        val rule = rule()!!
        assertEquals(TransactionType.TRANSFER, rule.type)
        assertEquals(cub, rule.toAccountId)
        assertEquals("one confirmation of Food, then one of a Transfer", MerchantRuleOrigin.CANDIDATE, rule.origin)
    }

    @Test
    fun aTransactionLoggedByHandWithNoCaptureTeachesNothing() = runBlocking {
        learnedFoodRule()
        val id = db.expenseDao().insert(
            ExpenseRecord(date = "2026-11-14", type = TransactionType.EXPENSE, category = "Food", description = "Swiggy", amount = 450.0, accountId = hdfc, remarks = "")
        )

        ledger.update(db.expenseDao().getById(id)!!.copy(category = "Groceries"))
        ledger.update(db.expenseDao().getById(id)!!.copy(category = "Snacks"))

        assertEquals(MerchantRuleOrigin.LEARNED, rule()!!.origin)
    }

    @Test
    fun aLinkedCaptureTeachesFromItsTransactionToo() = runBlocking {
        learnedFoodRule()
        val c = capture()
        val id = db.expenseDao().insert(
            ExpenseRecord(date = "2026-11-14", type = TransactionType.EXPENSE, category = "Food", description = "Swiggy", amount = 450.0, accountId = hdfc, remarks = "")
        )
        db.captureDao().markLinked(c.id, id, "Food")

        ledger.update(db.expenseDao().getById(id)!!.copy(category = "Groceries"))
        ledger.update(db.expenseDao().getById(id)!!.copy(category = "Snacks"))

        assertNull(rule())
    }

    private companion object {
        const val T0 = 1_760_000_000_000L
    }
}
