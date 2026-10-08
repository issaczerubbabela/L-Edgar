package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.DropdownRole
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.PermanentDeleteStrategy
import com.issaczerubbabel.ledgar.util.amountToInput
import com.issaczerubbabel.ledgar.util.parseAmountInput
import com.issaczerubbabel.ledgar.util.parseFlexibleDate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Another Account that Delete… can move Transactions to. */
data class AccountChoice(val id: Long, val label: String)

data class AccountFormUiState(
    /** Null while adding. */
    val accountId: Long? = null,
    val group: String = "",
    val name: String = "",
    /** For a Liability group, the Amount owed (positive). Read-only once the Account exists. */
    val amountInput: String = "",
    /** The As-of date: the amount is the balance at the end of this day. */
    val asOfDate: LocalDate = LocalDate.now(),
    val description: String = "",
    val includeInTotals: Boolean = true,
    val isHidden: Boolean = false,
    val groups: List<String> = emptyList(),
    val liabilityGroups: Set<String> = emptySet(),
    val errors: AccountFormErrors = AccountFormErrors(),
    val transactionCount: Int = 0,
    val otherAccounts: List<AccountChoice> = emptyList(),
    val isLoaded: Boolean = false
) {
    val isEditMode: Boolean get() = accountId != null
    val isLiability: Boolean get() = group in liabilityGroups
}

/** What the form asks its screen to do once an action is done. */
enum class AccountFormResult { Saved, Archived, Deleted }

/**
 * The one Add/Edit Account form (opened with an `accountId` to edit, without one to add). Editing
 * never changes the Initial balance or As-of date: Reconcile corrects a balance (ADR-0009).
 */
