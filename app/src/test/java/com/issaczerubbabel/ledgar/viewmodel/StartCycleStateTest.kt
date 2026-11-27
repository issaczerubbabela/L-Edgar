package com.issaczerubbabel.ledgar.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Start new cycle's Carry over copies Planned contributions with the Buckets, and on its own (#41). */
class StartCycleStateTest {

    private val today = LocalDate.of(2026, 10, 25)

    private fun state(buckets: Int, plans: Int, carryOver: Boolean? = null): StartCycleUiState {
        val initial = StartCycleUiState(isLoaded = true, startDate = today, carryableBucketCount = buckets, carryablePlanCount = plans)
            .let { it.copy(carryOver = it.canCarryOver) }
        return if (carryOver == null) initial else initial.copy(carryOver = carryOver)
    }

    @Test
    fun bucketsAndContributionsAreCarriedTogether() {
        val state = state(buckets = 3, plans = 2)

        assertTrue(state.carryOver)
        assertTrue(state.request(115_842.0).carryOverBuckets)
    }

    @Test
    fun contributionsAreCarriedForACycleWithNoBuckets() {
        val state = state(buckets = 0, plans = 2)

        assertTrue(state.canCarryOver)
        assertTrue(state.carryOver)
        assertTrue(state.request(115_842.0).carryOverBuckets)
    }

    @Test
    fun turningCarryOverOffCarriesNothing() {
        assertFalse(state(buckets = 3, plans = 2, carryOver = false).request(115_842.0).carryOverBuckets)
    }

    @Test
    fun withNothingToCarryCarryOverIsOff() {
        val state = state(buckets = 0, plans = 0, carryOver = true)

        assertFalse(state.canCarryOver)
        assertFalse(state.request(115_842.0).carryOverBuckets)
    }

    @Test
    fun theRequestCarriesTheDatesAndAmount() {
        val request = state(buckets = 1, plans = 0).request(68_000.0)

        assertEquals(today, request.startDate)
        assertEquals(68_000.0, request.spendableAmount, 0.0)
    }

    @Test
    fun carryOverSaysWhatItCopies() {
        assertEquals("3 buckets and their categories, and 2 planned contributions. Spending starts again from zero.", state(3, 2).carryOverText)
        assertEquals("1 planned contribution. Set aside starts again from zero.", state(0, 1).carryOverText)
        assertEquals("1 bucket and its categories. Spending starts again from zero.", state(1, 0).carryOverText)
        assertEquals("You have no buckets to carry over yet.", state(0, 0).carryOverText)
    }
}
