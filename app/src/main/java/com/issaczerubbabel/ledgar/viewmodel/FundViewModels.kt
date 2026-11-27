package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.BucketBudgetRepository
import com.issaczerubbabel.ledgar.data.repository.FundRepository
import com.issaczerubbabel.ledgar.data.repository.HoldingResult
import com.issaczerubbabel.ledgar.data.repository.UndoableActions
import com.issaczerubbabel.ledgar.fund.FundMath
import com.issaczerubbabel.ledgar.fund.FundsSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** The Funds list, from More. */
@HiltViewModel
class FundsViewModel @Inject constructor(
    funds: FundRepository,
    accounts: AccountRepository
) : ViewModel() {
    val uiState: StateFlow<FundsUiState> = combine(funds.observeSnapshot(), accounts.getAccountBook(), ::fundsList)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FundsUiState())
}

/** A Fund's page: its holdings, this cycle's Set aside and its history. */
@HiltViewModel
class FundDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    funds: FundRepository,
    accounts: AccountRepository,
    budgets: BucketBudgetRepository,
    undoableActions: UndoableActions
) : ViewModel() {

    private val fundId: Long = checkNotNull(savedStateHandle["fundId"])

    val uiState: StateFlow<FundDetailUiState> = combine(
        funds.observeSnapshot(),
        accounts.getAccountBook(),
        budgets.observeRunningCycle(),
        undoableActions.hiddenTransactionIds
    ) { snapshot, book, cycle, hidden ->
        fundDetail(fundId, snapshot, book.without(hidden), cycle, LocalDate.now())
            ?: FundDetailUiState(fundId = fundId, isLoaded = true, notFound = true)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FundDetailUiState(fundId = fundId))
}

/** What the New / Edit Fund sheet shows besides the draft. */
data class FundEditorUiState(
    val draft: FundDraft? = null,
    val accounts: List<AccountRecord> = emptyList(),
    val snapshot: FundsSnapshot = FundsSnapshot.EMPTY,
    val balances: Map<Long, Double> = emptyMap(),
    /** The Fund's balance now: what Archive or Delete releases to Free. */
    val fundBalance: Double = 0.0,
    val isArchived: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null
) {
    val accountNames: Map<Long, String> get() = accounts.associate { it.id to it.accountName }
    fun choices(kind: HoldingKind): List<AccountRecord> =
        draft?.let { FundEditor.accountChoices(kind, it, snapshot, accounts) }.orEmpty()
}

/**
 * The New / Edit Fund sheet. Open it with [start]; the draft lives here so it survives rotation.
 * Any screen can host the sheet (Fund detail, the Funds list, the Budget tab's "+ New fund").
 */
