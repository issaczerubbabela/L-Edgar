package com.issaczerubbabel.ledgar.sync

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression coverage for issue #56: an imported row must ask for a Transaction ID, never be deleted. */
class SheetMergeTest {

    @Test
    fun `an unsynced imported row with no Transaction ID yet is asked for one, not deleted`() {
        val imported = record(isSynced = false, syncId = null)

        val plan = SheetMerge.plan(local = listOf(imported), sheet = emptyList(), accountNames = emptyMap(), allowMassDelete = true)

        assertEquals(listOf(MergeStep.AssignNewId(imported.id, imported.localVersion)), plan.steps)
        assertTrue(plan.heldDeletes.isEmpty())
    }

    @Test
    fun `an unsynced imported row that already has a Transaction ID is left to Push, not deleted`() {
        val imported = record(isSynced = false, syncId = "local-generated-id")

        val plan = SheetMerge.plan(local = listOf(imported), sheet = emptyList(), accountNames = emptyMap(), allowMassDelete = true)

        assertTrue(plan.steps.isEmpty())
        assertTrue(plan.heldDeletes.isEmpty())
    }

    @Test
    fun `a row marked already-synced with no Transaction ID -- the old CSV import bug -- is still deleted`() {
        val staleImport = record(isSynced = true, syncId = null)

        val plan = SheetMerge.plan(local = listOf(staleImport), sheet = emptyList(), accountNames = emptyMap(), allowMassDelete = true)

        assertEquals(listOf(MergeStep.DeleteLocal(staleImport.id, staleImport.localVersion)), plan.steps)
    }

    private fun record(isSynced: Boolean, syncId: String?, id: Long = 1L) = ExpenseRecord(
        id = id,
        date = "2026-10-07",
        type = "Expense",
        category = "Food",
        description = "",
        amount = 100.0,
        remarks = "",
        isSynced = isSynced,
        syncId = syncId,
        syncAction = if (isSynced) "NONE" else "INSERT"
    )
}
