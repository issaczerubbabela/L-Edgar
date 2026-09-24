package com.issaczerubbabel.ledgar.data.bucket

import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import java.time.LocalDate
import kotlin.math.abs

data class StartCycleRequest(
    /** Payday. May be in the past: spend from this date on moves into the new cycle. */
    val startDate: LocalDate,
    val endDate: LocalDate,
    val spendableAmount: Double,
    /** Copy the running cycle's buckets and category routing into the new one. */
    val carryOverBuckets: Boolean
)

enum class StartCycleError {
    INVALID_AMOUNT,
    END_BEFORE_START,
    /** The new cycle would begin on or before the day the running one began. */
    START_NOT_AFTER_CURRENT
}

sealed interface StartCycleResult {
    data class Started(val cycleId: Long) : StartCycleResult
    data class Rejected(val error: StartCycleError) : StartCycleResult
}

/** The rules for moving from one cycle to the next. Pure, so they are tested without a database. */
object CycleTransitions {

    fun validate(request: StartCycleRequest, running: BudgetCycle?): StartCycleError? {
        if (!request.spendableAmount.isFinite() || request.spendableAmount < 0.0) {
            return StartCycleError.INVALID_AMOUNT
        }
        if (request.endDate.isBefore(request.startDate)) return StartCycleError.END_BEFORE_START
        if (running != null && !request.startDate.isAfter(LocalDate.parse(running.startDate))) {
            return StartCycleError.START_NOT_AFTER_CURRENT
        }
        return null
    }

    /**
     * The running cycle once the next one starts: closed, and ended the day before the new one
     * begins. This handles all three real cases the same way. An overdue cycle stretches to cover
     * the gap it was absorbing, a backdated start trims it so spend from the real payday moves on,
     * and an on-time handover leaves it as it was.
     */
    fun closeForNext(previous: BudgetCycle, request: StartCycleRequest, today: LocalDate): BudgetCycle =
        previous.copy(
            endDate = request.startDate.minusDays(1).toString(),
            closedAt = today.toString()
        )
}

/** Suggests the spendable amount from a salary that has already landed. */
object SalaryDetector {

    const val SALARY_CATEGORY = "Salary"

    /**
     * Total of Income records categorised Salary within [windowDays] of [around], or null when
     * none is found. Offered as a suggestion only; it is never applied without the user confirming.
     */
    fun detect(records: List<ExpenseRecord>, around: LocalDate, windowDays: Long = 5): Double? {
        val matches = records.filter { record ->
            if (record.type != "Income" || !record.category.trim().equals(SALARY_CATEGORY, ignoreCase = true)) {
                return@filter false
            }
            val date = parseFlexibleDate(record.date) ?: return@filter false
            abs(java.time.temporal.ChronoUnit.DAYS.between(around, date)) <= windowDays
        }
        return matches.takeIf { it.isNotEmpty() }?.sumOf { it.amount }
    }
}
