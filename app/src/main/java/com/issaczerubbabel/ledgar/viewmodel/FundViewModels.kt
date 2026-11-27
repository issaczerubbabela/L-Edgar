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
import com.issaczerubbabel.ledgar.data.repository.SaveFundResult
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
            val result = funds.saveFund(
                fund = FundEditor.toFund(current, existing, LocalDate.now()),
                holdings = FundEditor.toHoldings(current, current.fundId),
                removedHoldingIds = FundEditor.removedHoldingIds(current, snapshot)
            )
            when (result) {
                is SaveFundResult.Saved -> {
                    draft.value = current.copy(
                        fundId = result.fundId,
                        holdings = current.holdings.map { it.copy(holdingId = result.holdingIds[it.accountId] ?: it.holdingId) }
                    )
                    startedFor = result.fundId
                    status.value = Status()
                    onSaved(result.fundId)
                }
                is SaveFundResult.Refused -> {
                    val name = uiState.value.accountNames[result.accountId].orEmpty()
                    val fundNames = snapshot.funds.associate { it.id to it.name }
                    status.value = Status(error = refusalText(result.reason, name, fundNames))
                }
                SaveFundResult.FundNotFound -> status.value = Status(error = "This Fund no longer exists")
            }
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

/** Why the holding of [accountName] was refused, as the Fund sheet says it. */
private fun refusalText(reason: HoldingResult, accountName: String, fundNames: Map<Long, String>): String = when (reason) {
    is HoldingResult.AccountHeldWhole -> "$accountName is already ${fundNames[reason.fundId] ?: "another Fund"}'s whole account"
    HoldingResult.AccountHasOtherHoldings -> "Other Funds hold part of $accountName, so it can't be held whole"
    HoldingResult.AlreadyInFund -> "This Fund already holds $accountName"
    HoldingResult.FundNotFound -> "This Fund no longer exists"
    is HoldingResult.Saved -> ""
}
