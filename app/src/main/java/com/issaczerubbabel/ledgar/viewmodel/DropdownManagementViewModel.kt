package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.issaczerubbabel.ledgar.data.local.dao.DropdownUsage
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.DropdownRole
import com.issaczerubbabel.ledgar.data.repository.DropdownOptionRepository
import com.issaczerubbabel.ledgar.data.repository.UndoableAction
import com.issaczerubbabel.ledgar.data.repository.UndoableActions
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A role an option can take (ADR-0004), how the UI names it, and what it does once chosen. */
data class RoleChoice(val role: String, val label: String, val effect: String)

/** [roles] are the roles options of this type can take besides none; empty when the type has none. */
enum class DropdownOptionType(val key: String, val label: String, val roles: List<RoleChoice> = emptyList()) {
    ExpenseCategory(
        "EXPENSE_CATEGORY", "Expense Categories",
        listOf(RoleChoice(DropdownRole.SAVING, "Saving", "Counts as saving in Stats"))
    ),
    IncomeCategory(
        "INCOME_CATEGORY", "Income Categories",
        listOf(RoleChoice(DropdownRole.REFUND, "Refund", "Counts as refund in Stats"))
    ),
    AccountGroup(
        "ACCOUNT_GROUP", "Account Groups",
        listOf(
            RoleChoice(DropdownRole.SAVINGS, "Savings", "Counts as savings in Stats"),
            RoleChoice(DropdownRole.LIABILITY, "Liability", "Money owed: counts in Liabilities")
        )
    ),
    PaymentMode("PAYMENT_MODE", "Payment Modes")
}

/** The question before deleting an option something still uses, naming what uses it. */
data class DeleteConfirm(val option: DropdownOption, val usage: DropdownUsage) {
    val title: String get() = "Delete ${option.name}?"

    /** "12 transactions, 1 Bucket route and 2 Merchant rules use it. They'll keep the name." */
    val message: String get() = OptionUsage.describe(usage)
}

/** How the delete confirm and its Undo snackbar word an option's use. */
object OptionUsage {

    fun describe(usage: DropdownUsage): String {
        val users = listOf(
            usage.transactions to "transaction",
            usage.bucketRoutes to "Bucket route",
            usage.merchantRules to "Merchant rule",
            usage.repeatRules to "Repeat rule"
        ).filter { (count, _) -> count > 0 }
        val sentences = mutableListOf<String>()
        if (users.isNotEmpty()) {
            val one = users.size == 1 && users.single().first == 1
            sentences += "${joined(users.map { (count, noun) -> counted(count, noun) })} ${if (one) "uses" else "use"} it."
        }
        if (usage.accounts > 0) {
            sentences += if (usage.accounts == 1) "1 Account is in it." else "${usage.accounts} Accounts are in it."
        }
        val total = usage.transactions + usage.bucketRoutes + usage.merchantRules + usage.repeatRules + usage.accounts
        if (total > 0) sentences += if (total == 1) "It'll keep the name." else "They'll keep the name."
        return sentences.joinToString(" ")
    }

    fun deletedMessage(option: DropdownOption) = "Deleted “${option.name}”"

    private fun counted(count: Int, noun: String) = if (count == 1) "1 $noun" else "$count ${noun}s"

    private fun joined(parts: List<String>) =
        if (parts.size == 1) parts.single() else parts.dropLast(1).joinToString(", ") + " and " + parts.last()
}

