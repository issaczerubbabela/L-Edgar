package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** A delete still offering Undo: hidden everywhere, written only once it's final. */
data class UndoableDelete(
    val transaction: ExpenseRecord,
    /** "Deleted “Uber to office” · ₹212". */
    val message: String
)

/**
 * The one delete offering Undo, shared by every screen that lists Transactions (the Ledger, Search,
 * Bookmarks, an Account's page), so a row hidden on one is hidden on all of them.
 *
 * Nothing is written until the delete is final: SyncTriggers asks for a Sync the moment a row turns
 * unsynced, so marking it now and clearing it on Undo would race that Sync.
 */
@Singleton
class UndoableDeletes @Inject constructor(
    private val repository: ExpenseRepository
) {
    // A final delete must land even if the screen that asked for it is gone.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var undoTimer: Job? = null

    private val _pending = MutableStateFlow<UndoableDelete?>(null)
    /** The delete the Undo snackbar is offering, if any. */
    val pending: StateFlow<UndoableDelete?> = _pending.asStateFlow()

    private val _hiddenIds = MutableStateFlow<Set<Long>>(emptySet())
    /** Transactions to leave out of every list and total: offering Undo, or being written. */
    val hiddenIds: StateFlow<Set<Long>> = _hiddenIds.asStateFlow()

    /** Hides [transaction] at once and offers Undo. Another delete finalises this one first. */
    @Synchronized
    fun delete(transaction: ExpenseRecord, message: String) {
        commit()
        val delete = UndoableDelete(transaction, message)
        _pending.value = delete
        _hiddenIds.update { it + transaction.id }
        undoTimer = scope.launch {
            delay(UNDO_WINDOW_MS)
            commit(delete)
        }
    }

    /** Brings [delete] back untouched (Room never saw it), unless a newer delete has replaced it. */
    @Synchronized
    fun undo(delete: UndoableDelete) {
        if (_pending.value != delete) return
        undoTimer?.cancel()
        undoTimer = null
        _pending.value = null
        _hiddenIds.update { it - delete.transaction.id }
    }

    /** Makes the delete offering Undo final with the usual soft delete, so it syncs as any delete does. */
    @Synchronized
    fun commit() {
        _pending.value?.let { commit(it) }
    }

    @Synchronized
    private fun commit(delete: UndoableDelete) {
        if (_pending.value != delete) return
        undoTimer?.cancel()
        undoTimer = null
        _pending.value = null
        scope.launch {
            try {
                repository.delete(delete.transaction)
            } finally {
                // Hidden until the soft delete lands, so the row never flashes back; shown again
                // if the write failed, since it's still there.
                _hiddenIds.update { it - delete.transaction.id }
            }
        }
    }

    private companion object {
        const val UNDO_WINDOW_MS = 5_000L
    }
}
