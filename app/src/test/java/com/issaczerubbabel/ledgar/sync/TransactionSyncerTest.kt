package com.issaczerubbabel.ledgar.sync

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.remote.AccountImportResponse
import com.issaczerubbabel.ledgar.data.remote.ApiService
import com.issaczerubbabel.ledgar.data.remote.BucketBudgetImportResponse
import com.issaczerubbabel.ledgar.data.remote.BudgetImportResponse
import com.issaczerubbabel.ledgar.data.remote.DropdownImportResponse
import com.issaczerubbabel.ledgar.data.remote.ImportRecordDto
import com.issaczerubbabel.ledgar.data.remote.ImportResponse
import com.issaczerubbabel.ledgar.data.remote.SyncRequest
import com.issaczerubbabel.ledgar.data.remote.SyncResponse
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.Response
import java.io.IOException
import java.util.UUID

class TransactionSyncerTest {

    private val phone = FakeStore()
    private val sheet = FakeAppsScript()
    private val syncer = TransactionSyncer(phone, sheet)

    private suspend fun push() = syncer.sync(URL, pull = false)
    private suspend fun fullSync(allowMassDelete: Boolean = false) = syncer.sync(URL, pull = true, allowMassDelete)

    // ── Push ─────────────────────────────────────────────────────────────────

    @Test
    fun `a new Transaction reaches the Sheet once and records what both sides agreed on`() = runBlocking {
        val id = phone.addNew(amount = 120.0)

        push()

        assertEquals(1, sheet.rows.size)
        assertEquals(120.0, sheet.rows.values.single().amount, 0.0)
        val synced = phone.get(id)!!
        assertTrue(synced.isSynced)
        assertEquals("the phone records the row's revision as agreed", sheet.revisionOf(synced.syncId!!), synced.syncedRevision)
    }

    @Test
    fun `a retry after a lost reply does not duplicate the row`() = runBlocking {
        val id = phone.addNew()
        sheet.loseNextReply = true

        try {
            push()
            fail("the lost reply should surface as an IOException")
        } catch (expected: IOException) {
        }
        assertEquals("the Sheet applied the write before the reply was lost", 1, sheet.rows.size)
        assertFalse(phone.get(id)!!.isSynced)

        push()
        assertEquals(1, sheet.rows.size)
        assertTrue(phone.get(id)!!.isSynced)
    }

    @Test
    fun `an edit made while the request is in flight is not lost`() = runBlocking {
        val id = phone.addNew(amount = 100.0)
        sheet.beforeReply = {
            phone.edit(id) { it.copy(amount = 250.0, isSynced = false, syncAction = "UPDATE") }
            sheet.beforeReply = null
        }

        push()
        assertFalse(phone.get(id)!!.isSynced)

        push()
        assertEquals(250.0, sheet.rows.values.single().amount, 0.0)
        assertTrue(phone.get(id)!!.isSynced)
    }

    @Test
    fun `a delete made while the request is in flight is not undone`() = runBlocking {
        val id = phone.addNew()
        sheet.beforeReply = {
            phone.edit(id) { it.copy(isSynced = false, syncAction = "DELETE") }
            sheet.beforeReply = null
        }

        push()
        assertEquals("DELETE", phone.get(id)!!.syncAction)

        push()
        assertTrue(sheet.rows.isEmpty())
        assertNull(phone.get(id))
    }

    @Test
    fun `deleting a row already gone from the Sheet finishes the delete`() = runBlocking {
        val id = phone.addNew()
        push()
        sheet.rows.clear()
        phone.edit(id) { it.copy(isSynced = false, syncAction = "DELETE") }

        push()

        assertNull(phone.get(id))
    }

    @Test
    fun `large changes go out in bounded batches`() = runBlocking {
        repeat(120) { phone.addNew(amount = it.toDouble()) }

        push()

        assertEquals(3, sheet.writeRequests)
        assertEquals(120, sheet.rows.size)
        assertTrue(phone.pending().isEmpty())
    }

    // ── Outdated scripts ─────────────────────────────────────────────────────

