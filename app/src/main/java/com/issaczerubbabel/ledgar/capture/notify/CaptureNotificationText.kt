package com.issaczerubbabel.ledgar.capture.notify

import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.formatRupeesExact

/** The name shown for a capture: its merchant or payee, or a plain fallback when it has none. */
fun CapturedTransaction.displayTitle(): String =
    merchantRaw?.trim()?.takeIf { it.isNotEmpty() }
        ?: if (suggestedType == TransactionType.INCOME) "Money received" else "Payment"

data class CaptureNotificationContent(
    val title: String,
    val text: String,
    /** What the lock screen shows instead, so amounts and merchants are not readable there. */
    val publicTitle: String
)

object CaptureNotificationText {

    fun build(capture: CapturedTransaction, category: String, accountName: String): CaptureNotificationContent {
        val sign = if (capture.suggestedType == TransactionType.INCOME) "+" else "−"
        return CaptureNotificationContent(
            title = "${capture.displayTitle()}  $sign${formatRupeesExact(capture.amount)}",
            text = "$category · $accountName. Confirm it, or open the app to change it.",
            publicTitle = "A captured transaction is waiting"
        )
    }
}
