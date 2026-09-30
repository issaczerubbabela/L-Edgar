package com.issaczerubbabel.ledgar.sync

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.remote.ImportRecordDto
import com.issaczerubbabel.ledgar.util.normalizeTimestampKey

/** One Sheet row as a Pull read it. */
data class PulledRow(val syncId: String, val dto: ImportRecordDto) {
    val revision: String? get() = dto.revision
    val content: TransactionContent by lazy { TransactionContent.of(dto) }
    val timestampKey: String? by lazy { normalizeTimestampKey(dto.timestamp) }
}

/**
 * A change a Pull makes on the phone. Each names the version of the local row it was planned from,
 * and is skipped if that row has changed since (it will be planned again on the next Pull).
 */
sealed interface MergeStep {
    /** Give an older row, matched by Remote timestamp, the Sheet's Transaction ID. */
    data class Link(val localId: Long, val version: Long, val syncId: String) : MergeStep

    /** A change made on the phone that never reached the Sheet gets its own Transaction ID. */
    data class AssignNewId(val localId: Long, val version: Long) : MergeStep

    data class InsertFromSheet(val row: PulledRow) : MergeStep

    /** The Sheet changed and the phone didn't: take the Sheet's version. */
    data class ApplyFromSheet(val localId: Long, val version: Long, val row: PulledRow) : MergeStep

    /** Both sides already agree: record the row's revision as the base for the next merge. */
    data class RecordBase(val localId: Long, val version: Long, val revision: String?) : MergeStep

    /** The phone's pending change turns out to match the Sheet already: nothing left to send. */
    data class Settle(val localId: Long, val version: Long, val revision: String?) : MergeStep

    /** Both sides changed the Transaction differently since the last Sync: the user decides. */
    data class Conflict(val localId: Long, val version: Long, val row: PulledRow) : MergeStep

    /** The Sheet no longer has a Transaction the phone had synced: someone deleted it there. */
    data class DeleteLocal(val localId: Long, val version: Long) : MergeStep

    /** A delete made on the phone is already done: the Sheet doesn't have the row. */
    data class FinishDelete(val localId: Long, val version: Long) : MergeStep

    /** The Sheet row a conflict was about is gone: the phone's version goes up as is. */
    data class ClearConflict(val localId: Long, val version: Long) : MergeStep
}

data class MergePlan(
    val steps: List<MergeStep>,
    /** Deletes withheld because there were too many to apply without asking. */
    val heldDeletes: List<MergeStep.DeleteLocal>
)

/**
 * Plans a Pull as a three-way merge per Transaction ID (ADR-0003). The phone changed a Transaction
 * if it has an unsynced change; the Sheet changed it if the row's revision differs from the one both
 * sides agreed on at the last Sync.
 */
object SheetMerge {

    fun plan(
        local: List<ExpenseRecord>,
        sheet: List<PulledRow>,
        accountNames: Map<Long, String>,
        allowMassDelete: Boolean
    ): MergePlan {
        val steps = mutableListOf<MergeStep>()
        val sheetById = LinkedHashMap<String, PulledRow>()
        sheet.forEach { row -> if (row.syncId.isNotBlank()) sheetById.putIfAbsent(row.syncId, row) }

        val claimed = local.mapNotNull { it.syncId }.toMutableSet()
        val pairs = mutableListOf<Pair<ExpenseRecord, PulledRow>>()
        val unmatchedLocal = mutableListOf<ExpenseRecord>()

        val unclaimedByTimestamp = sheetById.values
            .filter { it.syncId !in claimed && it.timestampKey != null }
            .groupByTo(LinkedHashMap()) { it.timestampKey!! }

        local.forEach { record ->
            val syncId = record.syncId
            if (syncId != null) {
                sheetById[syncId]?.let { pairs += record to it } ?: unmatchedLocal.add(record)
                return@forEach
            }
            val linked = normalizeTimestampKey(record.remoteTimestamp)
                ?.let { unclaimedByTimestamp[it]?.removeFirstOrNull() }
            if (linked == null) {
                unmatchedLocal += record
            } else {
                claimed += linked.syncId
                steps += MergeStep.Link(record.id, record.localVersion, linked.syncId)
                pairs += record to linked
            }
        }

        pairs.forEach { (record, row) -> planPair(record, row, accountNames)?.let(steps::add) }

        sheetById.values
            .filter { it.syncId !in claimed }
            .forEach { steps += MergeStep.InsertFromSheet(it) }

        val deletes = mutableListOf<MergeStep.DeleteLocal>()
        unmatchedLocal.forEach { record ->
            when {
                record.isPendingDelete() -> steps += MergeStep.FinishDelete(record.id, record.localVersion)
                record.sheetConflictJson != null -> steps += MergeStep.ClearConflict(record.id, record.localVersion)
                !record.isSynced && record.syncId == null -> steps += MergeStep.AssignNewId(record.id, record.localVersion)
                // An unsynced change the Sheet doesn't have yet (new, or edited after a delete there) goes up.
                !record.isSynced -> Unit
                else -> deletes += MergeStep.DeleteLocal(record.id, record.localVersion)
            }
        }

        val syncedCount = local.count { it.isSynced && !it.isPendingDelete() }
        val tooMany = deletes.size > MASS_DELETE_COUNT ||
            (deletes.size >= MASS_DELETE_MIN_FOR_SHARE && deletes.size * 100 > syncedCount * MASS_DELETE_PERCENT)
        return if (tooMany && !allowMassDelete) {
            MergePlan(steps, heldDeletes = deletes)
        } else {
            MergePlan(steps + deletes, heldDeletes = emptyList())
        }
    }

    private fun planPair(record: ExpenseRecord, row: PulledRow, accountNames: Map<Long, String>): MergeStep? {
        if (record.isPendingDelete()) return null

        val sameContent = row.content.fingerprint == TransactionContent.of(record, accountNames).fingerprint
        val base = record.syncedRevision
        val sheetChanged = row.revision != base

        if (record.sheetConflictJson != null) {
            return if (sameContent) {
                MergeStep.Settle(record.id, record.localVersion, row.revision)
            } else {
                MergeStep.Conflict(record.id, record.localVersion, row)
            }
        }

        if (record.isSynced) {
            return when {
                !sheetChanged -> null
                sameContent -> MergeStep.RecordBase(record.id, record.localVersion, row.revision)
                else -> MergeStep.ApplyFromSheet(record.id, record.localVersion, row)
            }
        }

        return when {
            sameContent -> MergeStep.Settle(record.id, record.localVersion, row.revision)
            // No base yet (a row from before Transaction IDs): the phone was the authority, so it wins.
            base == null || !sheetChanged -> null
            else -> MergeStep.Conflict(record.id, record.localVersion, row)
        }
    }

    private fun ExpenseRecord.isPendingDelete() = syncAction.equals("DELETE", ignoreCase = true)

    /** A Pull that would delete more than this many Transactions asks first. */
    const val MASS_DELETE_COUNT = 10

    /** ...as does one deleting more than this share of them, once it deletes at least a few. */
    const val MASS_DELETE_PERCENT = 10
    const val MASS_DELETE_MIN_FOR_SHARE = 3
}
