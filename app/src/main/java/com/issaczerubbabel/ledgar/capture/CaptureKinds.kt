package com.issaczerubbabel.ledgar.capture

/**
 * Kinds of money in that an Alert names itself, mapped once to one of the user's Income Categories
 * in More > Auto-capture, like the merchant kinds of the keyword list (which are Expenses). The app
 * never invents a Category: an unmapped kind shows "Needs a category".
 */
object CaptureKinds {

    /** Amazon Pay's "Your cashback of ₹25.00 is here!" (#181). */
    const val CASHBACK = "cashback"

    /** For the mapping screen, in order. */
    val INCOME: List<String> = listOf(CASHBACK)
}
