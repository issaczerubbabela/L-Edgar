package com.issaczerubbabel.ledgar.viewmodel

import com.issaczerubbabel.ledgar.account.AccountMath
import com.issaczerubbabel.ledgar.account.TransactionSnapshot
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.data.repository.AccountBook
import com.issaczerubbabel.ledgar.fund.FundMath
import com.issaczerubbabel.ledgar.fund.FundsSnapshot
import com.issaczerubbabel.ledgar.ledger.Ledger
import com.issaczerubbabel.ledgar.ledger.LedgerRow
import com.issaczerubbabel.ledgar.util.formatMoney
import com.issaczerubbabel.ledgar.util.formatRupees
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One Fund on the Funds list. */
data class FundListItemUi(
    val fundId: Long,
    val name: String,
    val emoji: String,
    val colorIndex: Int,
    /** "₹1,00,000"; blank for an archived Fund, which holds nothing. */
    val balance: String,
    /** "₹36,000 of ₹1,00,000" when the Fund has a target. */
    val progress: String?,
    val progressFraction: Float?,
    /** "₹5,000 short" when a fixed holding is above its Account's balance. */
    val shortBy: String?,
    val isArchived: Boolean,
    /** What TalkBack reads for the row. */
    val description: String
)

data class FundsUiState(
    val active: List<FundListItemUi> = emptyList(),
    val archived: List<FundListItemUi> = emptyList(),
    val isLoaded: Boolean = false
)

/** One Holding on a Fund's page. */
data class HoldingRowUi(
    val holdingId: Long,
    val accountId: Long,
    val accountName: String,
    val kind: HoldingKind,
    /** "Whole account" or "Fixed amount". */
    val kindLabel: String,
    val value: String,
    val shortBy: String?,
    /** The Account isn't Included in totals, so Your money doesn't see this holding. */
    val notInTotals: Boolean,
    /** "Opening ₹15,000 on 1 Sep 2026". */
    val openingNote: String,
    /** The whole row as one sentence for TalkBack: "₹70,000 fixed in HDFC". */
    val sentence: String
)

/** This Salary cycle's Planned contribution against what was Set aside. */
data class FundCycleUi(
    val planned: String?,
    val transferred: String,
    /** "₹5,000 still to transfer", "Planned amount transferred", or "Nothing planned this cycle". */
    val status: String,
    val isMet: Boolean
)

enum class FundHistoryKind { SET_ASIDE, DRAWN }

data class FundHistoryRowUi(
    val transaction: ExpenseRecord,
    val row: LedgerRow,
    val kind: FundHistoryKind,
    /** "1 Oct · Set aside" or "5 Oct · Drawn". */
    val trailingLine: String
) {
    val transactionId: Long get() = transaction.id
}

data class FundDetailUiState(
    val fundId: Long = 0,
    val fund: Fund? = null,
    val name: String = "",
    val emoji: String = "",
    val colorIndex: Int = 0,
    val balance: String = "",
    /** "Archived on 1 Oct 2026: its money went back to Free". */
    val archivedNote: String? = null,
    val progress: String? = null,
    val progressFraction: Float? = null,
    /** "Due 31 Mar 2027". */
    val dueDate: String? = null,
    val holdings: List<HoldingRowUi> = emptyList(),
    /** "Emergency Fund RD ₹30,000 · HDFC ₹70,000 fixed". */
    val holdingSummary: String = "",
    val cycle: FundCycleUi? = null,
    /** Newest first. */
    val history: List<FundHistoryRowUi> = emptyList(),
    val isLoaded: Boolean = false,
    val notFound: Boolean = false
)

/** Free on an Account's page (CONTEXT.md): its balance less the fixed holdings in it, or the Fund holding it whole. */
data class AccountFundUi(
    /** "Free ₹30,000.00", or "Fund: Emergency". */
    val label: String,
    /** "₹70,000.00 held for Emergency · ₹5,000.00 short". */
    val detail: String,
    val isShort: Boolean
)

private val dayLabel = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val longDateLabel = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

private fun parseDay(raw: String?): LocalDate? = raw?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() }

private fun progressOf(fund: Fund, balance: Double): Pair<String, Float>? {
    val target = fund.targetAmount?.takeIf { it > 0.0 } ?: return null
    return "${formatRupees(balance)} of ${formatRupees(target)}" to (balance / target).toFloat().coerceAtLeast(0f)
}

