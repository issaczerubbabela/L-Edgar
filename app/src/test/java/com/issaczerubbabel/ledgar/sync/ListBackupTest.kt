package com.issaczerubbabel.ledgar.sync

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.issaczerubbabel.ledgar.data.preferences.SyncStateRepository
import com.issaczerubbabel.ledgar.data.remote.AccountImportResponse
import com.issaczerubbabel.ledgar.data.remote.ApiService
import com.issaczerubbabel.ledgar.data.remote.BucketBudgetImportResponse
import com.issaczerubbabel.ledgar.data.remote.BudgetImportResponse
import com.issaczerubbabel.ledgar.data.remote.DropdownImportResponse
import com.issaczerubbabel.ledgar.data.remote.ImportResponse
import com.issaczerubbabel.ledgar.data.remote.SyncRequest
import com.issaczerubbabel.ledgar.data.remote.SyncResponse
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.Response

/** ADR-0010: a list Backup based on an old revision never overwrites a Sheet edit. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class ListBackupTest {

    // DataStore keeps one store per process, so each test uses its own target.
    private val syncState = SyncStateRepository(ApplicationProvider.getApplicationContext())

    @Test
    fun `each Backup is based on the revision the last one returned`() = runBlocking {
        val sheet = FakeListSheet(SyncResponse(status = "ok", revision = "r1"))
        val backup = ListBackup(sheet, syncState)

        assertEquals(ListBackup.Outcome.Written, backup.send(URL, request("dropdowns")))
        assertNull("the first Backup has no base, so it overwrites as before", sheet.sent.last().baseRevision)

        sheet.reply = SyncResponse(status = "ok", revision = "r2")
        backup.send(URL, request("dropdowns"))
        assertEquals("r1", sheet.sent.last().baseRevision)
        assertEquals("r2", syncState.listRevision("dropdowns"))
    }

    @Test
    fun `a tab changed in the Sheet holds the list until the user decides`() = runBlocking {
        syncState.acceptSheetList("accounts", "r1")
        val sheet = FakeListSheet(SyncResponse(status = "ok", sheetChanged = true, revision = "edited"))
        val backup = ListBackup(sheet, syncState)

        assertEquals(ListBackup.Outcome.SheetChanged, backup.send(URL, request("accounts")))
        assertEquals("r1", sheet.sent.last().baseRevision)
        assertTrue("accounts" in syncState.listsChangedInSheet.first())
        assertEquals("the base stays, so every later Backup is held too", "r1", syncState.listRevision("accounts"))

        syncState.overwriteListsInSheet(setOf("accounts"))
        sheet.reply = SyncResponse(status = "ok", revision = "r3")
        assertEquals(ListBackup.Outcome.Written, backup.send(URL, request("accounts")))
        assertNull("Overwrite sends no base", sheet.sent.last().baseRevision)
        assertTrue("accounts" !in syncState.listsChangedInSheet.first())
    }

    @Test
    fun `a script older than version 3 returns no revision and keeps overwriting`() = runBlocking {
        syncState.acceptSheetList("budgets", "r1")
        val sheet = FakeListSheet(SyncResponse(status = "ok", scriptVersion = 2))
        val backup = ListBackup(sheet, syncState)

        assertEquals(ListBackup.Outcome.Written, backup.send(URL, request("budgets")))
        assertNull(syncState.listRevision("budgets"))
    }

    @Test
    fun `a script that doesn't know salary cycles is reported, not counted as written`() = runBlocking {
        val sheet = FakeListSheet(SyncResponse(status = "ok", count = 0))
        val outcome = ListBackup(sheet, syncState).send(URL, request("bucket_budgets"), expectedType = "bucket_budgets_backed_up")

        assertEquals(ListBackup.Outcome.ScriptOutdated, outcome)
    }

    private fun request(target: String) = SyncRequest(action = "backup", target = target, records = listOf("x"))

    private companion object {
        const val URL = "https://script.example/exec"
    }
}

private class FakeListSheet(var reply: SyncResponse) : ApiService {
    val sent = mutableListOf<SyncRequest>()

    override suspend fun syncRecords(url: String, request: SyncRequest): Response<SyncResponse> {
        sent += request
        return Response.success(reply)
    }

    override suspend fun importRecords(url: String, target: String): Response<ImportResponse> = error("unused")
    override suspend fun importDropdownOptions(url: String, target: String): Response<DropdownImportResponse> = error("unused")
    override suspend fun importBudgets(url: String, target: String): Response<BudgetImportResponse> = error("unused")
    override suspend fun importAccounts(url: String, target: String): Response<AccountImportResponse> = error("unused")
    override suspend fun importBucketBudgets(url: String, target: String): Response<BucketBudgetImportResponse> = error("unused")
}
