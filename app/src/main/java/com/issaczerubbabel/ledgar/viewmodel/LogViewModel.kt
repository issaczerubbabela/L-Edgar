package com.issaczerubbabel.ledgar.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.bucket.BucketPreview
import com.issaczerubbabel.ledgar.data.bucket.BucketPreviewCalculator
import com.issaczerubbabel.ledgar.data.bucket.BucketPreviewContext
import com.issaczerubbabel.ledgar.data.bucket.BucketPreviewSource
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import com.issaczerubbabel.ledgar.data.preferences.ReminderPreferences
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.data.repository.RecurringRepository
import com.issaczerubbabel.ledgar.data.repository.UndoableActions
import com.issaczerubbabel.ledgar.ledger.Ledger
import com.issaczerubbabel.ledgar.sync.SyncScheduler
import com.issaczerubbabel.ledgar.sync.SyncStatus
import com.issaczerubbabel.ledgar.util.KeypadAction
import com.issaczerubbabel.ledgar.util.RecurrenceCalculator
import com.issaczerubbabel.ledgar.util.RepeatSchedule
import com.issaczerubbabel.ledgar.util.TransactionType
import com.issaczerubbabel.ledgar.util.amountValue
import com.issaczerubbabel.ledgar.util.applyKeypadAction
import com.issaczerubbabel.ledgar.util.isAmountExpression
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Which of the transaction's accounts an account pick applies to. */
enum class AccountTarget { Account, From, To }