data class DropdownManagementUiState(
    val selectedType: DropdownOptionType = DropdownOptionType.ExpenseCategory,
    val options: List<DropdownOption> = emptyList(),
    val isAddDialogVisible: Boolean = false,
    val newOptionName: String = "",
    /** Asking before deleting an option in use; null when not asking. */
    val deleteConfirm: DeleteConfirm? = null
)

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class DropdownManagementViewModel @Inject constructor(
    private val dropdownRepository: DropdownOptionRepository,
    private val undoableActions: UndoableActions
) : ViewModel() {

    val types: List<DropdownOptionType> = DropdownOptionType.entries

    private val selectedType = MutableStateFlow(DropdownOptionType.ExpenseCategory)
    private val addDialogVisible = MutableStateFlow(false)
    private val newOptionName = MutableStateFlow("")
    private val deleteConfirm = MutableStateFlow<DeleteConfirm?>(null)

    // A deleted option stays in Room, hidden, until its Undo window ends: Undo just shows it again,
    // in its place and with its role, because nothing about it was ever written.
    private val optionsForSelectedType = combine(
        selectedType.flatMapLatest { type -> dropdownRepository.getOptionsByType(type.key) },
        undoableActions.hiddenDropdownOptionIds
    ) { options, hidden -> options.filterNot { it.id in hidden } }

    val uiState: StateFlow<DropdownManagementUiState> = combine(
        selectedType,
        optionsForSelectedType,
        addDialogVisible,
        newOptionName,
        deleteConfirm
    ) { type, options, showDialog, optionName, confirm ->
        DropdownManagementUiState(
            selectedType = type,
            options = options,
            isAddDialogVisible = showDialog,
            newOptionName = optionName,
            deleteConfirm = confirm
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DropdownManagementUiState()
    )

    fun selectType(type: DropdownOptionType) {
        selectedType.value = type
    }

    /** Sets or clears the option's Stats role (ADR-0004); an empty [role] means an ordinary option. */
    fun setRole(option: DropdownOption, role: String) {
        if (option.role == role) return
        viewModelScope.launch { dropdownRepository.update(option.copy(role = role)) }
    }

    fun showAddDialog() {
        addDialogVisible.value = true
        newOptionName.value = ""
    }

    fun hideAddDialog() {
        addDialogVisible.value = false
        newOptionName.value = ""
    }

    fun updateNewOptionName(value: String) {
        newOptionName.value = value
    }

    fun addOption() {
        val name = uiState.value.newOptionName.trim()
        if (name.isBlank()) return

        val type = uiState.value.selectedType
        viewModelScope.launch {
            // Counting a deleted option still offering Undo too, so Undo never puts it in a taken place.
            val options = dropdownRepository.getOptionsByType(type.key).first()
            val maxOrder = options.maxOfOrNull { it.displayOrder } ?: -1
            dropdownRepository.insert(
                DropdownOption(
                    optionType = type.key,
                    name = name,
                    displayOrder = maxOrder + 1
                )
            )
            hideAddDialog()
        }
    }

    /** The Undo snackbar's action: this delete, or any other still offering Undo. */
    val pendingDelete: StateFlow<UndoableAction?> = undoableActions.pending

    /** Deletes [option] at once if nothing uses it, or asks first, naming what does. Either way offers Undo. */
    fun requestDelete(option: DropdownOption) {
        viewModelScope.launch {
            val usage = dropdownRepository.usage(option)
            if (usage.isUnused) delete(option) else deleteConfirm.value = DeleteConfirm(option, usage)
        }
    }

    fun confirmDelete() {
        val confirm = deleteConfirm.value ?: return
        deleteConfirm.value = null
        delete(confirm.option)
    }

    fun cancelDelete() {
        deleteConfirm.value = null
    }

    fun undoDelete(action: UndoableAction) = undoableActions.undo(action)

    fun commitPendingDelete() = undoableActions.commit()

    private fun delete(option: DropdownOption) = undoableActions.perform(
        UndoableAction(OptionUsage.deletedMessage(option), hiddenDropdownOptionIds = setOf(option.id)) {
            dropdownRepository.delete(option)
        }
    )

    override fun onCleared() {
        commitPendingDelete()
    }

    fun moveUp(option: DropdownOption) {
        val options = uiState.value.options
        val currentIndex = options.indexOfFirst { it.id == option.id }
        if (currentIndex <= 0) return

        val above = options[currentIndex - 1]
        val updatedCurrent = option.copy(displayOrder = above.displayOrder)
        val updatedAbove = above.copy(displayOrder = option.displayOrder)

        viewModelScope.launch {
            dropdownRepository.updateOptions(listOf(updatedCurrent, updatedAbove))
        }
    }

    fun moveDown(option: DropdownOption) {
        val options = uiState.value.options
        val currentIndex = options.indexOfFirst { it.id == option.id }
        if (currentIndex == -1 || currentIndex >= options.lastIndex) return

        val below = options[currentIndex + 1]
        val updatedCurrent = option.copy(displayOrder = below.displayOrder)
        val updatedBelow = below.copy(displayOrder = option.displayOrder)

        viewModelScope.launch {
            dropdownRepository.updateOptions(listOf(updatedCurrent, updatedBelow))
        }
    }
}
