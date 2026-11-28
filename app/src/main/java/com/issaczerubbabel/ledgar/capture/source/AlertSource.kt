package com.issaczerubbabel.ledgar.capture.source

import com.issaczerubbabel.ledgar.capture.CaptureSource

enum class AlertSourceKind { SMS, BANK_APP, UPI_APP, EMAIL }

/**
 * Where an Alert came from (CONTEXT.md): one bank's SMS, one bank or UPI app, or one bank's email.
 * Two Alerts from the same Alert source are never one payment's (ADR-0012), so this is also what
 * keeps two identical payments apart.
 */
data class AlertSource(val kind: AlertSourceKind, val name: String) {

    /** Only a bank's own Alert (SMS, app or email) is about one of its accounts. */
    val isBank: Boolean get() = kind != AlertSourceKind.UPI_APP

    /** How the card's Original text names it: "SMS from CUB", "Google Pay". */
    val label: String
        get() = when (kind) {
            AlertSourceKind.SMS -> "SMS from $name"
            AlertSourceKind.EMAIL -> "Email from $name"
            AlertSourceKind.BANK_APP, AlertSourceKind.UPI_APP -> name
        }

    /** Stored in a capture's trace; [fromKey] reads it back. */
    val key: String get() = "${kind.name}:$name"

    companion object {
        /** Bank SMS IDs vary by prefix and suffix ("AD-HDFCBK-S", "JM-HDFCBN-T"); the bank is the middle. */
        private val SMS_ID = Regex("""^[A-Za-z]{2}-([A-Za-z0-9]{3,8})(-[A-Za-z0-9])?$""")

        /**
         * Banks' own SMS IDs (the middle part) to the bank, for banks with more than one. Anything else,
         * an insurer's or fund's "HDFCLI" or "AXISMF" included, is its own source.
         */
        private val BANK_SMS_IDS = mapOf("HDFCBK" to "HDFC", "HDFCBN" to "HDFC", "AXISBK" to "Axis", "ICICIB" to "ICICI", "ICICIT" to "ICICI")

        /** An email's or other sender's name to the bank, by a word in it. */
        private val BANK_WORDS = linkedMapOf("HDFC" to "HDFC", "CUB" to "CUB", "CITY UNION" to "CUB", "AXIS" to "Axis", "ICICI" to "ICICI", "KOTAK" to "Kotak")

        /** [source] is a [CaptureSource]; [sender] is what the parsers were given as the sender. */
        fun of(source: String, sender: String): AlertSource = when {
            source == CaptureSource.EMAIL -> AlertSource(AlertSourceKind.EMAIL, bankOf(sender))
            CaptureApps.isBankApp(sender) -> AlertSource(AlertSourceKind.BANK_APP, sender)
            CaptureApps.isUpiApp(sender) -> AlertSource(AlertSourceKind.UPI_APP, sender)
            else -> AlertSource(AlertSourceKind.SMS, bankOf(sender))
        }

        fun fromKey(key: String): AlertSource? {
            val kind = AlertSourceKind.entries.firstOrNull { key.startsWith("${it.name}:") } ?: return null
            return AlertSource(kind, key.removePrefix("${kind.name}:"))
        }

        private fun bankOf(sender: String): String {
            val trimmed = sender.trim()
            SMS_ID.matchEntire(trimmed)?.groupValues?.get(1)?.uppercase()?.let { id ->
                return BANK_SMS_IDS[id] ?: if (id.startsWith("CUB")) "CUB" else id
            }
            return BANK_WORDS.entries.firstOrNull { trimmed.contains(it.key, ignoreCase = true) }?.value ?: trimmed
        }
    }
}
