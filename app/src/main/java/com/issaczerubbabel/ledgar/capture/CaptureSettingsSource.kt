package com.issaczerubbabel.ledgar.capture

/** The auto-capture settings the ingestor needs, so it can be tested without DataStore. */
interface CaptureSettingsSource {
    suspend fun isEnabled(): Boolean

    /** Read bank emails: Gmail's notifications from known senders are read only while this is on (#180). */
    suspend fun readsBankEmails(): Boolean = false

    /** Canonical category key (e.g. "food_delivery") to the name of one of the user's Categories. */
    suspend fun categoryMapping(): Map<String, String>
}