/** How far each Fund's fixed holdings are above their Accounts' balances, by Fund id. */
private fun shortByFund(holdings: List<FundHolding>, balances: Map<Long, Double>): Map<Long, Double> {
    val short = FundMath.shortfalls(holdings, balances)
    return holdings.filter { it.holdingKind == HoldingKind.FIXED && it.accountId in short }
        .groupBy { it.fundId }
        .mapValues { (_, fixed) -> fixed.map { it.accountId }.distinct().sumOf { short.getValue(it) } }
}

/** The Funds list: active Funds in order with their balances, then archived ones. */
internal fun fundsList(snapshot: FundsSnapshot, book: AccountBook): FundsUiState {
    val balances = AccountMath.balances(book.accounts, book.transactions)
    val holdings = snapshot.activeHoldings
    val fundBalances = FundMath.fundBalances(holdings, balances)
    val short = shortByFund(holdings, balances)

    fun item(fund: Fund): FundListItemUi {
        val balance = fundBalances[fund.id] ?: 0.0
        val progress = if (fund.isArchived) null else progressOf(fund, balance)
        val shortBy = if (fund.isArchived) null else short[fund.id]?.let { "${formatRupees(it)} short" }
        val description = buildString {
            append(fund.name)
            if (fund.isArchived) append(", archived")
            else {
                append(", ").append(formatRupees(balance))
                fund.targetAmount?.takeIf { it > 0.0 }?.let { append(" of ").append(formatRupees(it)) }
            }
            shortBy?.let { append(", ").append(it) }
        }
        return FundListItemUi(
            fundId = fund.id,
            name = fund.name,
            emoji = fund.emoji,
            colorIndex = fund.colorIndex,
            balance = if (fund.isArchived) "" else formatRupees(balance),
            progress = progress?.first,
            progressFraction = progress?.second,
            shortBy = shortBy,
            isArchived = fund.isArchived,
            description = description
        )
    }

    val ordered = snapshot.funds.sortedWith(compareBy<Fund> { it.sortOrder }.thenBy { it.id })
    return FundsUiState(
        active = ordered.filterNot { it.isArchived }.map(::item),
        archived = ordered.filter { it.isArchived }.map(::item),
        isLoaded = true
    )
}

