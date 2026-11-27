package com.issaczerubbabel.ledgar.capture

import kotlinx.coroutines.CancellationException

/**
 * Runs [block] as a follow-up that must never fail what it follows, such as learning or guessing
 * again after a decision is saved: any failure is swallowed and gives null. Cancellation still
 * propagates, so a cancelled caller stops.
 */
suspend fun <R> bestEffort(block: suspend () -> R): R? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (_: Exception) {
    null
}
