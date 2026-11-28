package com.issaczerubbabel.ledgar.capture

/**
 * What an Alert says about which Account the money moved in, the key of an Account alias
 * (CONTEXT.md): a bank account's last digits ("1234"), or, for an Alert source with no digits, the
 * source itself ([ofSource]). Both are stored the same way, in `accountHint` and `account_aliases`,
 * so Map account, Confirm's alias and Transfer pairing treat them alike; a source can never be
 * mistaken for digits.
 */
object AccountHint {

    private const val SOURCE = "source:"

    /** Every Amazon Pay email about money in or out of the balance (#181). */
    val AMAZON_PAY_BALANCE = ofSource("Amazon Pay Balance")

    fun ofSource(name: String): String = SOURCE + name

    /** How the card names it: "XX1234", or the source's own name ("Amazon Pay Balance"). */
    fun label(hint: String): String = if (hint.startsWith(SOURCE)) hint.removePrefix(SOURCE) else "XX$hint"
}
