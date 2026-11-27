package com.issaczerubbabel.ledgar.capture.notify

import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.formatRupeesExact

/**
 * The name shown for a capture: what the user renamed its merchant to ([rename], from the Merchant
 * rule), else its merchant or payee as the bank wrote it, else a plain fallback.
 */
fun CapturedTransaction.displayTitle(rename: String? = null): String =
    rename?.trim()?.takeIf { it.isNotEmpty() }
        ?: merchantRaw?.trim()?.takeIf { it.isNotEmpty() }
        ?: if (suggestedType == TransactionType.INCOME) "Money received" else "Payment"

data class CaptureNotificationContent(
    val title: String,
    val text: String,
    /** What the lock screen shows instead, so amounts and merchants are not readable there. */
    val publicTitle: String
)

object CaptureNotificationText {

    /**
     * [otherAccountName] is a Transfer's other Account (To for a debit, From for a credit): with it,
     * the notification reads "Transfer ₹5,000 to HDFC Credit Card" and [category] is ignored.
     */
    fun build(
        capture: CapturedTransaction,
        title: String,
        category: String,
        accountName: String,
        otherAccountName: String? = null
    ): CaptureNotificationContent {
        if (capture.suggestedType == TransactionType.TRANSFER && otherAccountName != null) {
            val isDebit = capture.direction != "CREDIT"
            return CaptureNotificationContent(
                title = "Transfer ${formatRupeesExact(capture.amount)} ${if (isDebit) "to" else "from"} $otherAccountName",
                text = "$title · ${if (isDebit) "from" else "to"} $accountName. Confirm it, or open the app to change it.",
                publicTitle = PUBLIC_TITLE
            )
        }
        val sign = if (capture.suggestedType == TransactionType.INCOME) "+" else "−"
        return CaptureNotificationContent(
            title = "$title  $sign${formatRupeesExact(capture.amount)}",
            text = "$category · $accountName. Confirm it, or open the app to change it.",
            publicTitle = PUBLIC_TITLE
        )
    }

    private const val PUBLIC_TITLE = "A captured transaction is waiting"
}
