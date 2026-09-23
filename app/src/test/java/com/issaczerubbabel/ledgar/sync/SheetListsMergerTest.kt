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
import com.issaczerubbabel.ledgar.data.remote.BudgetImportResponse
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
        val merger = SheetListsMerger(sheet, db, SyncStateRepository(ApplicationProvider.getApplicationContext()))
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
        assertEquals(
            listOf("Food", "Side hustle"),
            db.dropdownOptionDao().getAllOptionsSnapshot().map { it.name }
        )
        assertTrue(db.dropdownOptionDao().getAllOptionsSnapshot().none { it.optionType == "PAYMENT_MODE" })
        assertEquals(listOf("2026-09" to 5000.0), db.budgetDao().getAllBudgetsSnapshot().map { it.monthYear to it.amount })

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
            AccountImportDto(groupName = "Cash", accountName = "Wallet", initialBalance = 1000.0)
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

    override suspend fun syncRecords(url: String, request: SyncRequest): Response<SyncResponse> = error("unused")
    override suspend fun importRecords(url: String, target: String): Response<ImportResponse> = error("unused")
}
