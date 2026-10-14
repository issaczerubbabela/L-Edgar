package com.issaczerubbabel.ledgar.viewmodel

import java.time.LocalDate

/**
 * The date pill's state on Add Transaction. [pinned] is the user's own choice of a date other than
 * today (kept across a save, shown in the "kept date" style); [tracksToday] is whether
 * [selectedDate] should keep following the live "today" ([com.issaczerubbabel.ledgar.util.CurrentDate]
 * drives that from [LogViewModel]). Kept pure and separate from the Hilt ViewModel so the
 * today-tracking rules behind issue #22 are unit tested directly, the way `AccountForm` and
 * `AccountMath` are tested without instantiating their ViewModels.
 */
data class LogDateState(
    val selectedDate: LocalDate,
    val pinned: Boolean,
    val tracksToday: Boolean
) {
    /** The user picked [date] from the date pill's picker. Picking today itself clears a pin. */
    fun pick(date: LocalDate, today: LocalDate): LogDateState {
        val nowPinned = date != today
        return copy(selectedDate = date, pinned = nowPinned, tracksToday = !nowPinned)
    }

    /** Editing, copying, a Repeat rule or a Calendar "Add on <day>": an explicit date that isn't a user pin. */
    fun setExplicit(date: LocalDate): LogDateState = copy(selectedDate = date, tracksToday = false)

    /** [com.issaczerubbabel.ledgar.util.CurrentDate.today] moved on; only takes while nothing fixed the date in place. */
    fun followToday(today: LocalDate): LogDateState = if (tracksToday) copy(selectedDate = today) else this

    /** After a save, on a brand-new (non-edit) Transaction: an unpinned date resumes tracking today. */
    fun resetAfterSave(today: LocalDate): LogDateState =
        if (pinned) this else LogDateState(today, pinned = false, tracksToday = true)

    /** The date a save should actually use: [today] at that exact moment while tracking it, never a stale read. */
    fun dateToSave(today: LocalDate): LocalDate = if (tracksToday) today else selectedDate

    companion object {
        /** The initial state restored from SavedStateHandle: [savedDate] is only trusted when [pinned]. */
        fun restore(savedDate: LocalDate?, pinned: Boolean, today: LocalDate): LogDateState {
            val date = if (pinned) savedDate ?: today else today
            return LogDateState(date, pinned, tracksToday = !pinned)
        }
    }
}
