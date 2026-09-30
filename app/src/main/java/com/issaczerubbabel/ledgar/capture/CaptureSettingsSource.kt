package com.issaczerubbabel.ledgar.capture

/** The two auto-capture settings the ingestor needs, so it can be tested without DataStore. */
interface CaptureSettingsSource {
    suspend fun isEnabled(): Boolean

    /** Canonical category key (e.g. "food_delivery") to the name of one of the user's Categories. */
    suspend fun categoryMapping(): Map<String, String>
}
