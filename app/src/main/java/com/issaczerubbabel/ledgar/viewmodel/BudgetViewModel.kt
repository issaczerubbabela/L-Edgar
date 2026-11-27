package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.FundRepository
import com.issaczerubbabel.ledgar.data.repository.RecurringRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * The Budget home screen: one cycle at a time, the running one first, older ones behind it.
 * The arithmetic is all in [budgetHome].
 */
@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class BudgetViewModel @Inject constructor(
    private val budgets: BucketBudgetRepository,
    private val repeats: RecurringRepository,
    accounts: AccountRepository,
    funds: FundRepository,
    dropdowns: DropdownOptionRepository
) : ViewModel() {

    private val selectedIndex = MutableStateFlow(0)

    /** For the Daily pace after the Repeats still due ([withRepeats]). */
    private val repeatsAndFunds = combine(repeats.observeRules(), funds.observeSnapshot(), ::Pair)

    private val roles = dropdowns.getOptionsByType("ACCOUNT_GROUP").map(StatsRoles::from)

    private val money = combine(accounts.getAccountBook(), funds.observeSnapshot(), roles, ::Triple)

    val uiState: StateFlow<BudgetUiState> = combine(budgets.observeAllCycles(), selectedIndex) { cycles, index ->
        cycles to index
    }
        .flatMapLatest { (cycles, index) ->
            if (cycles.isEmpty()) {
                flowOf(BudgetUiState(isLoading = false))
            } else {
                val position = index.coerceIn(0, cycles.lastIndex)
                val cycle = cycles[position]
                combine(
                    budgets.observeBuckets(cycle.id),
                    budgets.observeCategoryAssignments(cycle.id),
                    money
                ) { buckets, assignments, (book, snapshot, roles) ->
                    budgetHome(cycle, position, cycles.size, buckets, assignments, book, snapshot, roles, LocalDate.now())
                }
            }
        }
        .combine(repeatsAndFunds) { state, (rules, snapshot) -> state.withRepeats(rules, snapshot, LocalDate.now()) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetUiState())

    /** Add on a waiting row of the Still due sheet: the same as Add on More > Repeating. */
    fun addRepeat(ruleId: Long) {
        viewModelScope.launch { repeats.addDueOccurrence(ruleId) }
    }

    /** Skip on a waiting row of the Still due sheet. */
    fun skipRepeat(ruleId: Long) {
        viewModelScope.launch { repeats.skipDueOccurrence(ruleId) }
    }

    fun showOlderCycle() {
        val state = uiState.value
        if (state.canGoOlder) selectedIndex.value = state.cycleIndex + 1
    }

    fun showNewerCycle() {
        val state = uiState.value
        if (state.canGoNewer) selectedIndex.value = state.cycleIndex - 1
    }
}
