package com.sheetsync.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheetsync.data.local.entity.AccountRecord
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.repository.AccountRepository
import com.sheetsync.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class AccountListItemUi(
    val id: Long,
    val accountGroup: String,
    val accountName: String,
    val balance: Double
)

data class AccountsScreenUiState(
    val assets: Double = 0.0,
    val liabilities: Double = 0.0,
    val total: Double = 0.0,
    val groupedAccounts: Map<String, List<AccountListItemUi>> = emptyMap()
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    accountRepository: AccountRepository,
    expenseRepository: ExpenseRepository
) : ViewModel() {

    val groupedAccounts: StateFlow<Map<String, List<AccountRecord>>> = accountRepository
        .getAllAccounts()
        .map { accounts ->
            accounts
                .sortedWith(compareBy({ it.accountGroup.lowercase(Locale.getDefault()) }, { it.accountName.lowercase(Locale.getDefault()) }))
                .groupBy { it.accountGroup }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val accountItems: StateFlow<List<AccountListItemUi>> = combine(
        accountRepository.getAllAccounts(),
        expenseRepository.getAllRecords()
    ) { accounts, records ->
        val balances = calculateBalances(accounts, records)
        accounts.map { account ->
            AccountListItemUi(
                id = account.id,
                accountGroup = account.accountGroup,
                accountName = account.accountName,
                balance = balances[account.id] ?: account.initialBalance
            )
        }.sortedWith(compareBy({ it.accountGroup.lowercase(Locale.getDefault()) }, { it.accountName.lowercase(Locale.getDefault()) }))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val groupedAccountItems: StateFlow<Map<String, List<AccountListItemUi>>> = accountItems
        .map { items ->
            items.groupBy { it.accountGroup }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val uiState: StateFlow<AccountsScreenUiState> = combine(
        groupedAccountItems,
        accountItems
    ) { groups, items ->
        val assets = items.filter { it.balance >= 0 }.sumOf { it.balance }
        val liabilities = items.filter { it.balance < 0 }.sumOf { kotlin.math.abs(it.balance) }

        AccountsScreenUiState(assets = assets, liabilities = liabilities, total = assets - liabilities, groupedAccounts = groups)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountsScreenUiState())

    private fun calculateBalances(
        accounts: List<AccountRecord>,
        records: List<ExpenseRecord>
    ): Map<Long, Double> {
        val balances = accounts.associate { it.id to it.initialBalance }.toMutableMap()

        records.forEach { record ->
            when (record.type) {
                "Income" -> {
                    val to = record.toAccountId
                    if (to != null && balances.containsKey(to)) {
                        balances[to] = (balances[to] ?: 0.0) + record.amount
                    }
                }
                "Expense" -> {
                    val from = record.fromAccountId
                    if (from != null && balances.containsKey(from)) {
                        balances[from] = (balances[from] ?: 0.0) - record.amount
                    }
                }
                "Transfer" -> {
                    val from = record.fromAccountId
                    val to = record.toAccountId
                    if (from != null && balances.containsKey(from)) {
                        balances[from] = (balances[from] ?: 0.0) - record.amount
                    }
                    if (to != null && balances.containsKey(to)) {
                        balances[to] = (balances[to] ?: 0.0) + record.amount
                    }
                }
            }
        }

        return balances
    }
}


data class AccountStatementItemUi(
    val id: Long,
    val date: String,
    val category: String,
    val description: String,
    val paymentMode: String,
    val amount: Double,
    val type: String,
    val runningBalance: Double
)

data class AccountDetailUiState(
    val accountId: Long = -1,
    val accountName: String = "",
    val selectedMonth: YearMonth = YearMonth.now(),
    val periodLabel: String = "",
    val deposit: Double = 0.0,
    val withdrawal: Double = 0.0,
    val total: Double = 0.0,
    val currentBalance: Double = 0.0,
    val entries: List<AccountStatementItemUi> = emptyList()
)

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    accountRepository: AccountRepository,
    expenseRepository: ExpenseRepository
) : ViewModel() {

    private val accountId: Long = checkNotNull(savedStateHandle.get<String>("accountId")).toLong()
    private val selectedMonth = kotlinx.coroutines.flow.MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<AccountDetailUiState> = combine(
        accountRepository.getAllAccounts(),
        expenseRepository.getRecordsForAccount(accountId),
        selectedMonth
    ) { accounts, records, ym ->
        val account = accounts.firstOrNull { it.id == accountId }
        val periodFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

        val monthRecords = records.filter { record ->
            runCatching { LocalDate.parse(record.date) }.getOrNull()?.let { d ->
                d.year == ym.year && d.monthValue == ym.monthValue
            } == true
        }

        val sorted = monthRecords.sortedByDescending { it.date }

        val deposit = monthRecords.sumOf { record ->
            when {
                record.type == "Income" && record.toAccountId == accountId -> record.amount
                record.type == "Transfer" && record.toAccountId == accountId -> record.amount
                else -> 0.0
            }
        }
        val withdrawal = monthRecords.sumOf { record ->
            when {
                record.type == "Expense" && record.fromAccountId == accountId -> record.amount
                record.type == "Transfer" && record.fromAccountId == accountId -> record.amount
                else -> 0.0
            }
        }

        var running = account?.initialBalance ?: 0.0
        val allSortedAsc = records.sortedBy { it.date }
        allSortedAsc.forEach { record ->
            running += accountDelta(record, accountId)
        }

        var reverseRunning = running
        val statement = sorted.map { record ->
            val item = AccountStatementItemUi(
                id = record.id,
                date = record.date,
                category = record.category,
                description = record.description,
                paymentMode = record.paymentMode,
                amount = record.amount,
                type = record.type,
                runningBalance = reverseRunning
            )
            reverseRunning -= accountDelta(record, accountId)
            item
        }

        AccountDetailUiState(
            accountId = accountId,
            accountName = account?.accountName ?: "Account",
            selectedMonth = ym,
            periodLabel = ym.format(periodFormatter),
            deposit = deposit,
            withdrawal = withdrawal,
            total = deposit - withdrawal,
            currentBalance = running,
            entries = statement
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountDetailUiState(accountId = accountId))

    fun prevMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }

    private fun accountDelta(record: ExpenseRecord, accountId: Long): Double {
        return when {
            record.type == "Income" && record.toAccountId == accountId -> record.amount
            record.type == "Expense" && record.fromAccountId == accountId -> -record.amount
            record.type == "Transfer" && record.toAccountId == accountId -> record.amount
            record.type == "Transfer" && record.fromAccountId == accountId -> -record.amount
            else -> 0.0
        }
    }
}
