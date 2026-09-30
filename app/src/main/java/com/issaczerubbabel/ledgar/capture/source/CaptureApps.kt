package com.issaczerubbabel.ledgar.capture.source

import com.issaczerubbabel.ledgar.BuildConfig

/**
 * The apps whose notifications auto-capture is allowed to read. Anything else is never looked at.
 * Bank SMS arrives through the messaging app's notification, so no SMS permission is needed.
 */
object CaptureApps {

    const val GOOGLE_MESSAGES = "com.google.android.apps.messaging"

    /** Package name to the label the parsers recognise as the sender. */
    private val labels: Map<String, String> = mapOf(
        "com.hdfcbank.android.now" to "HDFC Bank",
        "com.axis.mobile" to "Axis Bank",
        "com.cub.plus.gui" to "City Union Bank",
        "com.google.android.apps.nbu.paisa.user" to "Google Pay",
        "net.one97.paytm" to "Paytm",
        GOOGLE_MESSAGES to "Messages"
    )

    /**
     * Debug builds also accept `adb shell cmd notification post`, which posts as the shell, so a
     * capture can be tried without making a real payment. Release builds never look at it.
     */
    private const val SHELL = "com.android.shell"

    fun isWatched(packageName: String): Boolean =
        packageName in labels || (BuildConfig.DEBUG && packageName == SHELL)

    /** The label passed to the parsers, or null for text that carries its own sender (SMS). */
    fun labelFor(packageName: String): String? = labels[packageName]?.takeUnless { packageName == GOOGLE_MESSAGES }
}
