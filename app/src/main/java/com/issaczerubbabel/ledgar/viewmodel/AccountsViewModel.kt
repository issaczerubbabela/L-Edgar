package com.issaczerubbabel.ledgar.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.patrykandpatrick.vico.core.cartesian.data.CartesianChartModelProducer
import com.patrykandpatrick.vico.core.cartesian.data.lineSeries
import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.account.StatementMonth
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.repository.AccountRepository
import com.issaczerubbabel.ledgar.data.repository.PermanentDeleteStrategy
import com.issaczerubbabel.ledgar.util.TransactionType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val assetGroups: Map<String, List<AccountListItemUi>> = emptyMap(),
    val liabilityGroups: Map<String, List<AccountListItemUi>> = emptyMap()
)

@HiltViewModel
class AccountsViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _events = MutableSharedFlow<String>(replay = 0)
    val events: SharedFlow<String> = _events.asSharedFlow()

    val groupedAccounts: StateFlow<Map<String, List<AccountRecord>>> = accountRepository
        .getAllAccounts()
        .map { accounts -> accounts.groupBy { it.accountGroup } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allAccounts: StateFlow<List<AccountRecord>> = accountRepository
        .getAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val uiState: StateFlow<AccountsScreenUiState> = accountRepository
        .getAccountBook()
        .map { book ->
            val balances = AccountMath.balances(book.accounts, book.transactions)
            val totals = AccountMath.totals(book.accounts, book.transactions)
            val liabilityIds = book.accounts.filter { it.isLiability }.mapTo(mutableSetOf()) { it.id }
            val items = book.records.mapIndexed { index, account ->
                AccountListItemUi(
                    id = account.id,
                    accountGroup = account.accountGroup,
                    accountName = account.accountName,
                    balance = balances.getValue(account.id),
                    isHidden = account.isHidden,
                    canMoveUp = index > 0,
                    canMoveDown = index < book.records.lastIndex
                )
            }
            val (liabilities, assets) = items.partition { it.id in liabilityIds }
            AccountsScreenUiState(
                assets = totals.assets,
                liabilities = totals.liabilities,
                total = totals.netWorth,
                assetGroups = assets.groupBy { it.accountGroup },
                liabilityGroups = liabilities.groupBy { it.accountGroup }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AccountsScreenUiState())

    fun toggleAccountVisibility(accountId: Long) {
        viewModelScope.launch {
            accountRepository.toggleHidden(accountId)
        }
    }

    fun deleteAccount(accountId: Long) {
        viewModelScope.launch {
            val account = accountRepository.getAccountById(accountId) ?: return@launch
            if (accountRepository.hasTransactions(accountId)) {
                accountRepository.save(
                    account.copy(
                        isHidden = true,
                        includeInTotals = false
                    )
                )
                _events.emit("Account has linked transactions, so it was archived (hidden) instead of deleted.")
                return@launch
            }
            accountRepository.delete(account)
        }
    }

    fun deleteAccountPermanently(accountId: Long, reassignToAccountId: Long?) {
        viewModelScope.launch {
            val strategy = if (reassignToAccountId == null) {
                PermanentDeleteStrategy.REMOVE_LINKED_TRANSACTIONS
            } else {
                PermanentDeleteStrategy.REASSIGN_LINKED_TRANSACTIONS
            }

            val deleted = accountRepository.permanentlyDeleteAccount(
                accountId = accountId,
                strategy = strategy,
                reassignToAccountId = reassignToAccountId
            )

            if (!deleted) {
                _events.emit("Unable to delete account permanently")
                return@launch
            }

            _events.emit("Account permanently deleted")
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
    val asOfDate: String = "1970-01-01",
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
    accountRepository: AccountRepository
) : ViewModel() {

    private val accountId: Long = checkNotNull(savedStateHandle.get<String>("accountId")).toLong()
    private val selectedMonth = MutableStateFlow(YearMonth.now())
    val statementChartModelProducer = CartesianChartModelProducer()

    private val accountBook = accountRepository.getAccountBook()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** The selected month's statement section, with the balances either side of it. */
    private data class MonthView(
        val record: AccountRecord?,
        val opening: Double,
        val month: StatementMonth?,
        val recordsById: Map<Long, ExpenseRecord>
    )

    private val monthView: StateFlow<MonthView?> = combine(accountBook, selectedMonth) { book, ym ->
        book ?: return@combine null
        val snapshot = book.accounts.firstOrNull { it.id == accountId } ?: return@combine null
        val statement = AccountMath.statement(snapshot, book.transactions)
        val month = statement.firstOrNull { it.month == ym }
        // A month without Transactions opens where the latest earlier month closed.
        val opening = month?.opening
            ?: statement.firstOrNull { it.month < ym }?.closing
            ?: snapshot.initialBalance
        val rowIds = month?.rows.orEmpty().mapTo(mutableSetOf()) { it.transactionId }
        MonthView(
            record = book.records.firstOrNull { it.id == accountId },
            opening = opening,
            month = month,
            recordsById = book.transactionRecords.filter { it.id in rowIds }.associateBy { it.id }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        viewModelScope.launch {
            combine(monthView, selectedMonth) { view, ym -> view?.let { dailyBalanceSeries(it, ym) } }
                .collect { points ->
                    val (xValues, yValues) = points ?: return@collect
                    statementChartModelProducer.runTransaction {
                        lineSeries { series(xValues, yValues) }
                    }
                }
        }
    }

    val uiState: StateFlow<AccountDetailUiState> = combine(monthView, selectedMonth) { view, ym ->
        val periodFormatter = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
        val month = view?.month
        val entries = month?.rows.orEmpty().map { row ->
            val record = view?.recordsById?.get(row.transactionId)
            AccountStatementItemUi(
                id = row.transactionId,
                date = row.date.toString(),
                category = record?.category?.ifBlank { null } ?: TransactionType.label(record?.type.orEmpty()),
                description = record?.description.orEmpty(),
                paymentMode = record?.paymentMode.orEmpty(),
                amount = row.amount,
                type = record?.type.orEmpty(),
                runningBalance = row.balanceAfter
            )
        }
        val opening = view?.opening ?: 0.0
        AccountDetailUiState(
            accountId = accountId,
            accountName = view?.record?.accountName ?: "Account",
            asOfDate = view?.record?.initialBalanceDate ?: "1970-01-01",
            selectedMonth = ym,
            periodLabel = ym.format(periodFormatter),
            deposit = month?.moneyIn ?: 0.0,
            withdrawal = month?.moneyOut ?: 0.0,
            total = (month?.moneyIn ?: 0.0) - (month?.moneyOut ?: 0.0),
            currentBalance = month?.closing ?: opening,
            entries = entries
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

    private fun dailyBalanceSeries(view: MonthView, ym: YearMonth): Pair<List<Double>, List<Double>> {
        // Rows are newest first; the last one on each day leaves that day's closing balance.
        val closingByDay = view.month?.rows.orEmpty()
            .reversed()
            .associate { it.date.dayOfMonth to it.balanceAfter }
        var running = view.opening
        val xValues = mutableListOf<Double>()
        val yValues = mutableListOf<Double>()
        for (day in 1..ym.lengthOfMonth()) {
            running = closingByDay[day] ?: running
            xValues += day.toDouble()
            yValues += running
        }
        return xValues to yValues
    }
}