    @Test
    fun `a script from before Transaction IDs pauses Sync and is never written to`() = runBlocking {
        sheet.version = 1
        val id = phone.addNew()

        assertEquals(TransactionSyncer.Outcome.ScriptOutdated, push())
        assertEquals(TransactionSyncer.Outcome.ScriptOutdated, fullSync())

        assertTrue(sheet.rows.isEmpty())
        assertFalse(phone.get(id)!!.isSynced)
    }

    // ── Pull ─────────────────────────────────────────────────────────────────

    @Test
    fun `a row typed into the Sheet comes to the phone once`() = runBlocking {
        sheet.typeRow(description = "Bus fare", amount = 30.0)

        fullSync()
        fullSync()

        val local = phone.all().single()
        assertEquals("Bus fare", local.description)
        assertTrue(local.isSynced)
        assertEquals(sheet.rows.keys.single(), local.syncId)
    }

    @Test
    fun `a Sheet edit to a Transaction the phone hasn't changed is taken`() = runBlocking {
        val id = phone.addNew(amount = 50.0)
        push()

        sheet.editRow(phone.get(id)!!.syncId!!) { it.copy(amount = 75.0) }
        fullSync()

        assertEquals(75.0, phone.get(id)!!.amount, 0.0)
        assertTrue(phone.get(id)!!.isSynced)
        assertTrue(phone.pending().isEmpty())
    }

    @Test
    fun `a phone edit to a Transaction the Sheet hasn't changed goes up`() = runBlocking {
        val id = phone.addNew(amount = 50.0)
        push()

        phone.edit(id) { it.copy(amount = 60.0, isSynced = false, syncAction = "UPDATE") }
        fullSync()

        assertEquals(60.0, sheet.rows.values.single().amount, 0.0)
        assertNull(phone.get(id)!!.sheetConflictJson)
    }

    @Test
    fun `a Transaction changed differently on both sides becomes a conflict and isn't sent`() = runBlocking {
        val id = phone.addNew(amount = 50.0)
        push()
        val syncId = phone.get(id)!!.syncId!!

        sheet.editRow(syncId) { it.copy(amount = 70.0) }
        phone.edit(id) { it.copy(amount = 60.0, isSynced = false, syncAction = "UPDATE") }
        fullSync()

        assertNotNull(phone.get(id)!!.sheetConflictJson)
        assertEquals("the Sheet keeps its version until the user decides", 70.0, sheet.rows.getValue(syncId).amount, 0.0)
        assertEquals(60.0, phone.get(id)!!.amount, 0.0)
    }

    @Test
    fun `a phone edit sent after a Sheet edit it hasn't pulled becomes a conflict, not an overwrite`() = runBlocking {
        val id = phone.addNew(amount = 50.0)
        push()
        val syncId = phone.get(id)!!.syncId!!

        sheet.editRow(syncId) { it.copy(amount = 70.0) }
        phone.edit(id) { it.copy(amount = 60.0, isSynced = false, syncAction = "UPDATE") }
        push()

        assertEquals("the Sheet's edit survives", 70.0, sheet.rows.getValue(syncId).amount, 0.0)
        assertNotNull("and the user is asked", phone.get(id)!!.sheetConflictJson)
    }

    @Test
    fun `a retry whose first attempt landed settles instead of conflicting`() = runBlocking {
        val id = phone.addNew(amount = 50.0)
        push()
        phone.edit(id) { it.copy(amount = 60.0, isSynced = false, syncAction = "UPDATE") }
        sheet.loseNextReply = true
        try {
            push()
        } catch (expected: IOException) {
        }

        push()

        assertNull(phone.get(id)!!.sheetConflictJson)
        assertTrue(phone.get(id)!!.isSynced)
        assertEquals(60.0, sheet.rows.values.single().amount, 0.0)
    }

    @Test
    fun `the same change made on both sides settles without a conflict`() = runBlocking {
        val id = phone.addNew(amount = 50.0)
        push()

        sheet.editRow(phone.get(id)!!.syncId!!) { it.copy(amount = 65.0) }
        phone.edit(id) { it.copy(amount = 65.0, isSynced = false, syncAction = "UPDATE") }
        fullSync()

        assertNull(phone.get(id)!!.sheetConflictJson)
        assertTrue(phone.get(id)!!.isSynced)
    }

