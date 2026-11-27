package com.issaczerubbabel.ledgar.capture.notify

import com.issaczerubbabel.ledgar.data.local.dao.CaptureDao
import com.issaczerubbabel.ledgar.data.preferences.ReminderPreferences
import com.issaczerubbabel.ledgar.data.repository.CaptureEdits
import com.issaczerubbabel.ledgar.data.repository.CaptureRepository
import com.issaczerubbabel.ledgar.util.TransactionType
import java.time.LocalDate
import javax.inject.Inject

/** What the notification's buttons do. Confirm only ever uses what the capture already suggests. */
class CaptureActionHandler @Inject constructor(
    private val captureDao: CaptureDao,
    private val captureRepository: CaptureRepository,
    private val reminderPreferences: ReminderPreferences
) {

    /**
     * Returns true if the capture became a Transaction. It needs a known Account and a suggested
     * Category, or for a Transfer rule its other Account; without them nothing is saved and the
     * capture stays waiting in the inbox. So does a capture that has since gained a match in the
     * Ledger: the user decides in To review. A Transfer pairs its other half as the inbox's Confirm does.
     */
    suspend fun confirm(captureId: Long): Boolean {
        val capture = captureDao.getById(captureId) ?: return false
        if (capture.status != "PENDING") return false
        val accountId = capture.accountId ?: return false
        val title = captureRepository.titleFor(capture)
        val edits = if (capture.suggestedType == TransactionType.TRANSFER) {
            val otherAccountId = capture.suggestedToAccountId?.takeIf { it != accountId } ?: return false
            CaptureEdits(TransactionType.TRANSFER, TransactionType.TRANSFER, accountId, title, otherAccountId = otherAccountId)
        } else {
            val category = capture.suggestedCategory?.takeIf { it.isNotBlank() } ?: return false
            CaptureEdits(capture.suggestedType ?: TransactionType.EXPENSE, category, accountId, title)
        }
        if (captureRepository.findLedgerMatch(captureId) != null) return false

        val confirmed = captureRepository.confirm(captureId, edits) != null
        // A confirmed capture is something logged today, so the evening reminder stays quiet.
        if (confirmed) reminderPreferences.markLoggedToday(LocalDate.now().toEpochDay())
        return confirmed
    }

    suspend fun dismiss(captureId: Long) = captureRepository.dismiss(captureId)
}
