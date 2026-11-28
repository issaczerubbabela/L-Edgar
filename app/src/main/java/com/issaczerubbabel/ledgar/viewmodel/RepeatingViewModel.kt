package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.RecurringRepository
import com.issaczerubbabel.ledgar.util.RecurrenceCalculator
import com.issaczerubbabel.ledgar.util.RecurrenceFrequency
import com.issaczerubbabel.ledgar.util.RepeatSchedule
import com.issaczerubbabel.ledgar.util.RepeatSuggestion
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.formatRupeesExact
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
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

/** A repeat found in the Ledger, as the Suggested for you card shows it. */
data class RepeatSuggestionUi(
    val suggestion: RepeatSuggestion,
    /** The description, "Transfer to RD" for an unnamed Transfer, or the Category. */
    val title: String,
    /** "₹649", or "about ₹649" when the amounts varied. */
    val amount: String,
    /** "Monthly, around the 5th", "Weekly, on Fridays". */
    val cadence: String,
    /** "Seen 6 times, last on 5 Oct". */
    val seen: String,
    /** "HDFC Card", or "Savings → RD" for a Transfer. */
    val account: String,
    /** "Next due Fri, 5 Dec": the rule's first date, which may already have passed. */
    val startsOn: String,
    /** The card as one TalkBack sentence. */
    val spoken: String
)

data class RepeatingUiState(
    val waiting: List<RepeatRuleUi> = emptyList(),
    val upcoming: List<RepeatRuleUi> = emptyList(),
    val rules: List<RepeatRuleUi> = emptyList(),
    val suggestions: List<RepeatSuggestionUi> = emptyList(),
    /** False until the first look through the Ledger finishes, so the empty state doesn't flash first. */
    val suggestionsChecked: Boolean = false,
    val isLoaded: Boolean = false
)

/** A just-deleted rule the snackbar offers to bring back. */
data class DeletedRepeatRule(val rule: RecurringRule, val message: String)

/** What a Suggested for you card did, for its Undo snackbar. */
sealed interface SuggestionEvent {
    val message: String

    /** Make it repeat saved rule [ruleId]; Undo deletes it. */
    data class Accepted(val ruleId: Long, override val message: String) : SuggestionEvent

    /** Not a repeat hid [key]; Undo shows it again. */
    data class Dismissed(val key: String, override val message: String) : SuggestionEvent
}