    @Test
    fun `a row deleted in the Sheet is deleted on the phone`() = runBlocking {
        val ids = List(30) { phone.addNew(amount = it.toDouble()) }
        push()

        sheet.rows.remove(phone.get(ids[0])!!.syncId!!)
        fullSync()

        assertNull(phone.get(ids[0]))
        assertEquals(29, phone.all().size)
    }

    @Test
    fun `deleting many rows in the Sheet is held back until the user confirms`() = runBlocking {
        repeat(30) { phone.addNew(amount = it.toDouble()) }
        push()
        sheet.rows.clear()

        val held = fullSync() as TransactionSyncer.Outcome.Synced
        assertEquals(30, held.heldDeletes!!.size)
        assertEquals(30, phone.all().size)

        fullSync(allowMassDelete = true)
        assertTrue(phone.all().isEmpty())
    }

    @Test
    fun `a phone edit to a row deleted in the Sheet puts it back`() = runBlocking {
        val id = phone.addNew(amount = 10.0)
        push()
        sheet.rows.clear()
        phone.edit(id) { it.copy(amount = 11.0, isSynced = false, syncAction = "UPDATE") }

        fullSync()

        assertEquals(11.0, sheet.rows.values.single().amount, 0.0)
    }

    @Test
    fun `a reinstall pulls every row once`() = runBlocking {
        repeat(5) { sheet.typeRow(description = "row $it", amount = it.toDouble()) }

        fullSync()
        fullSync()

        assertEquals(5, phone.all().size)
        assertEquals(5, sheet.rows.size)
    }

    @Test
    fun `pulling twice changes nothing the second time`() = runBlocking {
        repeat(3) { phone.addNew(amount = it.toDouble()) }
        sheet.typeRow(description = "typed", amount = 9.0)
        fullSync()
        val before = phone.all()

        val second = fullSync() as TransactionSyncer.Outcome.Synced

        assertEquals(0, second.pulledChanges)
        assertEquals(0, second.pushed)
        assertEquals(before, phone.all())
    }

    // ── Upgrading from before Transaction IDs ────────────────────────────────

    @Test
    fun `older Transactions link to their Sheet rows by Remote timestamp instead of duplicating`() = runBlocking {
        val syncId = sheet.typeRow(description = "Lunch", amount = 50.0, timestamp = "9/1/2026 12:00:00")
        val id = phone.addLegacy(description = "Lunch", amount = 50.0, remoteTimestamp = "9/1/2026 12:00:00", synced = true)

        fullSync()

        assertEquals(syncId, phone.get(id)!!.syncId)
        assertEquals(1, phone.all().size)
        assertEquals(1, sheet.rows.size)
        assertEquals(0, sheet.writeRequests)
    }

    @Test
    fun `an older change that never reached the Sheet gets its own ID and goes up`() = runBlocking {
        val id = phone.addLegacy(description = "Unsent", amount = 20.0, remoteTimestamp = null, synced = false)

        push()

        assertNotNull(phone.get(id)!!.syncId)
        assertTrue(phone.get(id)!!.isSynced)
        assertEquals("Unsent", sheet.rows.values.single().description)
    }

    @Test
    fun `a duplicated Sheet row from the old bug stays visible for review instead of vanishing`() = runBlocking {
        sheet.typeRow(description = "Coffee", amount = 5.0, timestamp = "9/2/2026 09:00:00")
        sheet.typeRow(description = "Coffee", amount = 5.0, timestamp = "9/2/2026 09:00:00")
        phone.addLegacy(description = "Coffee", amount = 5.0, remoteTimestamp = "9/2/2026 09:00:00", synced = true)

        fullSync()

        assertEquals(2, phone.all().size)
        assertEquals(2, phone.all().mapNotNull { it.syncId }.toSet().size)
    }

    private companion object {
        const val URL = "https://script.example/exec"
    }
}

/** Mirrors [RoomTransactionSyncStore]: version-checked writes, and the triggers on localVersion and syncId. */
private class FakeStore : TransactionSyncStore {
    private val rows = linkedMapOf<Long, ExpenseRecord>()
    private var nextId = 1L

