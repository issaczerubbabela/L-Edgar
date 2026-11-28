package com.issaczerubbabel.ledgar.capture.source

/**
 * A sender of alert emails. [displayName] is the notification's title for one email; for a
 * conversation of several, Gmail's title lists the senders' short names instead ("HDFC, alerts"),
 * and [threadNames] are the ones this sender's emails show there.
 */
data class EmailSender(val displayName: String, val address: String, val threadNames: Set<String> = emptySet())

/**
 * The senders whose alert emails auto-capture reads through Gmail's notification, while Read bank
 * emails is on. Gmail never shows the address, so a notification is matched on its title and on the
 * label the user's Gmail filter (keyed on the addresses) applies.
 */
object BankEmails {

    /**
     * HDFC sends every UPI alert with the same subject, so Gmail threads them, and a thread's title
     * is "HDFC, alerts" or "alerts, HDFC" (read off a phone on 2026-11-28).
     */
    val HDFC = EmailSender("HDFC Bank InstaAlerts", "alerts@hdfcbank.bank.in", threadNames = setOf("HDFC", "alerts"))

    /** Amazon Pay Balance's payments, gift cards and cashback (#181), which reach no bank. */
    val AMAZON_PAY = EmailSender("Amazon Pay India", "no-reply@amazonpay.in")

    /** Read now. A sender joins this list with its parser. */
    private val read: List<EmailSender> = listOf(HDFC, AMAZON_PAY)

    /** What the setup steps ask the user to filter on in Gmail. */
    val FILTER_QUERY: String = listOf(HDFC, AMAZON_PAY).joinToString(" OR ") { it.address }

    /** The label the setup suggests; the user can name their own. */
    const val DEFAULT_LABEL = "Bank alerts"

    /**
     * The sender a notification titled [title] is from: its display name exactly, or a thread's list
     * of two or more short names that are all its own. Null for anyone else.
     */
    fun senderFor(title: String?): EmailSender? {
        val trimmed = title?.trim().orEmpty()
        read.firstOrNull { it.displayName == trimmed }?.let { return it }
        val names = trimmed.split(',').map { it.trim() }
        if (names.size < 2) return null
        return read.firstOrNull { sender -> sender.threadNames.isNotEmpty() && names.all { it in sender.threadNames } }
    }

    /** True for the display name of a sender whose emails are read. */
    fun isKnownSender(displayName: String): Boolean = read.any { it.displayName == displayName.trim() }
}

/** One email as auto-capture reads it: its sender's display name, then its subject and preview. */
data class EmailAlert(val sender: String, val text: String)

/**
 * The only place that knows how Gmail lays out a notification, as read off a phone on 2026-11-28:
 * the title is the sender's display name (or, for a conversation, the senders' short names), the
 * text the subject, the big text the subject again and the email's text, and the sub text the label
 * the notification is for. A notification for several conversations ("18 new messages") is a group
 * summary, which the listener drops before this.
 *
 * Read only when the title is a sender in [BankEmails] and the sub text is the user's [label], the
 * one their Gmail filter applies, so a lookalike display name from another address is never read. A
 * null [label] skips that check, for the debug build's adb posts only.
 */
object GmailNotification {

    fun read(title: String?, text: String?, bigText: String?, subText: String?, label: String?): EmailAlert? {
        val sender = BankEmails.senderFor(title) ?: return null
        if (label != null && (label.isBlank() || !subText?.trim().equals(label.trim(), ignoreCase = true))) return null
        val subject = text?.trim().orEmpty()
        val big = bigText?.trim().orEmpty()
        val preview = if (subject.isNotEmpty() && big.startsWith(subject)) big.removePrefix(subject).trim() else big
        val body = listOf(subject, preview).filter { it.isNotEmpty() }.joinToString("\n")
        return if (body.isBlank()) null else EmailAlert(sender.displayName, body)
    }
}