@HiltViewModel
class FundEditorViewModel @Inject constructor(
    private val funds: FundRepository,
    accounts: AccountRepository
) : ViewModel() {

    private val draft = MutableStateFlow<FundDraft?>(null)
    private val status = MutableStateFlow(Status())

    private data class Status(val isSaving: Boolean = false, val error: String? = null)

    val uiState: StateFlow<FundEditorUiState> = combine(
        draft,
        accounts.getAllAccounts(),
        funds.observeSnapshot(),
        accounts.getAccountBook(),
        status
    ) { draft, accountList, snapshot, book, status ->
        val balances = AccountMath.balances(book.accounts, book.transactions)
        val fundId = draft?.fundId ?: 0L
        FundEditorUiState(
            draft = draft,
            accounts = accountList,
            snapshot = snapshot,
            balances = balances,
            fundBalance = if (fundId == 0L) 0.0 else FundMath.fundBalance(fundId, snapshot.activeHoldings, balances),
            isArchived = snapshot.funds.firstOrNull { it.id == fundId }?.isArchived == true,
            isSaving = status.isSaving,
            error = status.error
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FundEditorUiState())

    private var startedFor: Long? = null

    /** Loads the draft for [fundId], or a new Fund when it's null. Calling it again for the same Fund keeps the edits. */
    fun start(fundId: Long?) {
        val key = fundId ?: 0L
        if (startedFor == key && draft.value != null) return
        startedFor = key
        viewModelScope.launch {
            val snapshot = funds.getSnapshot()
            val fund = fundId?.let { id -> snapshot.funds.firstOrNull { it.id == id } }
            draft.value = if (fund != null) FundEditor.draftOf(fund, snapshot) else FundEditor.newDraft(snapshot)
        }
    }

    /** Forgets the draft when the sheet closes, so the next open starts from what's saved. */
    fun close() {
        startedFor = null
        draft.value = null
        status.value = Status()
    }

    fun edit(transform: (FundDraft) -> FundDraft) {
        draft.update { it?.let(transform) }
        status.update { it.copy(error = null) }
    }

    fun addHolding(kind: HoldingKind, accountId: Long) {
        val balance = uiState.value.balances[accountId] ?: 0.0
        edit { FundEditor.addHolding(it, kind, accountId, balance, LocalDate.now()) }
    }

    fun removeHolding(accountId: Long) = edit { d -> d.copy(holdings = d.holdings.filterNot { it.accountId == accountId }) }

    /** Saves the Fund and its holdings, then calls [onSaved] with its id. A holding the repository refuses keeps the sheet open. */
    fun save(onSaved: (Long) -> Unit) {
        val current = draft.value ?: return
        if (!current.canSave || status.value.isSaving) return
        status.value = Status(isSaving = true)
        viewModelScope.launch {
            val snapshot = funds.getSnapshot()
            val existing = snapshot.funds.firstOrNull { it.id == current.fundId }
            val today = LocalDate.now()
            val fundId = if (existing == null) {
                funds.createFund(FundEditor.toFund(current, existing = null, today = today))
            } else {
                funds.updateFund(FundEditor.toFund(current, existing, today))
                existing.id
            }
            FundEditor.removedHoldingIds(current, snapshot).forEach { funds.removeHolding(it) }

            val names = uiState.value.accountNames
            val fundNames = snapshot.funds.associate { it.id to it.name }
            var saved = current.copy(fundId = fundId)
            var error: String? = null
            FundEditor.toHoldings(current, fundId).forEach { holding ->
                val name = names[holding.accountId].orEmpty()
                when (val result = funds.saveHolding(holding)) {
                    is HoldingResult.Saved -> saved = saved.copy(holdings = saved.holdings.map {
                        if (it.accountId == holding.accountId) it.copy(holdingId = result.holdingId) else it
                    })
                    is HoldingResult.AccountHeldWhole -> error = "$name is already ${fundNames[result.fundId] ?: "another Fund"}'s whole account"
                    HoldingResult.AccountHasOtherHoldings -> error = "Other Funds hold part of $name, so it can't be held whole"
                    HoldingResult.AlreadyInFund -> error = "This Fund already holds $name"
                    HoldingResult.FundNotFound -> error = "This Fund no longer exists"
                }
            }
            // A second Save after an error updates the Fund just created instead of making another.
            draft.value = saved
            startedFor = fundId
            status.value = Status(error = error)
            if (error == null) onSaved(fundId)
        }
    }

    /** Archives the Fund: it keeps its history and its money goes back to Free. */
    fun archive(onDone: () -> Unit) {
        val fundId = draft.value?.fundId?.takeIf { it != 0L } ?: return
        viewModelScope.launch {
            funds.archiveFund(fundId, LocalDate.now().toString())
            onDone()
        }
    }

    fun unarchive(onDone: () -> Unit) {
        val fundId = draft.value?.fundId?.takeIf { it != 0L } ?: return
        viewModelScope.launch {
            funds.unarchiveFund(fundId)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val fundId = draft.value?.fundId?.takeIf { it != 0L } ?: return
        viewModelScope.launch {
            funds.deleteFund(fundId)
            onDone()
        }
    }
}