    fun addNew(amount: Double = 50.0, description: String = "Lunch"): Long =
        insert(record(amount, description).copy(syncId = UUID.randomUUID().toString()))

    fun addLegacy(description: String, amount: Double, remoteTimestamp: String?, synced: Boolean): Long =
        insert(record(amount, description).copy(
            remoteTimestamp = remoteTimestamp,
            isSynced = synced,
            syncAction = if (synced) "NONE" else "INSERT"
        ))

    private fun insert(record: ExpenseRecord): Long {
        val id = nextId++
        rows[id] = record.copy(id = id)
        return id
    }

    fun get(id: Long) = rows[id]
    fun all() = rows.values.toList()

    fun edit(id: Long, change: (ExpenseRecord) -> ExpenseRecord) {
        val old = rows.getValue(id)
        rows[id] = change(old).copy(localVersion = old.localVersion + 1)
    }

    private fun unchanged(id: Long, version: Long) = rows[id]?.localVersion == version

    override suspend fun allTransactions() = all()
    override suspend fun pending() = rows.values.filter { !it.isSynced }
    override suspend fun hasRowsWithoutSyncId() = rows.values.any { it.syncId == null }
    override suspend fun accountNamesById() = emptyMap<Long, String>()

    override suspend fun apply(steps: List<MergeStep>): Int = steps.count { step ->
        when (step) {
            is MergeStep.Link -> bookkeep(step.localId, step.version) { it.copy(syncId = step.syncId) }
            is MergeStep.AssignNewId -> bookkeep(step.localId, step.version) { it.copy(syncId = UUID.randomUUID().toString()) }
            is MergeStep.RecordBase -> bookkeep(step.localId, step.version) { it.copy(syncedRevision = step.revision) }
            is MergeStep.Conflict -> bookkeep(step.localId, step.version) { it.copy(sheetConflictJson = step.row.dto.toString()) }
            is MergeStep.ClearConflict -> bookkeep(step.localId, step.version) { it.copy(sheetConflictJson = null) }
            is MergeStep.Settle -> settle(step.localId, step.version, step.revision)
            is MergeStep.DeleteLocal -> unchanged(step.localId, step.version).also { if (it) rows.remove(step.localId) }
            is MergeStep.FinishDelete -> finishDelete(step.localId, step.version)
            is MergeStep.InsertFromSheet -> {
                if (rows.values.any { it.syncId == step.row.syncId }) false
                else { insert(fromSheet(step.row).copy(syncId = step.row.syncId)); true }
            }
            is MergeStep.ApplyFromSheet -> unchanged(step.localId, step.version).also { ok ->
                if (ok) edit(step.localId) { fromSheet(step.row).copy(id = it.id, syncId = it.syncId) }
            }
        }
    }

    /** Sync bookkeeping columns don't raise localVersion, like the Room trigger. */
    private fun bookkeep(id: Long, version: Long, change: (ExpenseRecord) -> ExpenseRecord): Boolean {
        if (!unchanged(id, version)) return false
        rows[id] = change(rows.getValue(id))
        return true
    }

    private fun settle(id: Long, version: Long, revision: String?): Boolean {
        val row = rows[id] ?: return false
        if (row.localVersion != version || row.syncAction == "DELETE") return false
        edit(id) { it.copy(isSynced = true, syncAction = "NONE", syncedRevision = revision, sheetConflictJson = null) }
        return true
    }

    private fun finishDelete(id: Long, version: Long): Boolean {
        val row = rows[id] ?: return false
        if (row.localVersion != version || row.syncAction != "DELETE") return false
        rows.remove(id)
        return true
    }

    override suspend fun markSyncedIfUnchanged(id: Long, version: Long, revision: String?) {
        settle(id, version, revision)
    }

    override suspend fun finishDeleteIfUnchanged(id: Long, version: Long) {
        finishDelete(id, version)
    }

