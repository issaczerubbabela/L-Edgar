package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.issaczerubbabel.ledgar.capture.categorize.ConfidenceBand
import com.issaczerubbabel.ledgar.capture.notify.CaptureNotifier
import com.issaczerubbabel.ledgar.capture.notify.displayTitle
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.preferences.ReminderPreferences
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import javax.inject.Inject

/** One pending capture with whatever the user has already changed on it. */
data class CaptureRowUi(
    val capture: CapturedTransaction,
    val type: String,
    val category: String?,
    val accountId: Long?,
    val band: ConfidenceBand,
    val why: String?,
    /** What Confirm saves as the Description: the user's edit, else the merchant's rename, else the bank's text. */
    val title: String,
    /** The title as the bank wrote it, before any rename. */
    val bankTitle: String,
    /** What the user has typed in the title field, if they've edited it. */
    val descriptionDraft: String? = null,
    val categoryPickedByUser: Boolean = false,
    val alwaysUse: Boolean = false
) {
    val isIncome: Boolean get() = type == TransactionType.INCOME
    val canConfirm: Boolean get() = !category.isNullOrBlank() && accountId != null

    /** Only a capture with a merchant can have a rule saved for it. */
    val canOfferAlwaysUse: Boolean get() = !capture.merchantNorm.isNullOrBlank() && !category.isNullOrBlank()

    /** Safe to confirm in bulk: a High-confidence guess whose Category and Account are both known. */
    val isBulkCandidate: Boolean get() = band == ConfidenceBand.HIGH && canConfirm
}

private data class RowEdit(
    val category: String? = null,
    val accountId: Long? = null,
    val alwaysUse: Boolean = false,
    val description: String? = null
)

@HiltViewModel
class ReviewInboxViewModel @Inject constructor(
    private val captureRepository: CaptureRepository,
    private val notifier: CaptureNotifier,
    private val reminderPreferences: ReminderPreferences,
    private val undoableActions: UndoableActions,
    expenseRepository: ExpenseRepository,
    accountRepository: AccountRepository,
    dropdownOptionRepository: DropdownOptionRepository
) : ViewModel() {

    // Kept until a decision is final, so Undo brings a card back exactly as the user left it.
    private val edits = MutableStateFlow<Map<Long, RowEdit>>(emptyMap())
    private val gson = Gson()
    private val traceType = object : TypeToken<Map<String, String?>>() {}.type

    val rows: StateFlow<List<CaptureRowUi>> = combine(
        captureRepository.observePending(),
        captureRepository.observeRenames(),
        edits,
        undoableActions.hiddenCaptureIds
    ) { pending, renames, edited, hidden ->
        pending.filterNot { it.id in hidden }.map { capture ->
            val edit = edited[capture.id]
            val category = edit?.category ?: capture.suggestedCategory
            val accountId = edit?.accountId ?: capture.accountId
            CaptureRowUi(
                capture = capture,
                type = capture.suggestedType ?: TransactionType.EXPENSE,
                category = category,
                accountId = accountId,
                // Picking a Category yourself is a decision, so it no longer needs a second look.
                band = if (edit?.category != null) ConfidenceBand.HIGH else ConfidenceBand.of(capture.confidence, capture.suggestedCategory != null),
                why = parseWhy(capture.traceJson),
                // A cleared field means the bank's own text, which also forgets an earlier rename.
                title = edit?.description?.trim()?.ifEmpty { capture.displayTitle() }
                    ?: capture.displayTitle(capture.merchantNorm?.let { renames[it] }),
                bankTitle = capture.displayTitle(),
                descriptionDraft = edit?.description,
                categoryPickedByUser = edit?.category != null,
                alwaysUse = edit?.alwaysUse ?: false
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** The Confirm or Dismiss the Undo snackbar is offering, if any. */
    val pending: StateFlow<UndoableAction?> = undoableActions.pending

    val accounts: StateFlow<List<AccountRecord>> = accountRepository.getAllVisibleAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun setCategory(captureId: Long, category: String) = edit(captureId) { it.copy(category = category) }

    fun setAlwaysUse(captureId: Long, alwaysUse: Boolean) = edit(captureId) { it.copy(alwaysUse = alwaysUse) }

    fun setAccount(captureId: Long, accountId: Long) = edit(captureId) { it.copy(accountId = accountId) }

    /** What the user typed as the title; blank means the bank's own text. */
    fun setDescription(captureId: Long, description: String) = edit(captureId) { it.copy(description = description) }

    fun confirm(row: CaptureRowUi) {
        if (!row.canConfirm) return
        undoableActions.perform(
            UndoableAction(
                message = "Confirmed ${row.title} · ${formatRupeesExact(row.capture.amount)} as ${row.category}",
                hiddenCaptureIds = setOf(row.capture.id)
            ) { confirmNow(row) }
        )
    }

    /** Review all: one decision, so one Undo puts every card back. */
    fun confirmAll(rows: List<CaptureRowUi>) {
        val chosen = rows.filter { it.canConfirm }
        if (chosen.size <= 1) return chosen.forEach { confirm(it) }
        undoableActions.perform(
            UndoableAction(
                message = "Confirmed ${chosen.size} · ${formatRupeesExact(chosen.sumOf { it.capture.amount })}",
                hiddenCaptureIds = chosen.map { it.capture.id }.toSet()
            ) { chosen.forEach { confirmNow(it) } }
        )
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

    private fun edit(captureId: Long, change: (RowEdit) -> RowEdit) {
        edits.update { it + (captureId to change(it[captureId] ?: RowEdit())) }
    }

    /** Runs once the Undo window is over, on the app's scope: this screen may be gone by then. */
    private suspend fun confirmNow(row: CaptureRowUi) {
        val category = row.category ?: return
        val accountId = row.accountId ?: return
        val expenseId = captureRepository.confirm(
            row.capture.id,
            CaptureEdits(
                type = row.type, category = category, accountId = accountId,
                description = row.title, alwaysUse = row.alwaysUse
            )
        )
        // A confirmed capture is something logged today, so the evening reminder stays quiet.
        if (expenseId != null) reminderPreferences.markLoggedToday(LocalDate.now().toEpochDay())
        notifier.cancel(row.capture.id)
        edits.update { it - row.capture.id }
    }

    private fun parseWhy(json: String?): String? {
        if (json.isNullOrBlank()) return null
        val trace = runCatching { gson.fromJson<Map<String, String?>>(json, traceType) }.getOrNull() ?: return null
        val rule = trace["rule"] ?: return null
        return "$rule · ${trace["why"].orEmpty()}"
    }
}
