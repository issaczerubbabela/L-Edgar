package com.issaczerubbabel.ledgar.trip

/**
 * Converts expenses saved with the old Adjust and Exact split modes to Custom, for the 22 -> 23
 * upgrade. Every Member's Share must come out exactly as stored, so the conversion locks as few
 * Members as it can while still reproducing every stored Share, and everyone when it can't.
 */
object LegacySplit {

    data class Row(val memberId: Long, val inputPaise: Long, val sharePaise: Long)

    /**
     * The Members to lock, with their amounts, for a legacy expense. [rows] are in Trip Member
     * order. Returns an empty map for Equal expenses, which stay Equal.
     */
    fun lockedFor(mode: String, expenseId: Long, amountPaise: Long, rows: List<Row>): Map<Long, Long> {
        when (mode) {
            "EXACT" -> return rows.associate { it.memberId to it.sharePaise }
            "ADJUST" -> Unit
            else -> return emptyMap()
        }
        val allLocked = rows.associate { it.memberId to it.sharePaise }
        val minimal = rows.filter { it.inputPaise != 0L }.associate { it.memberId to it.sharePaise }
        val candidate = TripExpenseInput(
            id = expenseId, date = "", purpose = "", amountPaise = amountPaise, payerId = 0L,
            mode = SplitMode.CUSTOM, memberIds = rows.map { it.memberId }, locked = minimal
        )
        val reproduces = TripMath.shares(candidate) == rows.associate { it.memberId to it.sharePaise }
        return if (reproduces) minimal else allLocked
    }
}
