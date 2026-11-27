package com.issaczerubbabel.ledgar.sync

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.preferences.SyncStateRepository
import com.issaczerubbabel.ledgar.data.remote.AccountImportDto
import com.issaczerubbabel.ledgar.data.remote.AccountImportResponse
import com.issaczerubbabel.ledgar.data.remote.ApiService
import com.issaczerubbabel.ledgar.data.remote.BudgetImportDto
import com.issaczerubbabel.ledgar.data.remote.BucketBudgetImportResponse
import com.issaczerubbabel.ledgar.data.remote.BudgetImportResponse
import com.issaczerubbabel.ledgar.data.remote.CycleImportDto
import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepositoryImpl
import com.issaczerubbabel.ledgar.data.repository.FundRepositoryImpl
import com.issaczerubbabel.ledgar.data.remote.FundHoldingImportDto
import com.issaczerubbabel.ledgar.data.remote.FundImportDto
import com.issaczerubbabel.ledgar.data.remote.FundPlanImportDto
import com.issaczerubbabel.ledgar.data.remote.FundsImportResponse
import com.issaczerubbabel.ledgar.data.remote.DropdownImportDto
import com.issaczerubbabel.ledgar.data.remote.DropdownImportResponse
import com.issaczerubbabel.ledgar.data.remote.ImportResponse
import com.issaczerubbabel.ledgar.data.remote.SyncRequest
import com.issaczerubbabel.ledgar.data.remote.SyncResponse
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SheetListsMergerTest {

    private lateinit var db: SheetSyncDatabase
    private val sheet = FakeSheetLists()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a fresh install takes in the Sheet's lists once, before its first Backup could replace them`() = runBlocking {
        val buckets = BucketBudgetRepositoryImpl(db, db.bucketBudgetDao())
        val funds = FundRepositoryImpl(db, db.fundDao())
        val merger = SheetListsMerger(
            sheet, db, buckets, FundsRestorer(sheet, funds, db), SyncStateRepository(ApplicationProvider.getApplicationContext())
        )
        // A fresh install: seeded categories and the fallback account created for imports.
        db.dropdownOptionDao().insert(DropdownOption(optionType = "EXPENSE_CATEGORY", name = "Food", displayOrder = 0))
        db.accountDao().insert(AccountRecord(groupName = "Cash", accountName = "Cash", initialBalance = 0.0))

        sheet.failing = true
        try {
            merger.mergeOnce(URL)
            fail("a failed read must not count as merged")
        } catch (expected: IllegalStateException) {
        }

        sheet.failing = false
        merger.mergeOnce(URL)

        assertEquals(listOf("Cash", "Wallet"), db.accountDao().getAllAccountsSnapshot().map { it.accountName })
        assertEquals(listOf(null, "2026-10-06"), db.accountDao().getAllAccountsSnapshot().map { it.reconciledAt })
        assertEquals(
            listOf("Food", "Side hustle"),
            db.dropdownOptionDao().getAllOptionsSnapshot().map { it.name }
        )
        assertTrue(db.dropdownOptionDao().getAllOptionsSnapshot().none { it.optionType == "PAYMENT_MODE" })
        assertEquals(listOf("2026-09" to 5000.0), db.budgetDao().getAllBudgetsSnapshot().map { it.monthYear to it.amount })
        assertEquals("the Sheet's salary cycle comes in when the phone has none",
            listOf("2026-09-01"), buckets.getBackupSnapshot().cycles.map { it.startDate })
        val wallet = db.accountDao().getAllAccountsSnapshot().single { it.accountName == "Wallet" }
        val cycle = buckets.getBackupSnapshot().cycles.single()
        val taken = funds.getSnapshot()
        assertEquals("the Sheet's Funds come in after the Accounts and cycles they name",
            listOf("Trip"), taken.funds.map { it.name })
        assertEquals(listOf(wallet.id), taken.holdings.map { it.accountId })
        assertEquals(listOf(cycle.id to 2000.0), taken.plans.map { it.cycleId to it.plannedAmount })

        val readsSoFar = sheet.reads
        merger.mergeOnce(URL)
        assertEquals("it only ever runs once", readsSoFar, sheet.reads)
    }

    private companion object {
        const val URL = "https://script.example/exec"
    }
}

private class FakeSheetLists : ApiService {
    var failing = false
    var reads = 0

    private fun <T> reply(body: T): Response<T> {
        reads++
        return if (failing) Response.error(500, "".toResponseBody()) else Response.success(body)
    }

    override suspend fun importAccounts(url: String, target: String) = reply(
        AccountImportResponse(status = "ok", data = listOf(
            AccountImportDto(groupName = "Cash", accountName = "cash", initialBalance = 0.0),
            AccountImportDto(groupName = "Cash", accountName = "Wallet", initialBalance = 1000.0, reconciledAt = "2026-10-06")
        ))
    )

    override suspend fun importDropdownOptions(url: String, target: String) = reply(
        DropdownImportResponse(status = "ok", data = listOf(
            DropdownImportDto(optionType = "EXPENSE_CATEGORY", name = "food", displayOrder = 0),
            DropdownImportDto(optionType = "INCOME_CATEGORY", name = "Side hustle", displayOrder = 1),
            DropdownImportDto(optionType = "PAYMENT_MODE", name = "UPI", displayOrder = 2)
        ))
    )

    override suspend fun importBudgets(url: String, target: String) = reply(
        BudgetImportResponse(status = "ok", data = listOf(BudgetImportDto(monthYear = "2026-09", category = "Food", amount = 5000.0)))
    )

    override suspend fun importBucketBudgets(url: String, target: String) = reply(
        BucketBudgetImportResponse(
            status = "ok",
            type = BucketBudgetImportResponse.TYPE,
            data = listOf(CycleImportDto(startDate = "2026-09-01", endDate = "2026-09-30", spendableAmount = 40000.0))
        )
    )

    override suspend fun importFunds(url: String, target: String) = reply(
        FundsImportResponse(
            status = "ok",
            type = FundsImportResponse.TYPE,
            data = listOf(
                FundImportDto(
                    name = "Trip",
                    createdDate = "2026-09-01",
                    holdings = listOf(FundHoldingImportDto(accountName = "wallet", kind = "WHOLE", startDate = "2026-09-01")),
                    plans = listOf(FundPlanImportDto(cycleStartDate = "2026-09-01", plannedAmount = 2000.0))
                )
            )
        )
    )

    override suspend fun syncRecords(url: String, request: SyncRequest): Response<SyncResponse> = error("unused")
    override suspend fun importRecords(url: String, target: String): Response<ImportResponse> = error("unused")
}
