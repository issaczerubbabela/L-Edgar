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
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.ExpenseRepository
import com.issaczerubbabel.ledgar.data.repository.RecurringRepository
import com.issaczerubbabel.ledgar.sync.SyncScheduler
import com.issaczerubbabel.ledgar.sync.SyncStatus
import com.issaczerubbabel.ledgar.util.RecurrenceCalculator
import com.issaczerubbabel.ledgar.util.RecurrenceFrequency
import com.issaczerubbabel.ledgar.util.TransactionType
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
    bucketPreviewSource: BucketPreviewSource,
    savedStateHandle: SavedStateHandle
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

    var selectedDate by mutableStateOf(LocalDate.now())
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

    /** The rule behind the Transaction being edited, if any, so its cadence can be shown and stopped. */
    var editingRule by mutableStateOf<RecurringRule?>(null)
        private set

    private var editingRecordId: Long? = null

    /** The record being edited, as first loaded: its amount is already in its bucket's spend. */
    private var editingOriginal by mutableStateOf<ExpenseRecord?>(null)
    private var hasStartedSyncObserver = false
    val isEditMode: Boolean get() = editingRecordId != null

    init {
        val navId = savedStateHandle.get<Long>("transactionId")
        editingRecordId = navId?.takeIf { it > 0L }
        val copyTransactionId = savedStateHandle.get<Long>("copyTransactionId")?.takeIf { it > 0L }
        val copyDateMode = savedStateHandle.get<String>("copyDateMode") ?: "original"

        if (editingRecordId != null) {
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
                    TransactionType.EXPENSE, TransactionType.INCOME -> record.accountId
                    else -> null
                }
                selectedFromAccountId = record.fromAccountId
                selectedToAccountId = record.toAccountId
                description = record.description
                amount = if (record.amount % 1.0 == 0.0) record.amount.toInt().toString() else record.amount.toString()
                remarks = record.remarks
                editingRule = record.recurringRuleId?.let { recurringRepository.getById(it) }
            }
        } else if (copyTransactionId != null) {
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

                selectedType = source.type
                selectedCategory = source.category
                selectedAccountId = when (source.type) {
                    TransactionType.EXPENSE, TransactionType.INCOME -> source.accountId
                    else -> null
                }
                selectedFromAccountId = source.fromAccountId
                selectedToAccountId = source.toAccountId
                description = source.description
                amount = if (source.amount % 1.0 == 0.0) source.amount.toInt().toString() else source.amount.toString()
                remarks = source.remarks
            }
        }
    }

    fun save() {
        val parsedAmount = amount.toDoubleOrNull()
        if (parsedAmount == null || parsedAmount <= 0.0) {
            errorMessage = "Enter a valid amount"; return
        }
        if (selectedType == TransactionType.TRANSFER) {
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

            val frequency = repeatFrequency
            if (!isEditMode && frequency != null) {
                val anchorDay = if (frequency == RecurrenceFrequency.WEEKLY) {
                    selectedDate.dayOfWeek.value
                } else {
                    selectedDate.dayOfMonth
                }
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
            }

            saveSuccess = true
            if (!isEditMode) resetForm()
        }
    }

    fun setRepeat(frequency: String?, interval: Int) {
        repeatFrequency = frequency
        repeatInterval = interval
    }

    /** Deletes the rule behind the Transaction being edited; Transactions it already made stay. */
    fun stopRepeating() {
        val rule = editingRule ?: return
        viewModelScope.launch {
            recurringRepository.deleteRule(rule)
            editingRule = null
        }
    }

    fun deleteCurrent() {
        val id = editingRecordId ?: return
        viewModelScope.launch {
            val current = repository.getById(id)
            if (current == null) {
                errorMessage = "Transaction not found"
                return@launch
            }

            repository.delete(current)

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
        draftAmount = amount.toDoubleOrNull() ?: 0.0,
        original = editingOriginal
    )

    fun startSyncStatusObserver() {
        if (hasStartedSyncObserver) return
        hasStartedSyncObserver = true
        observeSyncStatus()
    }

    private fun resetForm() {
        selectedDate = LocalDate.now()
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
}