@HiltViewModel
class AddEditAccountViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val accountRepository: AccountRepository,
    private val dropdownOptionRepository: DropdownOptionRepository
) : ViewModel() {

    private val editingId: Long? = savedStateHandle.get<Long>("accountId")?.takeIf { it > 0L }

    private val _uiState = MutableStateFlow(AccountFormUiState(accountId = editingId))
    val uiState: StateFlow<AccountFormUiState> = _uiState.asStateFlow()

    private val _results = MutableSharedFlow<AccountFormResult>()
    val results: SharedFlow<AccountFormResult> = _results.asSharedFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            dropdownOptionRepository.getOptionsByType(ACCOUNT_GROUP).collect { options ->
                _uiState.update { state ->
                    val names = options.map { it.name }
                    state.copy(
                        groups = names,
                        liabilityGroups = options.filter { it.role == DropdownRole.LIABILITY }.mapTo(mutableSetOf()) { it.name },
                        // A new Account starts in the first group; an edited one keeps its own.
                        group = if (state.group.isBlank() && editingId == null) names.firstOrNull().orEmpty() else state.group
                    )
                }
            }
        }
        viewModelScope.launch {
            accountRepository.getAllAccounts().collect { accounts ->
                _uiState.update { state ->
                    state.copy(
                        otherAccounts = accounts.filter { it.id != editingId }
                            .map { AccountChoice(it.id, "${it.accountName} (${it.groupName})") }
                    )
                }
            }
        }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val id = editingId ?: run {
            _uiState.update { it.copy(isLoaded = true) }
            return
        }
        val account = accountRepository.getAccountById(id) ?: run {
            _messages.emit("Account not found")
            return
        }
        val count = accountRepository.countTransactions(id)
        // Read the role directly: the groups Flow may not have arrived yet.
        val isLiability = dropdownOptionRepository.getAllOptionsSnapshot().any {
            it.optionType == ACCOUNT_GROUP && it.name == account.groupName && it.role == DropdownRole.LIABILITY
        }
        _uiState.update { state ->
            state.copy(
                group = account.groupName,
                name = account.accountName,
                amountInput = amountToInput(AccountForm.amountShown(account.initialBalance, isLiability)),
                asOfDate = parseFlexibleDate(account.initialBalanceDate) ?: LocalDate.now(),
                description = account.description.orEmpty(),
                includeInTotals = account.includeInTotals,
                isHidden = account.isHidden,
                transactionCount = count,
                isLoaded = true
            )
        }
    }

    fun setGroup(group: String) = _uiState.update { it.copy(group = group, errors = it.errors.copy(group = null)) }
    fun setName(name: String) = _uiState.update { it.copy(name = name, errors = it.errors.copy(name = null)) }
    fun setAmount(amount: String) = _uiState.update { it.copy(amountInput = amount, errors = it.errors.copy(amount = null)) }
    fun setAsOfDate(date: LocalDate) = _uiState.update { it.copy(asOfDate = date) }
    fun setDescription(description: String) = _uiState.update { it.copy(description = description) }
    fun setIncludeInTotals(include: Boolean) = _uiState.update { it.copy(includeInTotals = include) }
    fun setHidden(hidden: Boolean) = _uiState.update { it.copy(isHidden = hidden) }

    /** Adds an Account group (or picks the one already called that) and selects it. */
    fun addGroup(name: String) {
        viewModelScope.launch {
            val added = dropdownOptionRepository.addOptionIfAbsent(ACCOUNT_GROUP, name) ?: return@launch
            setGroup(added)
        }
    }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val others = accountRepository.getAllAccountsSnapshot().filter { it.id != editingId }
            val errors = AccountForm.validate(
                group = state.group,
                name = state.name,
                amountInput = state.amountInput,
                otherAccountNames = others.map { it.accountName },
                isAdding = editingId == null
            )
            if (!errors.isEmpty) {
                _uiState.update { it.copy(errors = errors) }
                return@launch
            }
            val existing = editingId?.let { accountRepository.getAccountById(it) }
            val record = if (existing != null) {
                existing.copy(
                    groupName = state.group,
                    accountName = state.name.trim(),
                    description = state.description.trim().ifBlank { null },
                    includeInTotals = state.includeInTotals,
                    isHidden = state.isHidden
                )
            } else {
                AccountRecord(
                    groupName = state.group,
                    accountName = state.name.trim(),
                    initialBalance = AccountForm.initialBalance(parseAmountInput(state.amountInput) ?: 0.0, state.isLiability),
                    initialBalanceDate = state.asOfDate.toString(),
                    isHidden = state.isHidden,
                    displayOrder = (others.maxOfOrNull { it.displayOrder } ?: -1) + 1,
                    description = state.description.trim().ifBlank { null },
                    includeInTotals = state.includeInTotals
                )
            }
            accountRepository.save(record)
            _results.emit(AccountFormResult.Saved)
        }
    }

    /** Hidden and left out of totals; its Transactions stay. */
    fun archive() {
        val id = editingId ?: return
        viewModelScope.launch {
            val account = accountRepository.getAccountById(id) ?: return@launch
            accountRepository.save(account.copy(isHidden = true, includeInTotals = false))
            _messages.emit("${account.accountName} archived")
            _results.emit(AccountFormResult.Archived)
        }
    }

    /** Deletes the Account, first moving its Transactions to [moveToAccountId] or, when null, deleting them. */
    fun delete(moveToAccountId: Long?) {
        val id = editingId ?: return
        viewModelScope.launch {
            val deleted = accountRepository.permanentlyDeleteAccount(
                accountId = id,
                strategy = if (moveToAccountId == null) PermanentDeleteStrategy.REMOVE_LINKED_TRANSACTIONS
                else PermanentDeleteStrategy.REASSIGN_LINKED_TRANSACTIONS,
                reassignToAccountId = moveToAccountId
            )
            if (deleted) _results.emit(AccountFormResult.Deleted) else _messages.emit("Couldn't delete this account")
        }
    }

    private companion object {
        const val ACCOUNT_GROUP = "ACCOUNT_GROUP"
    }
}
