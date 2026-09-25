package com.issaczerubbabel.ledgar.data.bucket

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.remote.BucketBudgetImportResponse
import com.issaczerubbabel.ledgar.data.remote.SyncRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The app half of the contract with scripts/AppsScript.gs. The script half is
 * scripts/tests/bucket-budgets.test.js. Both are pinned to the same two golden files, so a field
 * renamed on either side fails a test instead of silently breaking backup or restore.
 */
class BucketBudgetContractTest {

    private val fixtures = listOf("../scripts/tests/fixtures", "scripts/tests/fixtures")
        .map(::File).first { it.isDirectory }

    private fun fixture(name: String) = File(fixtures, name).readText(Charsets.UTF_8)

    private val snapshot = BucketBudgetSnapshot(
        cycles = listOf(
            BudgetCycle(id = 1, startDate = "2026-08-26", endDate = "2026-09-24", spendableAmount = 68000.0, closedAt = "2026-09-25"),
            BudgetCycle(id = 2, startDate = "2026-09-25", endDate = "2026-10-24", spendableAmount = 70000.0)
        ),
        buckets = listOf(
            BudgetBucket(id = 10, cycleId = 1, name = "Essentials", note = "Rent", colorIndex = 4, emoji = "🏠",
                allocatedAmount = 18000.0, sortOrder = 0),
            BudgetBucket(id = 11, cycleId = 1, name = "Eating Out", colorIndex = 1, allocatedAmount = 6000.0, sortOrder = 1)
        ),
        assignments = listOf(
            BucketCategory(cycleId = 1, bucketId = 10, category = "Rent"),
            BucketCategory(cycleId = 1, bucketId = 10, category = "Utilities"),
            BucketCategory(cycleId = 1, bucketId = 11, category = "Food")
        )
    )

    @Test
    fun theAppSendsExactlyTheRequestTheScriptIsTestedWith() {
        val request = SyncRequest(
            action = "backup",
            target = "bucket_budgets",
            records = emptyList(),
            cycles = BucketBackupMapper.toSyncDtos(snapshot)
        )

        assertEquals(JsonParser.parseString(fixture("bucket-budgets-request.json")), Gson().toJsonTree(request))
    }

    @Test
    fun recordsStayEmptySoAnOlderScriptHasNothingToFileAsTransactions() {
        val json = Gson().toJsonTree(
            SyncRequest(action = "backup", target = "bucket_budgets", records = emptyList(), cycles = BucketBackupMapper.toSyncDtos(snapshot))
        ).asJsonObject

        assertEquals(0, json.getAsJsonArray("records").size())
    }

    @Test
    fun theAppReadsExactlyTheResponseTheScriptProduces() {
        val response = Gson().fromJson(fixture("bucket-budgets-response.json"), BucketBudgetImportResponse::class.java)

        assertTrue(response.isUnderstoodByScript)
        val restored = BucketBackupMapper.fromImportDtos(response.data.orEmpty())
        assertEquals(2, restored.size)
        assertEquals("2026-09-25", restored[0].closedAt)
        assertNull(restored[1].closedAt)
        assertEquals(listOf("Essentials", "Eating Out"), restored[0].buckets.map { it.name })
        assertEquals(listOf("Rent", "Utilities"), restored[0].buckets[0].categories)
        assertEquals("🏠", restored[0].buckets[0].emoji)
        assertEquals(18000.0, restored[0].buckets[0].allocatedAmount, 0.0)
    }

    @Test
    fun aReplyFromAScriptThatPredatesBucketsIsRecognisedAndNotTrusted() {
        // An older script answers an unknown target with the transaction list: no marker.
        val transactionReply = """{"status":"ok","count":1,"data":[{"date":"2026-09-01","type":"Expense","amount":5}]}"""

        val response = Gson().fromJson(transactionReply, BucketBudgetImportResponse::class.java)

        assertFalse(response.isUnderstoodByScript)
    }
}