@HiltViewModel
class LogViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    accountRepository: AccountRepository,
    private val dropdownOptionRepository: DropdownOptionRepository,
    private val recurringRepository: RecurringRepository,
    private val syncScheduler: SyncScheduler,
    private val reminderPreferences: ReminderPreferences,
    bucketPreviewSource: BucketPreviewSource,
    private val savedStateHandle: SavedStateHandle,
    private val undoableActions: UndoableActions
) : ViewModel() {

    val accounts: StateFlow<List<AccountRecord>> = accountRepository
        .getAllVisibleAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val expenseCategories: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType(TransactionType.EXPENSE_CATEGORY_OPTION)
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<String>> = dropdownOptionRepository
        .getOptionsByType(TransactionType.INCOME_CATEGORY_OPTION)
        .map { options -> options.map { it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bucketContext: StateFlow<BucketPreviewContext?> = bucketPreviewSource.context
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val selectedDateState = mutableStateOf(
        savedStateHandle.get<String>(KEY_SELECTED_DATE)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()
    )

    /** The date shown on the date pill. Set through [pickDate], not assigned directly, so a picked date can be pinned. */
    var selectedDate: LocalDate
        get() = selectedDateState.value
        private set(value) {
            selectedDateState.value = value
            savedStateHandle[KEY_SELECTED_DATE] = value.toString()
        }

    /** True once the user has explicitly picked a date other than today on this screen; kept across a save. */
    private var datePinned: Boolean = savedStateHandle.get<Boolean>(KEY_DATE_PINNED) ?: false
        set(value) {
            field = value
            savedStateHandle[KEY_DATE_PINNED] = value
        }

    /** The date the last successful save landed on, for the "Saved to <date>" snackbar. */
    var lastSavedDate by mutableStateOf<LocalDate?>(null)
        private set

    var selectedType by mutableStateOf(TransactionType.EXPENSE)
    var selectedCategory by mutableStateOf("")
    var selectedAccountId by mutableStateOf<Long?>(null)
    var selectedFromAccountId by mutableStateOf<Long?>(null)
    var selectedToAccountId by mutableStateOf<Long?>(null)
    var description by mutableStateOf("")
    var amount by mutableStateOf("")
    var remarks by mutableStateOf("")
    var saveSuccess by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var syncStatus by mutableStateOf(SyncStatus.Idle)

    /** Null means Never. Only meaningful for a brand-new Transaction: repeat can't be added while editing. */
    var repeatFrequency by mutableStateOf<String?>(null)
    var repeatInterval by mutableStateOf(1)

    /** The rule behind the Transaction being edited, if any, so its cadence can be shown. */
    var editingRule by mutableStateOf<RecurringRule?>(null)
        private set

    /**
     * Opened from More > Repeating transactions: [RepeatEditMode.Occurrence] is "Edit & add" on a
     * waiting occurrence, [RepeatEditMode.Rule] edits the Repeat rule itself. Both close after saving.
     */
    val repeatEditMode: RepeatEditMode =
        RepeatEditMode.from(savedStateHandle.get<String>("repeatMode"), savedStateHandle.get<Long>("repeatRuleId"))
    private var repeatRule: RecurringRule? = null
    private var repeatOccurrenceDate: LocalDate? = null

    private var editingRecordId: Long? = null

    /** The record being edited, as first loaded: its amount is already in its bucket's spend. */
    private var editingOriginal by mutableStateOf<ExpenseRecord?>(null)
    private var hasStartedSyncObserver = false
    val isEditMode: Boolean get() = editingRecordId != null

    /** Editing, or one of the Repeat modes: the screen closes after Save instead of starting another. */
    val closesAfterSave: Boolean get() = isEditMode || repeatEditMode != RepeatEditMode.None

    init {
        val navId = savedStateHandle.get<Long>("transactionId")
        editingRecordId = navId?.takeIf { it > 0L }
        val copyTransactionId = savedStateHandle.get<Long>("copyTransactionId")?.takeIf { it > 0L }
        val copyDateMode = savedStateHandle.get<String>("copyDateMode") ?: "original"

        if (repeatEditMode != RepeatEditMode.None) {
            viewModelScope.launch { loadRepeatRule(savedStateHandle.get<Long>("repeatRuleId") ?: return@launch) }
        } else if (editingRecordId != null) {
            viewModelScope.launch {
                val record = repository.getById(editingRecordId ?: return@launch)
                if (record == null) {
                    errorMessage = "Transaction not found"
                    return@launch
                }
                editingOriginal = record
                selectedDate = runCatching { LocalDate.parse(record.date) }.getOrDefault(LocalDate.now())
                selectedType = record.type
                selectedCategory = record.category
                selectedAccountId = when (record.type) {
                    TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.ADJUSTMENT -> record.accountId
                    else -> null
                }
                selectedFromAccountId = record.fromAccountId
                selectedToAccountId = record.toAccountId
                description = record.description
                amount = if (record.amount % 1.0 == 0.0) record.amount.toInt().toString() else record.amount.toString()
                remarks = record.remarks
                editingRule = record.recurringRuleId?.let { recurringRepository.getById(it) }
            }
        } else if (copyTransactionId == null) {
            // Opened from an Account's page: start an Expense, Income or Transfer from that Account.
            val prefillType = savedStateHandle.get<String>("prefillType").orEmpty()
            val prefillAccountId = savedStateHandle.get<Long>("prefillAccountId")?.takeIf { it > 0L }
            if (prefillType in listOf(TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.TRANSFER)) {
                selectedType = prefillType
            }
            if (prefillAccountId != null) {
                if (selectedType == TransactionType.TRANSFER) selectedFromAccountId = prefillAccountId
                else selectedAccountId = prefillAccountId
            }
            // From the Calendar's "Add on <day>": that date for this Transaction only. Not pinned, so the
            // next one after saving starts on today again. Not reapplied over a date restored after
            // the process was killed.
            savedStateHandle.get<String>("prefillDate")?.takeIf { it.isNotBlank() && !savedStateHandle.contains(KEY_SELECTED_DATE) }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                ?.let { selectedDate = it }
        } else {
            viewModelScope.launch {
                val source = repository.getById(copyTransactionId)
                if (source == null) {
                    errorMessage = "Transaction to copy not found"
                    return@launch
                }

                selectedDate = if (copyDateMode.equals("today", ignoreCase = true)) {
                    LocalDate.now()
                } else {
                    runCatching { LocalDate.parse(source.date) }.getOrDefault(LocalDate.now())
                }

                // Only Reconcile creates Balance adjustments, so a copy of one starts as an Expense.
                val isAdjustment = source.type == TransactionType.ADJUSTMENT
                selectedType = if (isAdjustment) TransactionType.EXPENSE else source.type
                selectedCategory = if (isAdjustment) "" else source.category
                selectedAccountId = when (source.type) {
                    TransactionType.EXPENSE, TransactionType.INCOME, TransactionType.ADJUSTMENT -> source.accountId
                    else -> null
                }
                selectedFromAccountId = source.fromAccountId
                selectedToAccountId = source.toAccountId
                description = source.description
                val copiedAmount = if (isAdjustment) kotlin.math.abs(source.amount) else source.amount
                amount = if (copiedAmount % 1.0 == 0.0) copiedAmount.toInt().toString() else copiedAmount.toString()
                remarks = source.remarks
            }
        }
    }

    /** Fills the form from the rule's template, dated on its due occurrence (or its next date, for the rule). */
    private suspend fun loadRepeatRule(ruleId: Long) {
        val rule = recurringRepository.getById(ruleId)
        if (rule == null) {
            errorMessage = "Repeat rule not found"
            return
        }
        repeatRule = rule
        val next = LocalDate.parse(rule.nextDate)
        repeatOccurrenceDate = next
        if (!savedStateHandle.contains(KEY_SELECTED_DATE)) selectedDate = next
        selectedType = rule.type
        selectedCategory = rule.category
        selectedAccountId = if (rule.type == TransactionType.TRANSFER) null else rule.accountId
        selectedFromAccountId = rule.fromAccountId
        selectedToAccountId = rule.toAccountId
        description = rule.description
        amount = if (rule.amount % 1.0 == 0.0) rule.amount.toInt().toString() else rule.amount.toString()
        remarks = rule.remarks
        repeatFrequency = rule.frequency
        repeatInterval = rule.interval
    }

    fun save() {
        // The keypad works a sum out before it saves; this only covers a caller that didn't.
        if (isAmountExpression(amount)) amount = applyKeypadAction(amount, KeypadAction.Evaluate)
        val parsedAmount = amount.toDoubleOrNull()
        // A Balance adjustment is signed: negative lowers the balance.
        val isAdjustment = selectedType == TransactionType.ADJUSTMENT
        if (parsedAmount == null || (if (isAdjustment) parsedAmount == 0.0 else parsedAmount <= 0.0)) {
            errorMessage = "Enter a valid amount"; return
        }
        if (isAdjustment) {
            if (selectedAccountId == null) { errorMessage = "Select an account"; return }
        } else if (selectedType == TransactionType.TRANSFER) {
            if (selectedFromAccountId == null) { errorMessage = "Select From Account"; return }
            if (selectedToAccountId == null) { errorMessage = "Select To Account"; return }
            if (selectedFromAccountId == selectedToAccountId) { errorMessage = "From and To accounts must differ"; return }
        } else {
            if (selectedCategory.isBlank()) { errorMessage = "Select a category"; return }
            if (selectedAccountId == null) { errorMessage = "Select an account"; return }
        }

        viewModelScope.launch {
            val baseRecord = editingRecordId?.let { repository.getById(it) }
            val accountNameById = accounts.value.associate { it.id to it.accountName }

            var record = ExpenseRecord(
                id = baseRecord?.id ?: 0,
                date = selectedDate.toString(),
                type = selectedType,
                category = if (selectedType == TransactionType.TRANSFER) TransactionType.TRANSFER else selectedCategory,
                description = description,
                amount = parsedAmount,
                accountId = if (selectedType == TransactionType.TRANSFER) null else selectedAccountId,
                remarks = remarks,
                fromAccountId = when (selectedType) {
                    TransactionType.EXPENSE -> selectedAccountId
                    TransactionType.TRANSFER -> selectedFromAccountId
                    else -> null
                },
                toAccountId = when (selectedType) {
                    TransactionType.INCOME -> selectedAccountId
                    TransactionType.TRANSFER -> selectedToAccountId
                    else -> null
                },
                toAccountName = when (selectedType) {
                    TransactionType.TRANSFER -> selectedToAccountId?.let { accountNameById[it] }
                    else -> baseRecord?.toAccountName
                },
                isBookmarked = baseRecord?.isBookmarked ?: false,
                isSynced = false,
                remoteTimestamp = baseRecord?.remoteTimestamp,
                syncAction = if (isEditMode) "UPDATE" else "INSERT",
                recurringRuleId = baseRecord?.recurringRuleId
            )

            val rule = repeatRule
            if (repeatEditMode == RepeatEditMode.Rule && rule != null) {
                val frequency = repeatFrequency ?: rule.frequency
                recurringRepository.updateRule(
                    rule.copy(
                        type = record.type,
                        category = record.category,
                        description = record.description,
                        amount = record.amount,
                        accountId = record.accountId,
                        remarks = record.remarks,
                        fromAccountId = record.fromAccountId,
                        toAccountId = record.toAccountId,
                        frequency = frequency,
                        interval = repeatInterval,
                        anchorDay = RepeatSchedule.anchorDayFor(frequency, selectedDate),
                        nextDate = selectedDate.toString()
                    )
                )
                saveSuccess = true
                return@launch
            }
            if (repeatEditMode == RepeatEditMode.Occurrence && rule != null) {
                recurringRepository.saveEditedOccurrence(rule.id, repeatOccurrenceDate ?: selectedDate, record)
                reminderPreferences.markLoggedToday(LocalDate.now().toEpochDay())
                saveSuccess = true
                return@launch
            }

            val frequency = repeatFrequency
            if (!isEditMode && frequency != null) {
                val anchorDay = RepeatSchedule.anchorDayFor(frequency, selectedDate)
                val ruleId = recurringRepository.createRule(
                    RecurringRule(
                        type = record.type,
                        category = record.category,
                        description = record.description,
                        amount = record.amount,
                        accountId = record.accountId,
                        remarks = record.remarks,
                        fromAccountId = record.fromAccountId,
                        toAccountId = record.toAccountId,
                        frequency = frequency,
                        interval = repeatInterval,
                        anchorDay = anchorDay,
                        startDate = record.date,
                        nextDate = RecurrenceCalculator.nextAfter(selectedDate, frequency, repeatInterval, anchorDay).toString(),
                        createdAt = LocalDate.now().toString()
                    )
                )
                record = record.copy(recurringRuleId = ruleId)
            }

            if (isEditMode) {
                repository.update(record)
            } else {
                repository.save(record)
                // A backdated entry logged tonight still counts as logged today.
                reminderPreferences.markLoggedToday(LocalDate.now().toEpochDay())
            }

            if (!isEditMode) {
                lastSavedDate = selectedDate
                resetForm()
            }
            saveSuccess = true
        }
    }

    fun setRepeat(frequency: String?, interval: Int) {
        repeatFrequency = frequency
        repeatInterval = interval
    }

    /** Called when the user picks a date from the date pill's picker. */
    fun pickDate(date: LocalDate) {
        selectedDate = date
        datePinned = date != LocalDate.now()
    }

    fun deleteCurrent() {
        val id = editingRecordId ?: return
        viewModelScope.launch {
            val current = repository.getById(id)
            if (current == null) {
                errorMessage = "Transaction not found"
                return@launch
            }

            // The screen closes; the one underneath offers Undo.
            undoableActions.delete(current, Ledger.deletedMessage(current, accounts.value))

            saveSuccess = true
        }
    }

    fun resetSaveSuccess() { saveSuccess = false }
    fun clearError() { errorMessage = null }

    fun retrySync() {
        viewModelScope.launch { syncScheduler.retrySync() }
    }

    /**
     * Creates a category on the type currently selected without leaving the screen, or selects
     * the existing one. If the type changes while the insert is in flight the new category
     * belongs to the old type's list, so it is not selected under the new one.
     */
    fun addCategoryInline(name: String) {
        val typeAtRequest = selectedType
        viewModelScope.launch {
            val category = dropdownOptionRepository
                .addOptionIfAbsent(TransactionType.categoryOptionType(typeAtRequest), name)
                ?: return@launch
            if (selectedType == typeAtRequest) selectedCategory = category
        }
    }

    fun setAccount(target: AccountTarget, accountId: Long) {
        when (target) {
            AccountTarget.Account -> selectedAccountId = accountId
            AccountTarget.From -> selectedFromAccountId = accountId
            AccountTarget.To -> selectedToAccountId = accountId
        }
    }

    /** The bucket this transaction would land in, counting the amount typed so far. */
    fun bucketPreview(context: BucketPreviewContext?): BucketPreview? = BucketPreviewCalculator.preview(
        context = context,
        type = selectedType,
        category = selectedCategory,
        date = selectedDate,
        draftAmount = amountValue(amount, allowNegative = selectedType == TransactionType.ADJUSTMENT) ?: 0.0,
        original = editingOriginal
    )

    fun startSyncStatusObserver() {
        if (hasStartedSyncObserver) return
        hasStartedSyncObserver = true
        observeSyncStatus()
    }

    private fun resetForm() {
        if (!datePinned) selectedDate = LocalDate.now()
        selectedType = TransactionType.EXPENSE
        selectedCategory = ""
        selectedAccountId = null
        selectedFromAccountId = null
        selectedToAccountId = null
        description = ""
        amount = ""
        remarks = ""
        repeatFrequency = null
        repeatInterval = 1
    }

    private fun observeSyncStatus() {
        viewModelScope.launch {
            syncScheduler.transactionSyncStatus.collect { syncStatus = it }
        }
    }

    private companion object {
        const val KEY_SELECTED_DATE = "logSelectedDate"
        const val KEY_DATE_PINNED = "logDatePinned"
    }
}

/** How Add Transaction was opened from More > Repeating transactions, if it was. */
enum class RepeatEditMode {
    None, Occurrence, Rule;

    companion object {
        fun from(mode: String?, ruleId: Long?): RepeatEditMode = when {
            ruleId == null || ruleId <= 0L -> None
            mode == "rule" -> Rule
            mode == "occurrence" -> Occurrence
            else -> None
        }
    }
}
