package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.bucket.CycleCalendar
import com.issaczerubbabel.ledgar.data.bucket.SalaryDetector
import com.issaczerubbabel.ledgar.data.bucket.StartCycleError
import com.issaczerubbabel.ledgar.data.bucket.StartCycleRequest
import com.issaczerubbabel.ledgar.data.bucket.StartCycleResult
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.util.amountToInput
import com.issaczerubbabel.ledgar.util.parseAmountInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

enum class StartCycleFormError { AMOUNT, END_BEFORE_START, START_NOT_AFTER_CURRENT }

data class StartCycleUiState(
    val isLoaded: Boolean = false,
    val startDate: LocalDate = LocalDate.now(),
    val endDate: LocalDate = CycleCalendar.defaultEnd(LocalDate.now()),
    val amountInput: String = "",
    val amountWasPrefilled: Boolean = false,
    val carryOver: Boolean = false,
    /** Start date of the cycle this one will close, or null for the very first cycle. */
    val runningCycleStart: LocalDate? = null,
    val carryableBucketCount: Int = 0,
    /** A salary found near the start date. Offered as a suggestion, never applied silently. */
    val suggestedSalary: Double? = null,
    val error: StartCycleFormError? = null,
    val isSaving: Boolean = false,
    val isDone: Boolean = false
) {
    val cycleDays: Int get() = CycleCalendar.totalDays(startDate, endDate)
}

@HiltViewModel
class StartCycleViewModel @Inject constructor(
    private val budgets: BucketBudgetRepository,
    private val expenses: ExpenseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StartCycleUiState())
    val uiState: StateFlow<StartCycleUiState> = _uiState.asStateFlow()

    private var records: List<ExpenseRecord> = emptyList()

    /** Once the user picks an end date themselves we stop moving it when the start date changes. */
    private var endDateChosenByUser = false

    init {
        viewModelScope.launch {
            val running = budgets.getRunningCycle()
            records = expenses.getAllRecords().first()
            val bucketCount = running?.let { budgets.getBuckets(it.id).size } ?: 0
            val today = LocalDate.now()
            _uiState.value = StartCycleUiState(
                isLoaded = true,
                startDate = today,
                endDate = CycleCalendar.defaultEnd(today),
                amountInput = running?.let { amountToInput(it.spendableAmount) }.orEmpty(),
                amountWasPrefilled = running != null,
                carryOver = bucketCount > 0,
                runningCycleStart = running?.let { LocalDate.parse(it.startDate) },
                carryableBucketCount = bucketCount,
                suggestedSalary = SalaryDetector.detect(records, today)
            )
        }
    }

    fun setStartDate(date: LocalDate) = _uiState.update { state ->
        val end = if (endDateChosenByUser && !state.endDate.isBefore(date)) state.endDate else CycleCalendar.defaultEnd(date)
        state.copy(
            startDate = date,
            endDate = end,
            suggestedSalary = SalaryDetector.detect(records, date),
            error = null
        )
    }

    fun setEndDate(date: LocalDate) {
        endDateChosenByUser = true
        _uiState.update { it.copy(endDate = date, error = null) }
    }

    fun setAmountInput(input: String) = _uiState.update {
        // Digits and a single decimal point only; grouping commas are added on display, not typed.
        it.copy(amountInput = input.filter { c -> c.isDigit() || c == '.' }, amountWasPrefilled = false, error = null)
    }

    fun setCarryOver(carry: Boolean) = _uiState.update { it.copy(carryOver = carry) }

    fun useSuggestedSalary() = _uiState.update { state ->
        state.suggestedSalary?.let { state.copy(amountInput = amountToInput(it), amountWasPrefilled = false, error = null) }
            ?: state
    }

    fun start() {
        val state = _uiState.value
        if (state.isSaving) return
        val amount = parseAmountInput(state.amountInput)
        if (amount == null) {
            _uiState.update { it.copy(error = StartCycleFormError.AMOUNT) }
            return
        }
        _uiState.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val result = budgets.startCycle(
                StartCycleRequest(
                    startDate = state.startDate,
                    endDate = state.endDate,
                    spendableAmount = amount,
                    carryOverBuckets = state.carryOver && state.carryableBucketCount > 0
                )
            )
            _uiState.update {
                when (result) {
                    is StartCycleResult.Started -> it.copy(isSaving = false, isDone = true)
                    is StartCycleResult.Rejected -> it.copy(isSaving = false, error = result.error.toFormError())
                }
            }
        }
    }

    private fun StartCycleError.toFormError() = when (this) {
        StartCycleError.INVALID_AMOUNT -> StartCycleFormError.AMOUNT
        StartCycleError.END_BEFORE_START -> StartCycleFormError.END_BEFORE_START
        StartCycleError.START_NOT_AFTER_CURRENT -> StartCycleFormError.START_NOT_AFTER_CURRENT
    }
}
