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
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.CaptureEdits
import com.issaczerubbabel.ledgar.data.repository.CaptureRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.util.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One pending capture with whatever the user has already changed on it. */
data class CaptureRowUi(
    val capture: CapturedTransaction,
    val type: String,
    val category: String?,
    val accountId: Long?,
    val band: ConfidenceBand,
    val why: String?,
    val categoryPickedByUser: Boolean = false
) {
    val isIncome: Boolean get() = type == TransactionType.INCOME
    val canConfirm: Boolean get() = !category.isNullOrBlank() && accountId != null

    /** Safe to confirm in bulk: a High-confidence guess whose Category and Account are both known. */
    val isBulkCandidate: Boolean get() = band == ConfidenceBand.HIGH && canConfirm

    val title: String get() = capture.displayTitle()
}

private data class RowEdit(val category: String? = null, val accountId: Long? = null)

@HiltViewModel
class ReviewInboxViewModel @Inject constructor(
    private val captureRepository: CaptureRepository,
    private val notifier: CaptureNotifier,
    accountRepository: AccountRepository,
    dropdownOptionRepository: DropdownOptionRepository
) : ViewModel() {

    private val edits = MutableStateFlow<Map<Long, RowEdit>>(emptyMap())
    private val gson = Gson()
    private val traceType = object : TypeToken<Map<String, String?>>() {}.type

    val rows: StateFlow<List<CaptureRowUi>> = combine(captureRepository.observePending(), edits) { pending, edited ->
        pending.map { capture ->
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
                categoryPickedByUser = edit?.category != null
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    fun setCategory(captureId: Long, category: String) {
        edits.update { it + (captureId to (it[captureId] ?: RowEdit()).copy(category = category)) }
    }

    fun setAccount(captureId: Long, accountId: Long) {
        edits.update { it + (captureId to (it[captureId] ?: RowEdit()).copy(accountId = accountId)) }
    }

    fun confirm(row: CaptureRowUi) {
        viewModelScope.launch { confirmNow(row) }
    }

    fun confirmAll(rows: List<CaptureRowUi>) {
        viewModelScope.launch { rows.forEach { confirmNow(it) } }
    }

    fun dismiss(captureId: Long) {
        viewModelScope.launch {
            captureRepository.dismiss(captureId)
            notifier.cancel(captureId)
        }
    }

    private suspend fun confirmNow(row: CaptureRowUi) {
        val category = row.category ?: return
        val accountId = row.accountId ?: return
        captureRepository.confirm(
            row.capture.id,
            CaptureEdits(type = row.type, category = category, accountId = accountId, description = row.title)
        )
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
