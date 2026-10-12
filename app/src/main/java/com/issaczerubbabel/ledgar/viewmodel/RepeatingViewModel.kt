package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.RecurringRepository
import com.issaczerubbabel.ledgar.util.RecurrenceCalculator
import com.issaczerubbabel.ledgar.util.RepeatSchedule
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.formatRupeesExact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** One Repeat rule as More > Repeating transactions lists it. */
data class RepeatRuleUi(
    val rule: RecurringRule,
    /** The description, or the Category when there's none: "Rent". */
    val title: String,
    /** "₹15,000". */
    val amount: String,
    /** "Monthly · HDFC Savings", or "Monthly · Ask me first". */
    val detail: String,
    /** "Next Sat, 1 Nov", "Due Fri, 10 Oct · 2 more due", "Paused" or "Ended". */
    val status: String,
    val isPaused: Boolean,
    val isFinished: Boolean
)

data class RepeatingUiState(
    val waiting: List<RepeatRuleUi> = emptyList(),
    val upcoming: List<RepeatRuleUi> = emptyList(),
    val rules: List<RepeatRuleUi> = emptyList(),
    val isLoaded: Boolean = false
)

/** A just-deleted rule the snackbar offers to bring back. */
data class DeletedRepeatRule(val rule: RecurringRule, val message: String)

@HiltViewModel
class RepeatingViewModel @Inject constructor(
    private val repository: RecurringRepository,
    accountRepository: AccountRepository
) : ViewModel() {

    val uiState: StateFlow<RepeatingUiState> = combine(
        repository.observeRules(),
        accountRepository.getAllAccounts()
    ) { rules, accounts ->
        val today = LocalDate.now()
        val names = accounts.associate { it.id to it.accountName }
        RepeatingUiState(
            waiting = RepeatSchedule.waiting(rules, today).map { it.toUi(names, today) },
            upcoming = RepeatSchedule.upcoming(rules, today).map { it.toUi(names, today) },
            rules = rules.sortedWith(compareBy({ it.isPaused }, { RepeatSchedule.isFinished(it) }, { it.nextDate }))
                .map { it.toUi(names, today) },
            isLoaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RepeatingUiState())

    private val _deleted = MutableSharedFlow<DeletedRepeatRule>(extraBufferCapacity = 1)
    /** One event per delete, for the Undo snackbar. */
    val deleted: SharedFlow<DeletedRepeatRule> = _deleted.asSharedFlow()

    fun add(rule: RecurringRule) {
        viewModelScope.launch { repository.addDueOccurrence(rule.id) }
    }

    fun skip(rule: RecurringRule) {
        viewModelScope.launch { repository.skipDueOccurrence(rule.id) }
    }

    fun setPaused(rule: RecurringRule, paused: Boolean) {
        viewModelScope.launch { repository.setPaused(rule.id, paused) }
    }

    fun setAskFirst(rule: RecurringRule, askFirst: Boolean) {
        viewModelScope.launch {
            val current = repository.getById(rule.id) ?: return@launch
            repository.updateRule(current.copy(autoAdd = !askFirst))
        }
    }

    /**
     * Deletes the rule at once and offers Undo. Rules never reach the Sheet, so unlike a Transaction
     * there's no Sync to race: Undo simply puts the same row back.
     */
    fun delete(rule: RecurringRule) {
        viewModelScope.launch {
            val current = repository.getById(rule.id) ?: return@launch
            repository.deleteRule(current)
            _deleted.tryEmit(DeletedRepeatRule(current, "Deleted repeat “${titleOf(current)}”"))
        }
    }

    fun undoDelete(deleted: DeletedRepeatRule) {
        viewModelScope.launch { repository.restoreRule(deleted.rule) }
    }

    private fun RecurringRule.toUi(accountNames: Map<Long, String>, today: LocalDate): RepeatRuleUi {
        val finished = RepeatSchedule.isFinished(this)
        val due = RepeatSchedule.dueDates(this, today)
        val account = if (type == TransactionType.TRANSFER) {
            listOfNotNull(fromAccountId?.let(accountNames::get), toAccountId?.let(accountNames::get))
                .takeIf { it.isNotEmpty() }?.joinToString(" → ")
        } else {
            accountId?.let(accountNames::get)
        }
        val detail = listOfNotNull(
            RecurrenceCalculator.label(frequency, interval),
            account ?: "No account",
            if (autoAdd) null else "Ask me first"
        ).joinToString(" · ")
        val status = when {
            finished -> "Ended"
            isPaused -> "Paused"
            due.isNotEmpty() && !autoAdd ->
                "Due ${due.first().format(dayFormat)}" + if (due.size > 1) " · ${due.size - 1} more due" else ""
            else -> "Next ${LocalDate.parse(nextDate).format(dayFormat)}"
        }
        return RepeatRuleUi(
            rule = this,
            title = titleOf(this),
            amount = formatRupeesExact(amount),
            detail = detail,
            status = status,
            isPaused = isPaused,
            isFinished = finished
        )
    }

    private companion object {
        val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")

        fun titleOf(rule: RecurringRule): String =
            rule.description.ifBlank { if (rule.type == TransactionType.TRANSFER) "Transfer" else rule.category }
    }
}
