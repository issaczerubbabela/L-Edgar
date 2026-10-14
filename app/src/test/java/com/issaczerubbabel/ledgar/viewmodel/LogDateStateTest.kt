package com.issaczerubbabel.ledgar.viewmodel

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogDateStateTest {
    private val sep30 = LocalDate.parse("2026-09-30")
    private val oct1 = LocalDate.parse("2026-10-01")
    private val aug1 = LocalDate.parse("2026-08-01")

    @Test
    fun `a fresh screen with nothing saved tracks today`() {
        val state = LogDateState.restore(savedDate = null, pinned = false, today = sep30)

        assertEquals(sep30, state.selectedDate)
        assertFalse(state.pinned)
        assertTrue(state.tracksToday)
    }

    @Test
    fun `restoring a pinned date keeps it, even once today has moved on`() {
        val state = LogDateState.restore(savedDate = aug1, pinned = true, today = oct1)

        assertEquals(aug1, state.selectedDate)
        assertTrue(state.pinned)
        assertFalse(state.tracksToday)
    }

    @Test
    fun `restoring without a pin ignores whatever the saved date says`() {
        // A stale KEY_SELECTED_DATE from before process death: not pinned, so it's never trusted.
        val state = LogDateState.restore(savedDate = sep30, pinned = false, today = oct1)

        assertEquals(oct1, state.selectedDate)
        assertTrue(state.tracksToday)
    }

    @Test
    fun `an unpinned date follows today across midnight`() {
        var state = LogDateState.restore(savedDate = null, pinned = false, today = sep30)

        state = state.followToday(oct1)

        assertEquals(oct1, state.selectedDate)
        assertFalse(state.pinned)
    }

    @Test
    fun `a pinned date does not follow today`() {
        var state = LogDateState.restore(savedDate = null, pinned = false, today = sep30)
        state = state.pick(aug1, sep30)

        state = state.followToday(oct1)

        assertEquals(aug1, state.selectedDate)
        assertTrue(state.pinned)
    }

    @Test
    fun `picking today itself clears any pin and resumes tracking`() {
        var state = LogDateState.restore(savedDate = null, pinned = false, today = sep30)
        state = state.pick(aug1, sep30)

        state = state.pick(sep30, sep30)

        assertFalse(state.pinned)
        assertTrue(state.tracksToday)
    }

    @Test
    fun `picking a different date pins it and stops tracking today`() {
        val state = LogDateState.restore(savedDate = null, pinned = false, today = sep30).pick(aug1, sep30)

        assertEquals(aug1, state.selectedDate)
        assertTrue(state.pinned)
        assertFalse(state.tracksToday)
    }

    @Test
    fun `an explicit date, such as editing or a Repeat rule, stops tracking without pinning`() {
        val state = LogDateState.restore(savedDate = null, pinned = false, today = sep30).setExplicit(aug1)

        assertEquals(aug1, state.selectedDate)
        assertFalse(state.pinned)
        assertFalse(state.tracksToday)
        // An explicit date is not "today moving on"; the next broadcast must leave it alone.
        assertEquals(aug1, state.followToday(oct1).selectedDate)
    }

    @Test
    fun `resetting after a save on a pinned date keeps the pin`() {
        val state = LogDateState.restore(savedDate = null, pinned = false, today = sep30).pick(aug1, sep30)

        val reset = state.resetAfterSave(oct1)

        assertEquals(aug1, reset.selectedDate)
        assertTrue(reset.pinned)
        assertFalse(reset.tracksToday)
    }

    @Test
    fun `resetting after a save on an unpinned date resumes tracking today`() {
        val state = LogDateState.restore(savedDate = null, pinned = false, today = sep30).setExplicit(aug1)

        val reset = state.resetAfterSave(oct1)

        assertEquals(oct1, reset.selectedDate)
        assertFalse(reset.pinned)
        assertTrue(reset.tracksToday)
    }

    @Test
    fun `saving while tracking today resolves the date at that exact moment, not a stale read`() {
        // selectedDate is still sep30 (the screen never recomposed) but today has already moved on.
        val state = LogDateState.restore(savedDate = null, pinned = false, today = sep30)

        assertEquals(oct1, state.dateToSave(oct1))
    }

    @Test
    fun `saving a pinned or explicit date ignores today`() {
        val pinned = LogDateState.restore(savedDate = null, pinned = false, today = sep30).pick(aug1, sep30)
        assertEquals(aug1, pinned.dateToSave(oct1))

        val explicit = LogDateState.restore(savedDate = null, pinned = false, today = sep30).setExplicit(aug1)
        assertEquals(aug1, explicit.dateToSave(oct1))
    }
}
