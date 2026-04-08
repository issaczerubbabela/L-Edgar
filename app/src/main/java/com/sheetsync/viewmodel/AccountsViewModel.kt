package com.sheetsync.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.sheetsync.data.local.entity.AccountRecord
import com.sheetsync.data.local.entity.ExpenseRecord
import com.sheetsync.data.repository.AccountRepository
import com.sheetsync.data.repository.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

data class AccountListItemUi(
    val id: Long,
    val accountGroup: String,
    val accountName: String,
    val balance: Double,
    val isHidden: Boolean,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean
)

data class AccountsScreenUiState(
    val assets: Double = 0.0,
    val liabilities: Double = 0.0,
    val total: Double = 0.0,
    val autoClassifiedLiabilityGroups: List<String> = emptyList(),
    val assetGroups: Map<String, List<AccountListItemUi>> = emptyMap(),
    val liabilityGroups: Map<String, List<AccountListItemUi>> = emptyMap()
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val liabilityGroupKeywords = listOf(
        "credit card",
        "loan",
        "owed",
        "overdraft",
        "debt",
        "payable"
    )

    private val assetGroupKeywords = listOf(
        "cash",
        "bank",
        "account",
        "investment",
        "railway e-wallet",
        "e-wallet",
        "wallet",
        "savings"
    )

    private val _events = MutableSharedFlow<String>(replay = 0)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val groupedAccounts: StateFlow<Map<String, List<AccountRecord>>> = accountRepository
        .getAllAccounts()
        .map { accounts -> accounts.groupBy { it.accountGroup } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private val accountItems: StateFlow<List<AccountListItemUi>> = accountRepository
        .getAccountsWithBalances()
        .map { accountsWithBalances ->
            accountsWithBalances.mapIndexed { index, (account, balance) ->
                AccountListItemUi(
                    id = account.id,
                    accountGroup = account.accountGroup,
                    accountName = account.accountName,
                    balance = balance,
                    isHidden = account.isHidden,
                    canMoveUp = index > 0,
                    canMoveDown = index < accountsWithBalances.lastIndex
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val groupedAccountItems: StateFlow<Map<String, List<AccountListItemUi>>> = accountItems
        .map { items ->
            items.groupBy { it.accountGroup }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val uiState: StateFlow<AccountsScreenUiState> = combine(
        groupedAccountItems,
        accountItems
    ) { groups, _ ->
        val assetGroups = groups.filterKeys { isAssetGroup(it) }
        val liabilityGroups = groups.filterKeys { isLiabilityGroup(it) }
        val autoLiabilityGroups = groups.keys.filter { isLiabilityGroup(it) }

        val assets = assetGroups.values.flatten().sumOf { it.balance }
        val liabilities = liabilityGroups.values.flatten().sumOf { kotlin.math.abs(it.balance) }

        AccountsScreenUiState(
            assets = assets,
            liabilities = liabilities,
            total = assets - liabilities,
            autoClassifiedLiabilityGroups = autoLiabilityGroups,
            assetGroups = assetGroups,
            liabilityGroups = liabilityGroups
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountsScreenUiState())

    private fun isAssetGroup(groupName: String): Boolean {
        val normalized = groupName.trim().lowercase(Locale.getDefault())
        if (isLiabilityGroup(groupName)) return false
        if (assetGroupKeywords.any { normalized.contains(it) }) return true
        // Default unknown groups to asset to keep net-worth view complete.
        return true
    }

    private fun isLiabilityGroup(groupName: String): Boolean {
        val normalized = groupName.trim().lowercase(Locale.getDefault())
        return liabilityGroupKeywords.any { normalized.contains(it) }
    }

    fun toggleAccountVisibility(accountId: Long) {
        viewModelScope.launch {
            accountRepository.toggleHidden(accountId)
        }
    }

    fun deleteAccount(accountId: Long) {
        viewModelScope.launch {
            val account = accountRepository.getAccountById(accountId) ?: return@launch
            if (accountRepository.hasTransactions(accountId)) {
                _events.emit("Cannot delete account with existing transactions. Please Hide it instead.")
                return@launch
            }
            accountRepository.delete(account)
        }
    }

    fun moveAccountUp(accountId: Long) {
        viewModelScope.launch {
            val accounts = accountRepository.getAllAccountsSnapshot()
            val currentIndex = accounts.indexOfFirst { it.id == accountId }
            if (currentIndex <= 0) return@launch

            val above = accounts[currentIndex - 1]
            val current = accounts[currentIndex]
            accountRepository.swapDisplayOrder(current.id, above.id)
        }
    }

    fun moveAccountDown(accountId: Long) {
        viewModelScope.launch {
            val accounts = accountRepository.getAllAccountsSnapshot()
            val currentIndex = accounts.indexOfFirst { it.id == accountId }
            if (currentIndex == -1 || currentIndex >= accounts.lastIndex) return@launch

            val below = accounts[currentIndex + 1]
            val current = accounts[currentIndex]
            accountRepository.swapDisplayOrder(current.id, below.id)
        }
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
@OptIn(ExperimentalCoroutinesApi::class)
class AccountDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    accountRepository: AccountRepository,
    expenseRepository: ExpenseRepository
) : ViewModel() {

    private val accountId: Long = checkNotNull(savedStateHandle.get<String>("accountId")).toLong()
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    val statementChartModelProducer = CartesianChartModelProducer()

    private val accountFlow: StateFlow<AccountRecord?> = accountRepository
        .getAllAccounts()
        .map { accounts -> accounts.firstOrNull { it.id == accountId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val monthRecords: StateFlow<List<ExpenseRecord>> = selectedMonth
        .flatMapLatest { ym ->
            expenseRepository.getTransactionsForAccountInMonth(
                accountId = accountId,
                startOfMonth = ym.atDay(1).toString(),
                endOfMonth = ym.atEndOfMonth().toString()
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val historicalSum: StateFlow<Double?> = selectedMonth
        .flatMapLatest { ym ->
            expenseRepository.getHistoricalSumForAccount(
                accountId = accountId,
                beforeDate = ym.atDay(1).toString()
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            combine(accountFlow, historicalSum, monthRecords, selectedMonth) { account, history, records, ym ->
                val baseBalance = (account?.initialBalance ?: 0.0) + (history ?: 0.0)
                buildDailyRunningBalanceSeries(records, baseBalance, ym)
            }.collect { (xValues, yValues) ->
                statementChartModelProducer.runTransaction {
                    lineSeries { series(xValues, yValues) }
                }
            }
        }
    }

    val uiState: StateFlow<AccountDetailUiState> = combine(
        accountFlow,
        historicalSum,
        monthRecords,
        selectedMonth
    ) { account, historySum, records, ym ->
        val periodFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

        val sorted = records.sortedWith(compareBy<ExpenseRecord> { it.date }.thenBy { it.id })

        val deposit = records.sumOf { record ->
            if (record.type == "Income") record.amount else 0.0
        }
        val withdrawal = records.sumOf { record ->
            if (record.type == "Expense") record.amount else 0.0
        }
        val total = deposit - withdrawal

        val baseBalance = (account?.initialBalance ?: 0.0) + (historySum ?: 0.0)
        val endBalance = baseBalance + total

        var running = baseBalance
        val statement = sorted.map { record ->
            running += accountDelta(record, accountId)
            val item = AccountStatementItemUi(
                id = record.id,
                date = record.date,
                category = record.category,
                description = record.description,
                paymentMode = record.paymentMode,
                amount = record.amount,
                type = record.type,
                runningBalance = running
            )
            item
        }

        val displayStatement = statement.sortedByDescending { it.date }

        AccountDetailUiState(
            accountId = accountId,
            accountName = account?.accountName ?: "Account",
            selectedMonth = ym,
            periodLabel = ym.format(periodFormatter),
            deposit = deposit,
            withdrawal = withdrawal,
            total = total,
            currentBalance = endBalance,
            entries = displayStatement
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountDetailUiState(accountId = accountId))

    fun prevMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }

    fun setMonthFromDate(date: LocalDate) {
        selectedMonth.value = YearMonth.from(date)
    }

    fun setMonthYear(year: Int, month: Int) {
        selectedMonth.value = YearMonth.of(year, month.coerceIn(1, 12))
    }

    private fun accountDelta(record: ExpenseRecord, accountId: Long): Double {
        return when {
            record.type == "Income" && record.accountId == accountId -> record.amount
            record.type == "Expense" && record.accountId == accountId -> -record.amount
            else -> 0.0
        }
    }

    private fun buildDailyRunningBalanceSeries(
        records: List<ExpenseRecord>,
        baseBalance: Double,
        ym: YearMonth
    ): Pair<List<Double>, List<Double>> {
        val dailyNetByDay = records.groupBy {
            runCatching { LocalDate.parse(it.date).dayOfMonth }.getOrDefault(1)
        }.mapValues { (_, txns) ->
            txns.sumOf { accountDelta(it, accountId) }
        }

        var running = baseBalance
        val xValues = mutableListOf<Double>()
        val yValues = mutableListOf<Double>()
        for (day in 1..ym.lengthOfMonth()) {
            running += dailyNetByDay[day] ?: 0.0
            xValues += day.toDouble()
            yValues += running
        }
        return xValues to yValues
    }
}
