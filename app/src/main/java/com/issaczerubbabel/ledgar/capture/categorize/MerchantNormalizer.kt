package com.issaczerubbabel.ledgar.capture.categorize

/** [norm] is the cleaned, comparable merchant key; [isP2p] flags a phone-number VPA handle. */
data class NormalizedMerchant(val norm: String, val isP2p: Boolean)

/** Cleans a raw merchant/payee string into a stable key for rule lookup and history matching. */
object MerchantNormalizer {

    private val STOP_WORDS = setOf(
        "pvt", "ltd", "limited", "private", "india", "technologies", "tech",
        "services", "co", "the", "payments", "systems"
    )
    private val PHONE_HANDLE = Regex("""^\+?\d{10,12}$""")
    private val SEPARATORS = Regex("""[*_./\-]""")
    private val DIGITS = Regex("""\d+""")
    private val WHITESPACE = Regex("""\s+""")

    fun normalize(raw: String?): NormalizedMerchant {
        if (raw.isNullOrBlank()) return NormalizedMerchant("", false)
        var s = raw.trim()

        if (s.contains("@")) {
            val handle = s.substringBefore("@")
            if (PHONE_HANDLE.matches(handle)) return NormalizedMerchant("P2P:$handle", true)
            s = handle
        }

        s = s.replace(SEPARATORS, " ").replace(DIGITS, " ").lowercase()
        val tokens = s.split(WHITESPACE).filter { it.length > 1 && it !in STOP_WORDS }
        return NormalizedMerchant(tokens.joinToString(" ").uppercase(), false)
    }
}
