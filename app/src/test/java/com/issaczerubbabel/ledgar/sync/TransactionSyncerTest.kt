package com.issaczerubbabel.ledgar.sync

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.remote.AccountImportResponse
import com.issaczerubbabel.ledgar.data.remote.ApiService
import com.issaczerubbabel.ledgar.data.remote.BudgetImportResponse
import com.issaczerubbabel.ledgar.data.remote.DropdownImportResponse
import com.issaczerubbabel.ledgar.data.remote.ImportResponse
import com.issaczerubbabel.ledgar.data.remote.SyncRecordDto
import com.issaczerubbabel.ledgar.data.remote.SyncRequest
import com.issaczerubbabel.ledgar.data.remote.SyncResponse
import com.issaczerubbabel.ledgar.util.generateTimestampKey
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

class TransactionSyncerTest {

    private val clock = LocalDateTime.of(2026, 9, 24, 10, 0, 0)
    private val store = FakeStore()
    private val sheet = FakeAppsScript()
    private val syncer = TransactionSyncer(store, sheet, Clock.fixed(clock.toInstant(ZoneOffset.UTC), ZoneOffset.UTC))

    @Test
    fun `a new Transaction reaches the Sheet once and is settled`() = runBlocking {
        val id = store.add(transaction(amount = 120.0))

        assertEquals(TransactionSyncer.Outcome.Synced(1), syncer.sync(URL))

        assertEquals(1, sheet.rows.size)
        assertEquals(120.0, sheet.rows.single().amount, 0.0)
        assertTrue(store.get(id)!!.isSynced)
        assertEquals("NONE", store.get(id)!!.syncAction)
    }

    @Test
    fun `a retry after a lost reply does not duplicate the row`() = runBlocking {
        val id = store.add(transaction())
        sheet.loseNextReply = true

        try {
            syncer.sync(URL)
            fail("the lost reply should surface as an IOException")
        } catch (expected: IOException) {
        }
        assertEquals("the Sheet applied the write before the reply was lost", 1, sheet.rows.size)
        assertFalse(store.get(id)!!.isSynced)

        assertEquals(TransactionSyncer.Outcome.Synced(1), syncer.sync(URL))
        assertEquals(1, sheet.rows.size)
        assertTrue(store.get(id)!!.isSynced)
    }

    @Test
    fun `the Remote timestamp is saved before the first request`() = runBlocking<Unit> {
        val id = store.add(transaction())
        var stampedBeforeSending = false
        sheet.onRequest = { stampedBeforeSending = !store.get(id)!!.remoteTimestamp.isNullOrBlank() }

        syncer.sync(URL)

        assertTrue(stampedBeforeSending)
    }

    @Test
    fun `an edit made while the request is in flight is not lost`() = runBlocking {
        val id = store.add(transaction(amount = 100.0))
        sheet.beforeReply = {
            store.edit(id) { it.copy(amount = 250.0, isSynced = false) }
            sheet.beforeReply = null
        }

        syncer.sync(URL)
        assertFalse("the edit is still waiting to sync", store.get(id)!!.isSynced)
        assertEquals(250.0, store.get(id)!!.amount, 0.0)

        syncer.sync(URL)
        assertEquals(1, sheet.rows.size)
        assertEquals(250.0, sheet.rows.single().amount, 0.0)
        assertTrue(store.get(id)!!.isSynced)
    }

    @Test
    fun `a delete made while the request is in flight is not undone`() = runBlocking {
        val id = store.add(transaction())
        sheet.beforeReply = {
            store.edit(id) { it.copy(isSynced = false, syncAction = "DELETE") }
            sheet.beforeReply = null
        }

        syncer.sync(URL)
        assertEquals("DELETE", store.get(id)!!.syncAction)

        syncer.sync(URL)
        assertTrue(sheet.rows.isEmpty())
        assertNull(store.get(id))
    }

    @Test
    fun `deleting a row that is already gone from the Sheet succeeds`() = runBlocking {
        val id = store.add(transaction(remoteTimestamp = "9/1/2026 08:00:00", syncAction = "DELETE"))

        assertEquals(TransactionSyncer.Outcome.Synced(1), syncer.sync(URL))
        assertNull(store.get(id))
    }

    @Test
    fun `a delete of a Transaction that never reached the Sheet sends nothing`() = runBlocking {
        val id = store.add(transaction(syncAction = "DELETE"))

        syncer.sync(URL)

        assertNull(store.get(id))
        assertEquals(0, sheet.requestCount)
    }

    @Test
    fun `new Remote timestamps never reuse one already taken`() = runBlocking {
        val taken = generateTimestampKey(clock)
        store.add(transaction(remoteTimestamp = taken, isSynced = true, syncAction = "NONE"))
        val first = store.add(transaction())
        val second = store.add(transaction())

        syncer.sync(URL)

        val stamps = listOf(taken, store.get(first)!!.remoteTimestamp, store.get(second)!!.remoteTimestamp)
        assertEquals(3, stamps.toSet().size)
    }

    @Test
    fun `later batches never reuse an earlier batch's timestamps`() = runBlocking {
        val first = store.add(transaction())
        syncer.sync(URL)
        val second = store.add(transaction())
        syncer.sync(URL)

        assertNotEquals(store.get(first)!!.remoteTimestamp, store.get(second)!!.remoteTimestamp)
        assertEquals(2, sheet.rows.size)
    }

