package com.issaczerubbabel.ledgar.viewmodel

import java.time.LocalDate

/**
 * The date on Add Transaction's date pill. A date nobody fixed is not stored: it is today, read
 * when it's needed, so a screen left open past midnight saves on the new day (#22).
 *
 * Kept apart from [LogViewModel] so these rules are tested without building the ViewModel, the
 * way [StartCycleUiState] is.
 */
data class LogDateState(
    /**
     * A date fixed in place: picked by the user, or the date of the Transaction being edited or
     * copied, a Repeat rule's occurrence, a capture, or the Calendar's "Add on <day>". Null is today.
     */
    val fixedDate: LocalDate? = null,
    /** The user picked [fixedDate] on another day than it (#16): kept after a save, shown in the kept-date style. */
    val pinned: Boolean = false,
    /**
     * The user picked a date, or saved, on this screen. The date it was opened for (a prefill) is
     * then not applied again when the screen comes back after the process was killed.
     */
    val settled: Boolean = false
) {
    /** The date the pill shows, and a Save uses, when today is [today]. */
    fun dateOn(today: LocalDate): LocalDate = fixedDate ?: today

    /** The user picked [date] on the date pill. Picking today means today, whatever day that later is. */
    fun pick(date: LocalDate, today: LocalDate): LogDateState =
        if (date == today) LogDateState(settled = true) else LogDateState(fixedDate = date, pinned = true, settled = true)

    /**
     * The Transaction being edited or copied was loaded with [date] (null: Copy to today). Always
     * applies: the rest of the form is loaded again too after the process was killed.
     */
    fun load(date: LocalDate?): LogDateState = copy(fixedDate = date, pinned = false)

    /**
     * The screen was opened for [date]: the Calendar's "Add on <day>", a Repeat rule's occurrence
     * or a capture. Not applied over a date the user picked, or after a save.
     */
    fun prefill(date: LocalDate): LogDateState = if (settled) this else copy(fixedDate = date, pinned = false)

    /** After a new Transaction is saved and the form starts again: a pinned date stays, anything else is today. */
    fun afterSave(): LogDateState = if (pinned) copy(settled = true) else LogDateState(settled = true)

    companion object {
        /**
         * Back from SavedStateHandle. Only a pinned date is kept there, so an old "today" saved
         * before midnight is never brought back (v1.8.0 saved the date even when nobody picked it).
         */
        fun restore(savedDate: String?, pinned: Boolean, settled: Boolean): LogDateState {
            val date = savedDate?.takeIf { pinned }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?: return LogDateState(settled = settled)
            return LogDateState(fixedDate = date, pinned = true, settled = true)
        }
    }
}