@HiltViewModel
class RepeatingViewModel @Inject constructor(
    private val repository: RecurringRepository,
    accountRepository: AccountRepository
) : ViewModel() {

    /** Null until the first look through the Ledger finishes: the screen never waits for it. */
    private val suggestions: Flow<List<RepeatSuggestion>?> =
        repository.observeSuggestions(LocalDate.now()).map<List<RepeatSuggestion>, List<RepeatSuggestion>?> { it }
            .onStart { emit(null) }

    val uiState: StateFlow<RepeatingUiState> = combine(
        repository.observeRules(),
        accountRepository.getAllAccounts(),
        suggestions
    ) { rules, accounts, found ->
        val today = LocalDate.now()
        val names = accounts.associate { it.id to it.accountName }
        RepeatingUiState(
            waiting = RepeatSchedule.waiting(rules, today).map { it.toUi(names, today) },
            upcoming = RepeatSchedule.upcoming(rules, today).map { it.toUi(names, today) },
            rules = rules.sortedWith(compareBy({ it.isPaused }, { RepeatSchedule.isFinished(it) }, { it.nextDate }))
                .map { it.toUi(names, today) },
            suggestions = found.orEmpty().map { it.toUi(names) },
            suggestionsChecked = found != null,
            isLoaded = true
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), RepeatingUiState())

    private val _deleted = MutableSharedFlow<DeletedRepeatRule>(extraBufferCapacity = 1)
    /** One event per delete, for the Undo snackbar. */
    val deleted: SharedFlow<DeletedRepeatRule> = _deleted.asSharedFlow()

    private val _suggestionEvents = MutableSharedFlow<SuggestionEvent>(extraBufferCapacity = 1)
    /** One event per Make it repeat or Not a repeat, for the Undo snackbar. */
    val suggestionEvents: SharedFlow<SuggestionEvent> = _suggestionEvents.asSharedFlow()

    /** Make it repeat > Save: creates the rule, Ask me first unless [askFirst] is off. */
    fun accept(row: RepeatSuggestionUi, askFirst: Boolean) {
        viewModelScope.launch {
            val s = row.suggestion
            val ruleId = repository.acceptSuggestion(s, askFirst)
            val cadence = RecurrenceCalculator.label(s.frequency, s.interval).lowercase()
            val message = "${row.title} repeats $cadence from ${s.nextDate.format(shortDayFormat)}"
            _suggestionEvents.tryEmit(SuggestionEvent.Accepted(ruleId, message))
        }
    }

    /** Not a repeat: hides the card for good, with Undo. */
    fun dismiss(row: RepeatSuggestionUi) {
        viewModelScope.launch {
            val key = row.suggestion.key
            repository.dismissSuggestion(key)
            _suggestionEvents.tryEmit(SuggestionEvent.Dismissed(key, "Won’t suggest “${row.title}” again"))
        }
    }

    fun undo(event: SuggestionEvent) {
        viewModelScope.launch {
            when (event) {
                is SuggestionEvent.Accepted -> repository.getById(event.ruleId)?.let { repository.deleteRule(it) }
                is SuggestionEvent.Dismissed -> repository.undismissSuggestion(event.key)
            }
        }
    }

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

    private fun RepeatSuggestion.toUi(accountNames: Map<Long, String>): RepeatSuggestionUi {
        val toName = toAccountId?.let(accountNames::get)
        val title = description.ifBlank {
            if (type == TransactionType.TRANSFER) toName?.let { "Transfer to $it" } ?: "Transfer" else category
        }
        val account = if (type == TransactionType.TRANSFER) {
            listOfNotNull(fromAccountId?.let(accountNames::get), toName).joinToString(" → ")
        } else {
            accountId?.let(accountNames::get).orEmpty()
        }.ifBlank { "No account" }
        val exact = formatRupeesExact(amount)
        val cadence = cadenceLabel(this)
        val spokenAmount = (if (isApproximate) "about " else "") + exact.removePrefix("₹") + " rupees"
        return RepeatSuggestionUi(
            suggestion = this,
            title = title,
            amount = if (isApproximate) "about $exact" else exact,
            cadence = cadence,
            seen = "Seen $occurrences times, last on ${lastDate.format(shortDayFormat)}",
            account = account,
            startsOn = "Next due ${nextDate.format(dayFormat)}",
            spoken = "Suggested repeat: $title, $spokenAmount, ${cadence.lowercase()}, seen $occurrences times, $account"
        )
    }

    private companion object {
        val dayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, d MMM")
        val shortDayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")

        /** "Monthly, around the 5th", "Every 3 months, around the 10th", "Weekly, on Fridays". */
        fun cadenceLabel(s: RepeatSuggestion): String {
            val base = RecurrenceCalculator.label(s.frequency, s.interval)
            return if (s.frequency == RecurrenceFrequency.WEEKLY) {
                val day = DayOfWeek.of(s.anchorDay.coerceIn(1, 7)).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                "$base, on ${day}s"
            } else {
                "$base, around the ${ordinal(s.anchorDay)}"
            }
        }

        fun ordinal(day: Int): String {
            val suffix = if (day % 100 in 11..13) "th" else when (day % 10) {
                1 -> "st"
                2 -> "nd"
                3 -> "rd"
                else -> "th"
            }
            return "$day$suffix"
        }

        fun titleOf(rule: RecurringRule): String =
            rule.description.ifBlank { if (rule.type == TransactionType.TRANSFER) "Transfer" else rule.category }
    }
}
