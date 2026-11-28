package com.issaczerubbabel.ledgar.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Add Transaction's date pill: a date nobody picked is today, even past midnight (#22); a picked one stays (#16). */
class LogDateStateTest {

    private val sep30 = LocalDate.of(2026, 9, 30)
    private val oct1 = LocalDate.of(2026, 10, 1)
    private val aug1 = LocalDate.of(2026, 8, 1)

    @Test
    fun aDateNobodyPickedMovesOnAtMidnight() {
        // Opened at 23:50 on 30 Sep and left alone.
        val state = LogDateState()
        assertEquals(sep30, state.dateOn(sep30))

        // Saved at 00:05 on 1 Oct.
        assertEquals(oct1, state.dateOn(oct1))
    }

    @Test
    fun aPickedDateStaysPastMidnight() {
        val state = LogDateState().pick(aug1, today = sep30)

        assertEquals(aug1, state.dateOn(oct1))
        assertTrue(state.pinned)
    }

    @Test
    fun pickingTodayMeansTodayOnTheNextDayToo() {
        val state = LogDateState().pick(aug1, today = sep30).pick(sep30, today = sep30)

        assertFalse(state.pinned)
        assertEquals(oct1, state.dateOn(oct1))
    }

    @Test
    fun aPickedDateIsKeptAfterASave() {
        val state = LogDateState().pick(aug1, today = sep30).afterSave()

        assertEquals(aug1, state.dateOn(oct1))
        assertTrue(state.pinned)
    }

    @Test
    fun anOpeningDateIsForOneTransactionOnly() {
        // The Calendar's "Add on 1 Aug": that date, not pinned, then today after the save.
        val state = LogDateState().prefill(aug1)
        assertEquals(aug1, state.dateOn(sep30))
        assertFalse(state.pinned)

        assertEquals(oct1, state.afterSave().dateOn(oct1))
    }

    @Test
    fun anOpeningDateDoesNotReplaceADateTheUserPicked() {
        // Back after the process was killed: the prefill is applied again, but the user had picked today.
        val state = LogDateState().pick(sep30, today = sep30).prefill(aug1)

        assertEquals(oct1, state.dateOn(oct1))
    }

    @Test
    fun anOpeningDateIsNotAppliedAgainAfterASave() {
        val saved = LogDateState().prefill(aug1).afterSave()

        assertEquals(oct1, saved.prefill(aug1).dateOn(oct1))
    }

    @Test
    fun anEditedOrCopiedTransactionKeepsItsOwnDate() {
        val state = LogDateState().load(aug1)

        assertEquals(aug1, state.dateOn(oct1))
        assertFalse(state.pinned)
    }

    @Test
    fun copyToTodayFollowsToday() {
        assertEquals(oct1, LogDateState().load(null).dateOn(oct1))
    }

    @Test
    fun anOldTodaySavedBeforeTheProcessWasKilledIsNotBroughtBack() {
        // v1.8.0 saved 30 Sep even though nobody picked it.
        val restored = LogDateState.restore(savedDate = "2026-09-30", pinned = false, settled = false)

        assertEquals(oct1, restored.dateOn(oct1))
    }

    @Test
    fun aPinnedDateComesBackAfterTheProcessWasKilled() {
        val restored = LogDateState.restore(savedDate = "2026-08-01", pinned = true, settled = false)

        assertEquals(aug1, restored.dateOn(oct1))
        assertTrue(restored.pinned)
        // Still not replaced by the date the screen was opened for.
        assertEquals(aug1, restored.prefill(sep30).dateOn(oct1))
    }

    @Test
    fun anUnreadablePinnedDateFallsBackToToday() {
        val restored = LogDateState.restore(savedDate = "not a date", pinned = true, settled = true)

        assertEquals(oct1, restored.dateOn(oct1))
        assertFalse(restored.pinned)
    }
}