    @Test
    fun `editing a Transaction that never synced keeps it to one row`() = runBlocking {
        val id = store.add(transaction(syncAction = "UPDATE", amount = 10.0))
        syncer.sync(URL)
        store.edit(id) { it.copy(amount = 20.0, isSynced = false, syncAction = "UPDATE") }
        syncer.sync(URL)

        assertEquals(1, sheet.rows.size)
        assertEquals(20.0, sheet.rows.single().amount, 0.0)
    }

    @Test
    fun `a failed request leaves every Transaction waiting`() = runBlocking {
        val id = store.add(transaction())
        sheet.failWithHttp = 500

        val outcome = syncer.sync(URL)

        assertTrue(outcome is TransactionSyncer.Outcome.Failed)
        assertFalse(store.get(id)!!.isSynced)
        assertTrue(sheet.rows.isEmpty())
    }

    @Test
    fun `large changes go out in bounded batches`() = runBlocking {
        repeat(45) { store.add(transaction(amount = it.toDouble())) }

        syncer.sync(URL)

        assertEquals(3, sheet.requestCount)
        assertEquals(45, sheet.rows.size)
        assertTrue(store.pending().isEmpty())
    }

    private fun transaction(
        amount: Double = 50.0,
        remoteTimestamp: String? = null,
        isSynced: Boolean = false,
        syncAction: String = "INSERT"
    ) = ExpenseRecord(
        date = "2026-09-24",
        type = "Expense",
        category = "Food",
        description = "Lunch",
        amount = amount,
        remarks = "",
        isSynced = isSynced,
        remoteTimestamp = remoteTimestamp,
        syncAction = syncAction
    )

    private companion object {
        const val URL = "https://script.example/exec"
    }
}

/** Mirrors the Room store, including the trigger that raises `localVersion` on every change. */
private class FakeStore : TransactionSyncStore {
    private val rows = linkedMapOf<Long, ExpenseRecord>()
    private var nextId = 1L

    fun add(record: ExpenseRecord): Long {
        val id = nextId++
        rows[id] = record.copy(id = id)
        return id
    }

    fun get(id: Long) = rows[id]

    fun edit(id: Long, change: (ExpenseRecord) -> ExpenseRecord) {
        val old = rows.getValue(id)
        rows[id] = change(old).copy(localVersion = old.localVersion + 1)
    }

    override suspend fun pending() = rows.values.filter { !it.isSynced }

    override suspend fun remoteTimestampsInUse() =
        rows.values.mapNotNull { it.remoteTimestamp?.takeIf(String::isNotBlank) }.toSet()

    override suspend fun assignRemoteTimestamp(id: Long, timestamp: String) {
        if (rows[id]?.remoteTimestamp.isNullOrBlank()) edit(id) { it.copy(remoteTimestamp = timestamp) }
    }

    override suspend fun markSyncedIfUnchanged(id: Long, version: Long) {
        val row = rows[id] ?: return
        if (row.localVersion == version && row.syncAction != "DELETE") {
            edit(id) { it.copy(isSynced = true, syncAction = "NONE") }
        }
    }

    override suspend fun finishDeleteIfUnchanged(id: Long, version: Long) {
        val row = rows[id] ?: return
        if (row.localVersion == version && row.syncAction == "DELETE") rows.remove(id)
    }

    override suspend fun accountNamesById() = emptyMap<Long, String>()
}

/** Behaves like the currently deployed Apps Script: `update` overwrites by timestamp or appends. */
private class FakeAppsScript : ApiService {
    data class Row(val timestamp: String, val amount: Double)

    val rows = mutableListOf<Row>()
    var requestCount = 0
    var loseNextReply = false
    var failWithHttp: Int? = null
    var onRequest: (() -> Unit)? = null
    var beforeReply: (() -> Unit)? = null

    override suspend fun syncRecords(url: String, request: SyncRequest): Response<SyncResponse> {
        requestCount++
        onRequest?.invoke()
        failWithHttp?.let { return Response.error(it, "".toResponseBody()) }

        val response = when (request.action) {
            "delete" -> {
                val index = rows.indexOfLast { it.timestamp == request.targetTimestamp }
                if (index >= 0) rows.removeAt(index)
                SyncResponse(status = "ok", count = if (index >= 0) 1 else 0)
            }
            "update", "insert" -> {
                request.records.filterIsInstance<SyncRecordDto>().forEach { dto ->
                    val row = Row(dto.timestamp, dto.amount)
                    val index = rows.indexOfFirst { it.timestamp == dto.timestamp }
                    if (request.action == "update" && index >= 0) rows[index] = row else rows += row
                }
                SyncResponse(status = "ok", count = request.records.size)
            }
            else -> SyncResponse(status = "error", message = "unexpected action ${request.action}")
        }

        beforeReply?.invoke()
        if (loseNextReply) {
            loseNextReply = false
            throw IOException("reply lost")
        }
        return Response.success(response)
    }

    override suspend fun importRecords(url: String, target: String): Response<ImportResponse> = error("unused")
    override suspend fun importDropdownOptions(url: String, target: String): Response<DropdownImportResponse> = error("unused")
    override suspend fun importBudgets(url: String, target: String): Response<BudgetImportResponse> = error("unused")
    override suspend fun importAccounts(url: String, target: String): Response<AccountImportResponse> = error("unused")
}
