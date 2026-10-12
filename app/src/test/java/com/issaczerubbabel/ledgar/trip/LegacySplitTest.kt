package com.issaczerubbabel.ledgar.trip

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacySplitTest {

    private fun row(id: Long, input: Long, share: Long) = LegacySplit.Row(id, input, share)

    @Test
    fun equalExpensesStayEqual() {
        val rows = listOf(row(1, 0, 500), row(2, 0, 500))
        assertEquals(emptyMap<Long, Long>(), LegacySplit.lockedFor("EQUAL", 1, 1000, rows))
    }

    @Test
    fun exactExpensesLockEveryone() {
        val rows = listOf(row(1, 40000, 40000), row(3, 80000, 80000))
        assertEquals(mapOf(1L to 40000L, 3L to 80000L), LegacySplit.lockedFor("EXACT", 8, 120000, rows))
    }

    @Test
    fun anAdjustedExpenseLocksOnlyTheAdjustedPersonAtTheirFullShare() {
        // ₹3,460 with Arjun +₹300: Arjun's Share was ₹1,090 and the others' ₹790.
        val rows = listOf(row(1, 0, 79000), row(2, 0, 79000), row(3, 0, 79000), row(4, 30000, 109000))
        assertEquals(mapOf(4L to 109000L), LegacySplit.lockedFor("ADJUST", 7, 346000, rows))
    }

    @Test
    fun anAdjustedExpenseLocksEveryoneWhenLockingOneWouldMoveAPaisa() {
        // 1002 paise, id 2, Rahul +100: the old rotation gave the spare paise to Priya and Arjun,
        // but locking only Rahul would hand them to You and Arjun.
        val rows = listOf(row(1, 0, 225), row(2, 100, 325), row(3, 0, 226), row(4, 0, 226))
        assertEquals(mapOf(1L to 225L, 2L to 325L, 3L to 226L, 4L to 226L), LegacySplit.lockedFor("ADJUST", 2, 1002, rows))
    }

    @Test
    fun everyConvertedExpenseKeepsEveryShareExactly() {
        val cases = listOf(
            Triple("ADJUST", 7L, listOf(row(1, 0, 79000), row(2, 0, 79000), row(3, 0, 79000), row(4, 30000, 109000))),
            Triple("ADJUST", 2L, listOf(row(1, 0, 225), row(2, 100, 325), row(3, 0, 226), row(4, 0, 226))),
            Triple("EXACT", 8L, listOf(row(1, 40000, 40000), row(3, 80000, 80000))),
            Triple("EQUAL", 1L, listOf(row(1, 0, 333), row(2, 0, 334), row(3, 0, 333)))
        )
        for ((mode, id, rows) in cases) {
            val amount = rows.sumOf { it.sharePaise }
            val locked = LegacySplit.lockedFor(mode, id, amount, rows)
            val converted = TripExpenseInput(id, "", "", amount, 1, if (mode == "EQUAL") SplitMode.EQUAL else SplitMode.CUSTOM, rows.map { it.memberId }, locked)
            assertEquals("$mode $id", rows.associate { it.memberId to it.sharePaise }, TripMath.shares(converted))
        }
    }
}