    private fun fromSheet(row: PulledRow) = ExpenseRecord(
        date = row.dto.date,
        type = row.dto.type,
        category = row.dto.expCategory.orEmpty(),
        description = row.dto.description,
        amount = row.dto.amount,
        accountName = row.dto.accountName,
        remarks = row.dto.remarks,
        isBookmarked = row.dto.isBookmarked ?: false,
        remoteTimestamp = row.timestampKey,
        isSynced = true,
        syncAction = "NONE",
        syncedRevision = row.revision
    )

    private fun record(amount: Double, description: String) = ExpenseRecord(
        date = "2026-09-24",
        type = "Expense",
        category = "Food",
        description = description,
        amount = amount,
        accountName = "Wallet",
        remarks = "",
        isSynced = false,
        syncAction = "INSERT"
    )
}

/**
 * Behaves like the version-2 Apps Script: rows keyed by Transaction ID, upserts and deletes by ID,
 * and a revision per row that any edit changes, with upserts on an outdated base refused as stale.
 */
private class FakeAppsScript : ApiService {
    val rows = linkedMapOf<String, ImportRecordDto>()

    fun revisionOf(id: String): String = rows.getValue(id).copy(id = null, revision = null, timestamp = null).hashCode().toString(16)
    var version = 2
    var writeRequests = 0
    var loseNextReply = false
    var beforeReply: (() -> Unit)? = null

    fun typeRow(description: String, amount: Double, timestamp: String = "9/24/2026 08:00:00"): String {
        val id = UUID.randomUUID().toString()
        rows[id] = ImportRecordDto(
            id = id, timestamp = timestamp, date = "2026-09-24", type = "Expense", expCategory = "Food",
            description = description, amount = amount, accountName = "Wallet", remarks = ""
        )
        return id
    }

    fun editRow(id: String, change: (ImportRecordDto) -> ImportRecordDto) {
        rows[id] = change(rows.getValue(id))
    }

    override suspend fun syncRecords(url: String, request: SyncRequest): Response<SyncResponse> {
        if (version < 2) return Response.success(SyncResponse(status = "ok", count = 0))
        writeRequests++
        val stale = mutableListOf<String>()
        val written = mutableListOf<String>()
        when (request.action) {
            "upsert" -> request.transactions.orEmpty().forEach { dto ->
                if (dto.base != null && rows.containsKey(dto.id) && revisionOf(dto.id) != dto.base) {
                    stale += dto.id
                    return@forEach
                }
                written += dto.id
                rows[dto.id] = ImportRecordDto(
                    id = dto.id,
                    timestamp = rows[dto.id]?.timestamp ?: dto.timestamp ?: "9/24/2026 10:00:00",
                    date = dto.date, type = dto.type, expCategory = dto.expCategory, incCategory = dto.incCategory,
                    description = dto.description, amount = dto.amount, accountName = dto.accountName,
                    fromAccountName = dto.fromAccountName, toAccountName = dto.toAccountName,
                    remarks = dto.remarks, isBookmarked = dto.isBookmarked
                )
            }
            "delete_ids" -> request.ids.orEmpty().forEach { rows.remove(it) }
            else -> return Response.error(400, "unexpected ${request.action}".toResponseBody())
        }
        beforeReply?.invoke()
        if (loseNextReply) {
            loseNextReply = false
            throw IOException("reply lost")
        }
        return Response.success(SyncResponse(
            status = "ok",
            scriptVersion = version,
            stale = stale,
            revisions = written.associateWith(::revisionOf)
        ))
    }

    override suspend fun importRecords(url: String, target: String): Response<ImportResponse> =
        Response.success(
            if (version < 2) ImportResponse(status = "ok", data = rows.values.map { it.copy(id = null) })
            else ImportResponse(status = "ok", data = rows.map { (id, row) -> row.copy(revision = revisionOf(id)) }, scriptVersion = version)
        )

    override suspend fun importDropdownOptions(url: String, target: String): Response<DropdownImportResponse> = error("unused")
    override suspend fun importBudgets(url: String, target: String): Response<BudgetImportResponse> = error("unused")
    override suspend fun importAccounts(url: String, target: String): Response<AccountImportResponse> = error("unused")
    override suspend fun importBucketBudgets(url: String, target: String): Response<BucketBudgetImportResponse> = error("unused")
}
