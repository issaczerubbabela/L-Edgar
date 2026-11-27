package com.issaczerubbabel.ledgar.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.issaczerubbabel.ledgar.data.bucket.RestoredCycle
import com.issaczerubbabel.ledgar.data.bucket.StartCycleRequest
import com.issaczerubbabel.ledgar.data.bucket.StartCycleResult
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.data.remote.AccountImportResponse
import com.issaczerubbabel.ledgar.data.remote.ApiService
import com.issaczerubbabel.ledgar.data.remote.BucketBudgetImportResponse
import com.issaczerubbabel.ledgar.data.remote.BudgetImportResponse
import com.issaczerubbabel.ledgar.data.remote.DropdownImportResponse
import com.issaczerubbabel.ledgar.data.remote.FundImportDto
import com.issaczerubbabel.ledgar.data.remote.FundSyncDto
import com.issaczerubbabel.ledgar.data.remote.FundsImportResponse
import com.issaczerubbabel.ledgar.data.remote.ImportResponse
import com.issaczerubbabel.ledgar.data.remote.SyncRequest
import com.issaczerubbabel.ledgar.data.remote.SyncResponse
import com.issaczerubbabel.ledgar.sync.FundsRestorer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class FundRepositoryTest {

    private lateinit var db: SheetSyncDatabase
    private lateinit var funds: FundRepositoryImpl
    private lateinit var buckets: BucketBudgetRepositoryImpl
    private val sheet = FakeFundSheet()
    private lateinit var restorer: FundsRestorer

    private val accounts = listOf(
        AccountRecord(groupName = "Accounts", accountName = "HDFC", initialBalance = 100_000.0, displayOrder = 0),
        AccountRecord(groupName = "Savings", accountName = "Emergency Fund RD", initialBalance = 30_000.0, displayOrder = 1),
        AccountRecord(groupName = "Accounts", accountName = "Ministry Fund", initialBalance = 5_000.0, displayOrder = 2)
    )
    private val cycles = listOf(
        RestoredCycle("2026-08-26", "2026-09-24", 110_000.0, closedAt = "2026-09-25", buckets = emptyList()),
        RestoredCycle("2026-09-25", "2026-10-24", 115_842.0, closedAt = null, buckets = emptyList())
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), SheetSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        funds = FundRepositoryImpl(db, db.fundDao())
        buckets = BucketBudgetRepositoryImpl(db, db.bucketBudgetDao())
        restorer = FundsRestorer(sheet, funds, db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun accountId(name: String) = db.accountDao().getAllAccountsSnapshot().single { it.accountName == name }.id
    private suspend fun cycleId(start: String) = db.bucketBudgetDao().getAllCycles().single { it.startDate == start }.id

    /** Emergency holds the RD whole and ₹70,000 of HDFC, Ministry holds its Account; both plan contributions. */
    private suspend fun givenTheUsersFunds() {
        accounts.forEach { db.accountDao().insert(it) }
        buckets.replaceAllFromBackup(cycles)

        val emergency = funds.createFund(
            Fund(name = "Emergency", emoji = "🛟", colorIndex = 2, targetAmount = 100_000.0, dueDate = "2027-03-31", createdDate = "2026-09-01")
        )
        val ministry = funds.createFund(Fund(name = "Ministry", colorIndex = 5, sortOrder = 1, createdDate = "2026-09-01"))
        funds.createFund(Fund(name = "Laptop", sortOrder = 2, archivedAt = "2026-10-01", createdDate = "2026-06-01"))

        saved(funds.saveHolding(FundHolding(fundId = emergency, accountId = accountId("Emergency Fund RD"), kind = HoldingKind.WHOLE.name, openingAmount = 30_000.0, startDate = "2026-09-01")))
        saved(funds.saveHolding(FundHolding(fundId = emergency, accountId = accountId("HDFC"), kind = HoldingKind.FIXED.name, amount = 70_000.0, openingAmount = 70_000.0, startDate = "2026-09-01")))
        saved(funds.saveHolding(FundHolding(fundId = ministry, accountId = accountId("Ministry Fund"), kind = HoldingKind.WHOLE.name, startDate = "2026-09-15")))
        funds.setPlannedContribution(emergency, cycleId("2026-08-26"), 15_000.0)
        funds.setPlannedContribution(emergency, cycleId("2026-09-25"), 15_000.0)
        funds.setPlannedContribution(ministry, cycleId("2026-09-25"), 5_000.0)
    }

    private fun saved(result: HoldingResult) = assertTrue("$result", result is HoldingResult.Saved)

    /** What Import rebuilds: every Account and cycle comes back under a new id, taking holdings and plans with them. */
    private suspend fun importRebuildsAccountsAndCycles() {
        db.accountDao().overwriteAll(accounts.reversed())
        buckets.replaceAllFromBackup(cycles.reversed())
    }

    /** The backup without Fund ids, which only mean something on one phone. */
    private fun List<FundSyncDto>.withoutIds() = map { it.copy(id = 0) }

    @Test
    fun backupThenWipeThenImportRestoresEveryFundHoldingAndPlan() = runBlocking {
        givenTheUsersFunds()
        val backedUp = funds.getBackup()
        assertEquals(listOf("Emergency", "Ministry", "Laptop"), backedUp.map { it.name })
        assertEquals(listOf("Emergency Fund RD", "HDFC"), backedUp[0].holdings.map { it.accountName })
        assertEquals(listOf("2026-08-26", "2026-09-25"), backedUp[0].plans.map { it.cycleStartDate })
        sheet.store(backedUp)

        // A fresh install: nothing of the phone's own survives.
        db.fundDao().deleteAllFunds()
        importRebuildsAccountsAndCycles()
        val restored = restorer.restore(URL, localBeforeImport = emptyList(), today = TODAY)

        assertEquals(3, restored)
        assertEquals(backedUp.withoutIds(), funds.getBackup().withoutIds())
        val rd = funds.getSnapshot().holdings.first { it.holdingKind == HoldingKind.WHOLE }
        assertEquals("the holding points at the rebuilt Account", accountId("Emergency Fund RD"), rd.accountId)
    }

    @Test
    fun anImportFromASheetWithoutFundsPutsThePhonesOwnFundsBack() = runBlocking {
        givenTheUsersFunds()
        val before = funds.getBackup()
        sheet.understood = false // a script deployed before Funds

        val local = restorer.keepLocal()
        importRebuildsAccountsAndCycles()
        assertTrue("rebuilding Accounts and cycles took the holdings and plans", funds.getSnapshot().holdings.isEmpty())
        assertEquals(0, restorer.restore(URL, local, TODAY))

        assertEquals(before.withoutIds(), funds.getBackup().withoutIds())
    }

    @Test
    fun aFailedReadStillPutsThePhonesFundsBackBeforeReportingIt() = runBlocking {
        givenTheUsersFunds()
        val before = funds.getBackup()
        val local = restorer.keepLocal()
        importRebuildsAccountsAndCycles()
        sheet.failing = true

        val error = runCatching { restorer.restore(URL, local, TODAY) }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals(before.withoutIds(), funds.getBackup().withoutIds())
    }

    @Test
    fun anAccountIsTheWholeHoldingOfOneFundAtMost() = runBlocking {
        accounts.forEach { db.accountDao().insert(it) }
        val emergency = funds.createFund(Fund(name = "Emergency", createdDate = "2026-09-01"))
        val insurance = funds.createFund(Fund(name = "Insurance", createdDate = "2026-09-01"))
        val rd = accountId("Emergency Fund RD")
        val hdfc = accountId("HDFC")

        saved(funds.saveHolding(FundHolding(fundId = emergency, accountId = rd, kind = HoldingKind.WHOLE.name, startDate = "2026-09-01")))
        assertEquals(HoldingResult.AccountHeldWhole(emergency),
            funds.saveHolding(FundHolding(fundId = insurance, accountId = rd, kind = HoldingKind.WHOLE.name, startDate = "2026-09-01")))
        assertEquals(HoldingResult.AccountHeldWhole(emergency),
            funds.saveHolding(FundHolding(fundId = insurance, accountId = rd, kind = HoldingKind.FIXED.name, amount = 1.0, startDate = "2026-09-01")))
        assertEquals(HoldingResult.AlreadyInFund,
            funds.saveHolding(FundHolding(fundId = emergency, accountId = rd, kind = HoldingKind.FIXED.name, amount = 1.0, startDate = "2026-09-01")))

        // Fixed amounts of a shared Account can sit in several Funds, but then no Fund can hold it whole.
        saved(funds.saveHolding(FundHolding(fundId = emergency, accountId = hdfc, kind = HoldingKind.FIXED.name, amount = 70_000.0, startDate = "2026-09-01")))
        saved(funds.saveHolding(FundHolding(fundId = insurance, accountId = hdfc, kind = HoldingKind.FIXED.name, amount = 5_000.0, startDate = "2026-09-01")))
        val insuranceShare = funds.getSnapshot().holdings.single { it.fundId == insurance && it.accountId == hdfc }
        assertEquals(HoldingResult.AccountHasOtherHoldings, funds.saveHolding(insuranceShare.copy(kind = HoldingKind.WHOLE.name)))

        // A WHOLE holding never keeps an amount.
        val whole = funds.getSnapshot().holdings.single { it.accountId == rd }
        saved(funds.saveHolding(whole.copy(amount = 999.0)))
        assertEquals(0.0, funds.getSnapshot().holdings.single { it.accountId == rd }.amount, 0.0)
    }

    @Test
    fun aPlannedContributionOfZeroIsRemovedAndDeletingAFundTakesItsHoldingsAndPlans() = runBlocking {
        givenTheUsersFunds()
        val ministry = funds.observeFunds().first().single { it.name == "Ministry" }.id
        funds.setPlannedContribution(ministry, cycleId("2026-09-25"), 0.0)
        assertEquals(listOf(15_000.0), funds.getSnapshot().plans.filter { it.cycleId == cycleId("2026-09-25") }.map { it.plannedAmount })

        val emergency = funds.observeFunds().first().single { it.name == "Emergency" }.id
        funds.deleteFund(emergency)
        val left = funds.getSnapshot()
        assertTrue(left.holdings.none { it.fundId == emergency })
        assertTrue(left.plans.isEmpty())
    }

    @Test
    fun startCycleWithCarryOverCopiesPlannedContributionsOfActiveFunds() = runBlocking {
        givenTheUsersFunds()
        val laptop = funds.observeFunds().first().single { it.name == "Laptop" }.id
        funds.setPlannedContribution(laptop, cycleId("2026-09-25"), 1_000.0) // archived: not carried

        val result = buckets.startCycle(
            StartCycleRequest(LocalDate.parse("2026-10-25"), LocalDate.parse("2026-11-24"), 115_842.0, carryOverBuckets = true),
            today = LocalDate.parse("2026-10-25")
        )

        val newCycle = (result as StartCycleResult.Started).cycleId
        val plans = funds.getSnapshot().plans.filter { it.cycleId == newCycle }
        val names = funds.observeFunds().first().associate { it.id to it.name }
        assertEquals(setOf("Emergency" to 15_000.0, "Ministry" to 5_000.0), plans.map { names.getValue(it.fundId) to it.plannedAmount }.toSet())
    }

    @Test
    fun startCycleWithCarryOverCopiesBucketsAndPlannedContributionsTogether() = runBlocking {
        givenTheUsersFunds()
        db.bucketBudgetDao().insertBucket(
            BudgetBucket(cycleId = cycleId("2026-09-25"), name = "Essentials", colorIndex = 0, allocatedAmount = 60_000.0, sortOrder = 0)
        )

        val result = buckets.startCycle(
            StartCycleRequest(LocalDate.parse("2026-10-25"), LocalDate.parse("2026-11-24"), 115_842.0, carryOverBuckets = true),
            today = LocalDate.parse("2026-10-25")
        )

        val newCycle = (result as StartCycleResult.Started).cycleId
        assertEquals(listOf("Essentials" to 60_000.0), db.bucketBudgetDao().getBuckets(newCycle).map { it.name to it.allocatedAmount })
        assertEquals(20_000.0, funds.getSnapshot().plansFor(newCycle).sumOf { it.plannedAmount }, 0.001)
    }

    @Test
    fun startCycleWithoutCarryOverStartsWithNoPlannedContributions() = runBlocking {
        givenTheUsersFunds()
        val result = buckets.startCycle(
            StartCycleRequest(LocalDate.parse("2026-10-25"), LocalDate.parse("2026-11-24"), 115_842.0, carryOverBuckets = false),
            today = LocalDate.parse("2026-10-25")
        )
        val newCycle = (result as StartCycleResult.Started).cycleId
        assertTrue(funds.getSnapshot().plans.none { it.cycleId == newCycle })
    }

    private companion object {
        const val URL = "https://script.example/exec"
        val TODAY: LocalDate = LocalDate.of(2026, 11, 27)
    }
}

/** The Sheet's Fund tabs: a backup stored as JSON and read back the way the app parses the script's reply. */
private class FakeFundSheet : ApiService {
    var understood = true
    var failing = false
    private var json = "[]"
    private val gson = Gson()

    fun store(backup: List<FundSyncDto>) {
        json = gson.toJson(SyncRequest(action = "backup", target = "funds", funds = backup).funds)
    }

    override suspend fun importFunds(url: String, target: String): Response<FundsImportResponse> {
        if (failing) return Response.error(500, "".toResponseBody())
        val data = gson.fromJson(json, Array<FundImportDto>::class.java).toList()
        return Response.success(
            if (understood) FundsImportResponse(status = "ok", type = FundsImportResponse.TYPE, data = data)
            else FundsImportResponse(status = "ok", data = emptyList())
        )
    }

    override suspend fun syncRecords(url: String, request: SyncRequest): Response<SyncResponse> = error("unused")
    override suspend fun importRecords(url: String, target: String): Response<ImportResponse> = error("unused")
    override suspend fun importDropdownOptions(url: String, target: String): Response<DropdownImportResponse> = error("unused")
    override suspend fun importBudgets(url: String, target: String): Response<BudgetImportResponse> = error("unused")
    override suspend fun importBucketBudgets(url: String, target: String): Response<BucketBudgetImportResponse> = error("unused")
    override suspend fun importAccounts(url: String, target: String): Response<AccountImportResponse> = error("unused")
}
