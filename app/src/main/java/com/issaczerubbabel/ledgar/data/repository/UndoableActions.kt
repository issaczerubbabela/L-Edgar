package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.di.ApplicationScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A decision still offering Undo: the rows it hides straight away, and the [write] that makes it
 * final. Deleting a Transaction, confirming or dismissing a Captured transaction, and Review all
 * are each one action.
 */
class UndoableAction(
    /** "Deleted “Uber to office” · ₹212", "Confirmed Instamart · ₹349 as Groceries". */
    val message: String,
    val hiddenTransactionIds: Set<Long> = emptySet(),
    val hiddenCaptureIds: Set<Long> = emptySet(),
    val write: suspend () -> Unit
)

/**
 * The one action offering Undo, shared by every screen, so a row hidden on one is hidden on all of
 * them (the Ledger, Search, Bookmarks, an Account's page, To review).
 *
 * Nothing is written until the action is final: SyncTriggers asks for a Sync the moment a row turns
 * unsynced, so writing now and reversing on Undo would race that Sync. Waiting also means Undo never
 * has to put back a Merchant rule or Account alias that a Confirm taught.
 */
@Singleton
class UndoableActions @Inject constructor(
    private val expenseDao: ExpenseDao,
    // A final action must land even if the screen that asked for it is gone.
    @ApplicationScope private val scope: CoroutineScope
) {
    private var undoTimer: Job? = null

    private val _pending = MutableStateFlow<UndoableAction?>(null)
    /** The action the Undo snackbar is offering, if any. */
    val pending: StateFlow<UndoableAction?> = _pending.asStateFlow()

    private val _hiddenTransactionIds = MutableStateFlow<Set<Long>>(emptySet())
    /** Transactions to leave out of every list and total: offering Undo, or being written. */
    val hiddenTransactionIds: StateFlow<Set<Long>> = _hiddenTransactionIds.asStateFlow()

    private val _hiddenCaptureIds = MutableStateFlow<Set<Long>>(emptySet())
    /** Captured transactions to leave out of To review: offering Undo, or being written. */
    val hiddenCaptureIds: StateFlow<Set<Long>> = _hiddenCaptureIds.asStateFlow()

    /** Hides [action]'s rows at once and offers Undo. Another action finalises this one first. */
    @Synchronized
    fun perform(action: UndoableAction) {
        commit()
        _pending.value = action
        hide(action)
        undoTimer = scope.launch {
            delay(UNDO_WINDOW_MS)
            commit(action)
        }
    }

    /** Hides [transaction] at once and soft-deletes it once final, so it syncs as any delete does. */
    fun delete(transaction: ExpenseRecord, message: String) =
        perform(UndoableAction(message, hiddenTransactionIds = setOf(transaction.id)) {
            expenseDao.markTransactionDeletedById(transaction.id)
        })

    /** Brings [action]'s rows back untouched (Room never saw it), unless a newer action has replaced it. */
    @Synchronized
    fun undo(action: UndoableAction) {
        if (_pending.value !== action) return
        undoTimer?.cancel()
        undoTimer = null
        _pending.value = null
        show(action)
    }

    /** Makes the action offering Undo final now. */
    @Synchronized
    fun commit() {
        _pending.value?.let { commit(it) }
    }

    @Synchronized
    private fun commit(action: UndoableAction) {
        if (_pending.value !== action) return
        undoTimer?.cancel()
        undoTimer = null
        _pending.value = null
        scope.launch {
            try {
                action.write()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Nothing to undo: a failed write left Room as it was, and showing the rows again
                // below is how the user sees that it didn't happen.
            } finally {
                // Hidden until the write lands, so the rows never flash back.
                show(action)
            }
        }
    }

    private fun hide(action: UndoableAction) {
        _hiddenTransactionIds.update { it + action.hiddenTransactionIds }
        _hiddenCaptureIds.update { it + action.hiddenCaptureIds }
    }

    private fun show(action: UndoableAction) {
        _hiddenTransactionIds.update { it - action.hiddenTransactionIds }
        _hiddenCaptureIds.update { it - action.hiddenCaptureIds }
    }

    companion object {
        const val UNDO_WINDOW_MS = 5_000L
    }
}
