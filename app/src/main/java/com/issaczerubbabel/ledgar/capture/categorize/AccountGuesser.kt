package com.issaczerubbabel.ledgar.capture.categorize

/**
 * Suggests an Account for a capture whose account digits have no Account alias: when the alert
 * came from a bank's own app and every alias seen with that bank's alerts points to the same one
 * Account. It is only a suggestion; the alias is saved when the user confirms, as with any pick.
 */
object AccountGuesser {

    /**
     * [isBankApp] is whether the alert came from a bank's app (not a UPI app or an SMS), and
     * [accountsAliasedForBank] the Accounts that aliases seen on that bank's alerts point to.
     */
    fun guess(isBankApp: Boolean, accountsAliasedForBank: Set<Long>): Long? =
        accountsAliasedForBank.singleOrNull()?.takeIf { isBankApp }

    const val WHY = "The only Account you use with this bank"
}