/** A Fund's page, or null when the Fund no longer exists. [cycle] is the running Salary cycle, if any. */
internal fun fundDetail(
    fundId: Long,
    snapshot: FundsSnapshot,
    book: AccountBook,
    cycle: BudgetCycle?,
    today: LocalDate
): FundDetailUiState? {
    val fund = snapshot.funds.firstOrNull { it.id == fundId } ?: return null
    val balances = AccountMath.balances(book.accounts, book.transactions)
    val own = snapshot.holdings.filter { it.fundId == fundId }
    // An archived Fund holds nothing now, but its history still reads its own holdings.
    val counting = if (fund.isArchived) (snapshot.activeHoldings + own).distinctBy { it.id } else snapshot.activeHoldings
    val accountNames = book.accounts.associate { it.id to it.name }
    val included = book.accounts.filter { it.includedInTotals }.mapTo(HashSet()) { it.id }
    val shortfalls = FundMath.shortfalls(counting, balances)
    val balance = FundMath.fundBalance(fundId, counting, balances)

    val rows = own.sortedWith(compareBy<FundHolding> { it.holdingKind.ordinal }.thenBy { it.id }).map { holding ->
        val name = accountNames[holding.accountId] ?: "Unknown account"
        val value = formatRupees(FundMath.holdingValue(holding, balances))
        val short = if (holding.holdingKind == HoldingKind.FIXED) shortfalls[holding.accountId]?.let { "${formatRupees(it)} short" } else null
        val notInTotals = holding.accountId !in included
        val sentence = buildString {
            if (holding.holdingKind == HoldingKind.WHOLE) append(name).append(", whole account, ").append(value)
            else append(value).append(" fixed in ").append(name)
            short?.let { append(", ").append(it) }
            if (notInTotals) append(", not counted in Your money")
        }
        val start = parseDay(holding.startDate)
        HoldingRowUi(
            holdingId = holding.id,
            accountId = holding.accountId,
            accountName = name,
            kind = holding.holdingKind,
            kindLabel = if (holding.holdingKind == HoldingKind.WHOLE) "Whole account" else "Fixed amount",
            value = value,
            shortBy = short,
            notInTotals = notInTotals,
            openingNote = "Opening ${formatRupees(holding.openingAmount)}" + (start?.let { " on ${it.format(longDateLabel)}" } ?: ""),
            sentence = sentence
        )
    }

    val cycleUi = cycle?.let {
        val planned = snapshot.plansFor(it.id).firstOrNull { plan -> plan.fundId == fundId }?.plannedAmount ?: 0.0
        val transferred = FundMath.setAside(it, book.transactions, counting, today).byFund[fundId] ?: 0.0
        // A Fund this cycle neither plans for nor puts money into has nothing to say here.
        if (planned <= 0.0 && transferred == 0.0) return@let null
        val still = AccountMath.rupees(AccountMath.paise(planned) - AccountMath.paise(transferred))
        FundCycleUi(
            planned = planned.takeIf { p -> p > 0.0 }?.let(::formatRupees),
            transferred = formatRupees(transferred),
            status = when {
                planned <= 0.0 -> "Nothing planned this cycle"
                still > 0.0 -> "${formatRupees(still)} still to transfer"
                else -> "Planned amount transferred"
            },
            isMet = planned > 0.0 && still <= 0.0
        )
    }

    val records = book.transactionRecords.associateBy { it.id }
    val history = book.transactions.mapNotNull { txn ->
        val kind = when (fundId) {
            FundMath.setAsideInto(txn, counting) -> FundHistoryKind.SET_ASIDE
            FundMath.drawnFrom(txn, counting) -> FundHistoryKind.DRAWN
            else -> return@mapNotNull null
        }
        val record = records[txn.id] ?: return@mapNotNull null
        txn to FundHistoryRowUi(
            transaction = record,
            row = Ledger.row(record, book.records),
            kind = kind,
            trailingLine = "${txn.date.format(dayLabel)} · ${if (kind == FundHistoryKind.SET_ASIDE) "Set aside" else "Drawn"}"
        )
    }
        .sortedWith(compareByDescending<Pair<TransactionSnapshot, FundHistoryRowUi>> { it.first.date }.thenByDescending { it.first.id })
        .map { it.second }

    val progress = if (fund.isArchived) null else progressOf(fund, balance)
    return FundDetailUiState(
        fundId = fundId,
        fund = fund,
        name = fund.name,
        emoji = fund.emoji,
        colorIndex = fund.colorIndex,
        balance = formatRupees(if (fund.isArchived) 0.0 else balance),
        archivedNote = if (fund.isArchived) {
            "Archived${parseDay(fund.archivedAt)?.let { " on ${it.format(longDateLabel)}" } ?: ""}: its money went back to Free"
        } else {
            null
        },
        progress = progress?.first,
        progressFraction = progress?.second,
        dueDate = parseDay(fund.dueDate)?.let { "Due ${it.format(longDateLabel)}" },
        holdings = rows,
        holdingSummary = rows.joinToString(" · ") { row ->
            if (row.kind == HoldingKind.WHOLE) "${row.accountName} ${row.value}" else "${row.accountName} ${row.value} fixed"
        },
        cycle = if (fund.isArchived) null else cycleUi,
        history = history,
        isLoaded = true
    )
}

/**
 * [accountId]'s Free for its page: the Fund holding it whole, or its balance less the fixed
 * holdings in it. Null while there are no active Funds, when Free would only repeat the balance.
 */
internal fun accountFund(accountId: Long, snapshot: FundsSnapshot, book: AccountBook): AccountFundUi? {
    if (snapshot.activeFunds.isEmpty()) return null
    val holdings = snapshot.activeHoldings
    val fundNames = snapshot.activeFunds.associate { it.id to it.name }
    FundMath.wholeAccounts(holdings)[accountId]?.let { fundId ->
        val name = fundNames[fundId].orEmpty()
        return AccountFundUi(label = "Fund: $name", detail = "Its whole balance is $name's", isShort = false)
    }
    val balances = AccountMath.balances(book.accounts, book.transactions)
    val free = FundMath.accountFree(listOf(accountId), holdings, balances)[accountId] ?: 0.0
    val fixed = holdings.filter { it.accountId == accountId && it.holdingKind == HoldingKind.FIXED }
    val short = FundMath.shortfalls(holdings, balances)[accountId]
    val detail = listOfNotNull(
        fixed.joinToString(" · ") { "${formatMoney(it.amount)} held for ${fundNames[it.fundId].orEmpty()}" }.takeIf { it.isNotBlank() },
        short?.let { "${formatMoney(it)} short" }
    ).joinToString(" · ")
    return AccountFundUi(label = "Free ${formatMoney(free)}", detail = detail, isShort = short != null)
}
