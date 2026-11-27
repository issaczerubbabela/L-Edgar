package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.bucket.SpendableTopUp
import com.issaczerubbabel.ledgar.data.preferences.SpendableTopUpPreferences
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.FundRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
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
    accounts: AccountRepository,
    funds: FundRepository,
    dropdowns: DropdownOptionRepository,
    private val topUps: SpendableTopUpPreferences
) : ViewModel() {

    private val selectedIndex = MutableStateFlow(0)

    private val roles = dropdowns.getOptionsByType("ACCOUNT_GROUP").map(StatsRoles::from)

    private val money = combine(accounts.getAccountBook(), funds.observeSnapshot(), roles, ::Triple)

    private val topUpAnswers = combine(topUps.fundsSince, topUps.answeredCycleIds, ::Pair)

    init {
        // Keeps the day this phone first had a whole-Account Fund, so later Fund edits can't move it (#156).
        viewModelScope.launch {
            val since = funds.observeSnapshot().mapNotNull { SpendableTopUp.fundsSince(it, LocalDate.now()) }.first()
            topUps.recordFundsSince(since)
        }
    }

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
                    money,
                    topUpAnswers
                ) { buckets, assignments, (book, snapshot, roles), (storedSince, answered) ->
                    val today = LocalDate.now()
                    // Until the first sighting is saved, the same date worked out from the Funds.
                    val fundsSince = storedSince ?: SpendableTopUp.fundsSince(snapshot, today)
                    budgetHome(cycle, position, cycles.size, buckets, assignments, book, snapshot, roles, today, fundsSince, answered)
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BudgetUiState())

    fun showOlderCycle() {
        val state = uiState.value
        if (state.canGoOlder) selectedIndex.value = state.cycleIndex + 1
    }

    fun showNewerCycle() {
        val state = uiState.value
        if (state.canGoNewer) selectedIndex.value = state.cycleIndex - 1
    }

    /** Raise on the "raise spendable" card: saves [amount] as the running cycle's spendable, like Plan buckets. */
    fun raiseSpendable(amount: Double) {
        val cycle = uiState.value.summary?.cycle?.takeIf { it.closedAt == null } ?: return
        viewModelScope.launch {
            topUps.markAnswered(cycle.id)
            budgets.updateCycle(cycle.copy(spendableAmount = amount.coerceAtLeast(0.0)))
        }
    }

    /** Keep on the "raise spendable" card: hides it for this cycle only. */
    fun keepSpendable() {
        val cycle = uiState.value.summary?.cycle ?: return
        viewModelScope.launch { topUps.markAnswered(cycle.id) }
    }
}
