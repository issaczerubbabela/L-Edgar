package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.capture.CaptureConfirmer
import com.issaczerubbabel.ledgar.capture.CaptureDraftHandoff
import com.issaczerubbabel.ledgar.capture.notify.CaptureNotifier
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.CaptureEdits
import com.issaczerubbabel.ledgar.data.repository.CaptureRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.data.repository.UndoableAction
import com.issaczerubbabel.ledgar.data.repository.UndoableActions
import com.issaczerubbabel.ledgar.util.DescriptionUse
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.formatRupeesExact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class ReviewInboxViewModel @Inject constructor(
    private val captureRepository: CaptureRepository,
    private val confirmer: CaptureConfirmer,
    private val draftHandoff: CaptureDraftHandoff,
    private val notifier: CaptureNotifier,
    private val undoableActions: UndoableActions,
    expenseRepository: ExpenseRepository,
    accountRepository: AccountRepository,
    dropdownOptionRepository: DropdownOptionRepository
) : ViewModel() {

    // Kept until a decision is final, so Undo brings a card back exactly as the user left it.
    private val edits = MutableStateFlow<Map<Long, CaptureRowEdit>>(emptyMap())

    val accounts: StateFlow<List<AccountRecord>> = accountRepository.getAllVisibleAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val rows: StateFlow<List<CaptureRowUi>> = combine(
        captureRepository.observePending(),
        captureRepository.observeRenames(),
        edits,
        undoableActions.hiddenCaptureIds,
        // Worked out live: a capture picks up its match the moment the Transaction it repeats is saved.
        combine(accounts, captureRepository.observeLedgerCandidates(), ::Pair)
    ) { pending, renames, edited, hidden, (accountList, ledger) ->
        val knownAccountIds = accountList.map { it.id }.toSet()
        val cards = pending.filterNot { it.id in hidden }.map { capture ->
            CaptureRows.build(capture, edited[capture.id], renames, knownAccountIds, match = ledger.bestMatch(capture))
        }
        // A waiting debit and credit that look like one Transfer between own Accounts show as one row.
        CaptureInbox.withTransferSuggestions(cards)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filter = MutableStateFlow(CaptureFilter.ALL)
    /** All, Needs a pick or Ready. */
    val filter: StateFlow<CaptureFilter> = _filter.asStateFlow()

    private val _expandedId = MutableStateFlow<Long?>(null)
    /** The one row shown in full; every other row is compact. */
    val expandedId: StateFlow<Long?> = _expandedId.asStateFlow()

    /** The rows under [filter], by day in the phone's zone, newest first. */
    val days: StateFlow<List<CaptureDay>> = combine(rows, _filter) { all, filter ->
        CaptureInbox.days(all, filter, ZoneId.systemDefault())
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(filter: CaptureFilter) {
        _filter.value = filter
    }

    /** Expands [captureId] and collapses any other row; on the open row, collapses it. */
    fun toggleExpanded(captureId: Long) = _expandedId.update { CaptureInbox.toggleExpanded(it, captureId) }

    /** Opens [captureId] in full, say to show what a swipe found missing. */
    fun expand(captureId: Long) {
        _expandedId.value = captureId
    }

    /** The Confirm or Dismiss the Undo snackbar is offering, if any. */
    val pending: StateFlow<UndoableAction?> = undoableActions.pending

    val expenseCategories: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType(TransactionType.EXPENSE_CATEGORY_OPTION)
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType(TransactionType.INCOME_CATEGORY_OPTION)
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Past Descriptions by type, for the suggestions under a card's title field. */
    val descriptionUses: StateFlow<Map<String, List<DescriptionUse>>> = flow {
        emit(
            listOf(TransactionType.EXPENSE, TransactionType.INCOME)
                .associateWith { runCatching { expenseRepository.descriptionUses(it) }.getOrDefault(emptyList()) }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /** The card's Expense / Income / Transfer switch. */
    fun setType(captureId: Long, type: String) = edit(captureId) { CaptureRows.switchType(it, type) }

    fun setCategory(captureId: Long, category: String) =
        edit(captureId) { CaptureRows.pickCategory(it, shownType(captureId), category) }

    /** A Transfer's other Account: the To-account of a debit, the From-account of a credit. */
    fun setOtherAccount(captureId: Long, accountId: Long) =
        edit(captureId) { CaptureRows.pickOtherAccount(it, shownType(captureId), accountId) }

    fun setAlwaysUse(captureId: Long, alwaysUse: Boolean) = edit(captureId) { it.copy(alwaysUse = alwaysUse) }

    fun setAccount(captureId: Long, accountId: Long) = edit(captureId) { it.copy(accountId = accountId) }

    /** What the user typed as the title; blank means the bank's own text. */
    fun setDescription(captureId: Long, description: String) = edit(captureId) { it.copy(description = description) }

    /**
     * Hands the card's choices to the Add Transaction form opened with Edit. Back from there leaves
     * the capture waiting and the card as it was.
     */
    fun prepareEdit(row: CaptureRowUi) {
        draftHandoff.put(
            row.capture.id,
            row.toEdits() ?: CaptureEdits(
                type = row.type,
                category = row.category.orEmpty(),
                accountId = row.accountId ?: 0L,
                description = row.title,
                alwaysUse = row.alwaysUse,
                otherAccountId = row.otherAccountId
            )
        )
    }

    /** Confirm, or on a Suggested Transfer Confirm Transfer: one Transfer that resolves both captures. */
    fun confirm(row: CaptureRowUi) = confirmRows(listOf(row))

    /** Splits a Suggested Transfer back into its two captures, which then wait on their own. */
    fun split(row: CaptureRowUi) {
        val credit = row.transferPartner ?: return
        viewModelScope.launch { captureRepository.splitTransferSuggestion(row.capture.id, credit.capture.id) }
    }

    /** The bulk sheet (Confirm day or Review all): one decision, so one Undo puts every card back. */
    fun confirmAll(rows: List<CaptureRowUi>) = confirmRows(rows)

    /** Same one: the capture is the bank's alert for [CaptureRowUi.match], so nothing new is added. */
    fun link(row: CaptureRowUi) {
        val match: ExpenseRecord = row.match ?: return
        confirmer.link(row.capture, match) { captureId -> edits.update { it - captureId } }
    }

    fun dismiss(row: CaptureRowUi) {
        val captureId = row.capture.id
        undoableActions.perform(
            UndoableAction(
                message = "Dismissed ${row.title} · ${formatRupeesExact(row.capture.amount)}",
                hiddenCaptureIds = setOf(captureId)
            ) {
                captureRepository.dismiss(captureId)
                notifier.cancel(captureId)
                edits.update { it - captureId }
            }
        )
    }

    fun undo(action: UndoableAction) = undoableActions.undo(action)

    fun commitPending() = undoableActions.commit()

    override fun onCleared() {
        commitPending()
    }

    private fun confirmRows(rows: List<CaptureRowUi>) {
        val chosen = CaptureInbox.confirmations(rows)
        if (chosen.isEmpty()) return
        val accountNames = accounts.value.associate { it.id to it.accountName }
        // A Suggested Transfer's credit is resolved with its debit, so its picks go with it.
        val partners = rows.mapNotNull { row -> row.transferPartner?.let { row.capture.id to it.capture.id } }.toMap()
        viewModelScope.launch {
            // Runs once the Undo window is over, on the app's scope: this screen may be gone by then.
            confirmer.confirm(chosen, accountNames) { captureId ->
                edits.update { it - captureId - listOfNotNull(partners[captureId]).toSet() }
            }
        }
    }

    /** The type the card shows right now; a pick made on it pins it (see [CaptureRows.pickCategory]). */
    private fun shownType(captureId: Long): String? = rows.value
        .flatMap { listOfNotNull(it, it.transferPartner) }
        .firstOrNull { it.capture.id == captureId }?.type

    private fun edit(captureId: Long, change: (CaptureRowEdit) -> CaptureRowEdit) {
        edits.update { it + (captureId to change(it[captureId] ?: CaptureRowEdit())) }
    }
}
