package com.issaczerubbabel.ledgar.capture.notify

import com.issaczerubbabel.ledgar.data.local.dao.CaptureDao
import com.issaczerubbabel.ledgar.data.repository.CaptureEdits
import com.issaczerubbabel.ledgar.data.repository.CaptureRepository
import com.issaczerubbabel.ledgar.util.TransactionType
import javax.inject.Inject

/** What the notification's buttons do. Confirm only ever uses what the capture already suggests. */
class CaptureActionHandler @Inject constructor(
    private val captureDao: CaptureDao,
    private val captureRepository: CaptureRepository
) {

    /**
     * Returns true if the capture became a Transaction. It needs both a suggested Category and a
     * known Account; without them nothing is saved and the capture stays waiting in the inbox.
     */
    suspend fun confirm(captureId: Long): Boolean {
        val capture = captureDao.getById(captureId) ?: return false
        if (capture.status != "PENDING") return false
        val category = capture.suggestedCategory?.takeIf { it.isNotBlank() } ?: return false
        val accountId = capture.accountId ?: return false

        return captureRepository.confirm(
            captureId,
            CaptureEdits(
                type = capture.suggestedType ?: TransactionType.EXPENSE,
                category = category,
                accountId = accountId,
                description = capture.displayTitle()
            )
        ) != null
    }

    suspend fun dismiss(captureId: Long) = captureRepository.dismiss(captureId)
}
