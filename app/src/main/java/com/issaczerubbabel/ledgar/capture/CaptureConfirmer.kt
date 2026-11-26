package com.issaczerubbabel.ledgar.capture

import com.issaczerubbabel.ledgar.capture.notify.CaptureNotifier
import com.issaczerubbabel.ledgar.capture.pair.TransferPairing
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.preferences.ReminderPreferences
import com.issaczerubbabel.ledgar.data.repository.CaptureEdits
import com.issaczerubbabel.ledgar.data.repository.CaptureRepository
import com.issaczerubbabel.ledgar.data.repository.UndoableAction
import com.issaczerubbabel.ledgar.data.repository.UndoableActions
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.formatRupeesExact
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** One capture the user chose to confirm, with what they settled on and the title it shows. */
data class CaptureConfirmation(val capture: CapturedTransaction, val edits: CaptureEdits, val title: String)

/**
 * Confirming captures from To review or from the Add Transaction form opened with Edit: one
 * deferred-commit action offering Undo for 5 seconds, so both places learn, pair and undo the same
 * way. The cards hide at once, a Transfer's other half with them, and nothing is written to Room
 * until the action is final.
 */
class CaptureConfirmer @Inject constructor(
    private val captureRepository: CaptureRepository,
    private val undoableActions: UndoableActions,
    private val notifier: CaptureNotifier,
    private val reminderPreferences: ReminderPreferences
) {

    /** [onFinal] runs with each capture's id once its write is done, on the app's scope. */
    suspend fun confirm(
        items: List<CaptureConfirmation>,
        accountNames: Map<Long, String>,
        onFinal: (captureId: Long) -> Unit = {}
    ) {
        if (items.isEmpty()) return
        val pairings = items.map { captureRepository.findTransferPair(it.capture.id, it.edits) }
        val pairedIds = pairings.mapNotNull { (it as? TransferPairing.One)?.capture?.id }
        val message = if (items.size == 1) {
            CaptureConfirmMessages.single(items.single(), pairings.single(), accountNames)
        } else {
            CaptureConfirmMessages.several(items)
        }
        undoableActions.perform(
            UndoableAction(
                message = message,
                hiddenCaptureIds = items.map { it.capture.id }.toSet() + pairedIds
            ) {
                var confirmedAny = false
                items.forEach { item ->
                    if (captureRepository.confirm(item.capture.id, item.edits) != null) confirmedAny = true
                    notifier.cancel(item.capture.id)
                    onFinal(item.capture.id)
                }
                // Only a capture that really was paired loses its notification.
                pairedIds.forEach { id -> if (captureRepository.getById(id)?.status == "PAIRED") notifier.cancel(id) }
                // A confirmed capture is something logged today, so the evening reminder stays quiet.
                if (confirmedAny) reminderPreferences.markLoggedToday(LocalDate.now().toEpochDay())
            }
        )
    }
}

/** The Undo snackbar's text for a Confirm. */
object CaptureConfirmMessages {

    fun single(item: CaptureConfirmation, pairing: TransferPairing, accountNames: Map<Long, String>): String {
        val edits = item.edits
        val amount = formatRupeesExact(edits.amount ?: item.capture.amount)
        if (edits.type != TransactionType.TRANSFER) return "Confirmed ${item.title} · $amount as ${edits.category}"

        val isDebit = item.capture.direction != "CREDIT"
        val other = edits.otherAccountId?.let { accountNames[it] } ?: "the other Account"
        val otherHalf = if (isDebit) "credit" else "debit"
        return when (pairing) {
            is TransferPairing.One -> "Transfer saved, and the matching $other $otherHalf too"
            is TransferPairing.Ambiguous ->
                "Transfer saved. ${pairing.count} ${otherHalf}s could be its other half, so they're still waiting"
            TransferPairing.None -> "Transfer saved · $amount ${if (isDebit) "to" else "from"} $other"
        }
    }

    fun several(items: List<CaptureConfirmation>): String =
        "Confirmed ${items.size} · ${formatRupeesExact(items.sumOf { it.edits.amount ?: it.capture.amount })}"
}

/**
 * Carries a card's unsaved choices from To review to the Add Transaction form opened with Edit.
 * Lost if the app is killed in between; the form then starts from the capture's own suggestions.
 */
@Singleton
class CaptureDraftHandoff @Inject constructor() {
    private val drafts = ConcurrentHashMap<Long, CaptureEdits>()

    fun put(captureId: Long, edits: CaptureEdits) {
        drafts[captureId] = edits
    }

    fun take(captureId: Long): CaptureEdits? = drafts.remove(captureId)
}
