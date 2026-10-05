package com.issaczerubbabel.ledgar.trip

import com.issaczerubbabel.ledgar.util.formatRupeesExact

/**
 * Everything a Trip's screens show and Post uses, worked out from its Members, Trip expenses and
 * Settlements (see the Trips section of CONTEXT.md). Pure: no Room, no Android. Amounts are whole
 * paise so Shares always add up to the expense exactly.
 */
enum class SplitMode { EQUAL, CUSTOM }

data class TripMember(
    val id: Long,
    val name: String,
    val isSelf: Boolean = false,
    val upiId: String? = null,
    /** Index into [MemberPalette]; the Member colour shown everywhere in the Trip. */
    val colorIndex: Int = 0
)

data class TripExpenseInput(
    val id: Long,
    val date: String, // yyyy-MM-dd
    val purpose: String,
    val amountPaise: Long,
    val payerId: Long,
    val mode: SplitMode,
    /** Members the expense is split between, in Trip Member order. */
    val memberIds: List<Long>,
    /** CUSTOM: each Locked amount. Members not listed split what is left equally. Unused for EQUAL. */
    val locked: Map<Long, Long> = emptyMap(),
    val category: String = ""
)

data class TripSettlementInput(val fromId: Long, val toId: Long, val amountPaise: Long)

data class MemberBalance(val memberId: Long, val paidPaise: Long, val sharePaise: Long, val settledPaise: Long) {
    /** Positive: the group owes them. Negative: they owe the group. */
    val netPaise: Long get() = paidPaise - sharePaise + settledPaise
}

data class PlannedPayment(val fromId: Long, val toId: Long, val amountPaise: Long)

data class PostPreviewRow(
    val expenseId: Long,
    val date: String,
    val description: String,
    val category: String,
    val sharePaise: Long
)

/** Why a split can't be saved, or null when it can. */
sealed interface SplitProblem {
    data object NoMembers : SplitProblem

    /** The Locked amounts add up to more than the expense. */
    data class OverAssigned(val overPaise: Long) : SplitProblem

    /** Everyone is locked and the amounts add up to less than the expense. */
    data class UnderAssigned(val leftPaise: Long) : SplitProblem
}

/** One row of the "does this settle everyone?" check. */
data class CheckRow(val memberId: Long, val balancePaise: Long, val paysPaise: Long, val receivesPaise: Long) {
    /** What the Member is still owed (or owes) once the Suggested payments are made. */
    val afterPaise: Long get() = balancePaise + paysPaise - receivesPaise
}

enum class LedgerKind { EXPENSE, SETTLEMENT }

/** One line of a Member's Balance: how an expense or Settlement moved it. */
data class LedgerLine(val label: String, val detail: String, val deltaPaise: Long, val kind: LedgerKind)

data class MemberLedger(val memberId: Long, val lines: List<LedgerLine>) {
    val netPaise: Long get() = lines.sumOf { it.deltaPaise }
}

object TripMath {

    /**
     * Each Member's Share of [expense]. EQUAL splits it equally. CUSTOM gives each Locked amount to
     * its Member and splits what is left equally between the rest. Leftover paise go one each to
     * Members in a rotation seeded by the expense id, so the same person isn't always charged the
     * extra paisa.
     */
    fun shares(expense: TripExpenseInput): Map<Long, Long> {
        val ids = expense.memberIds
        if (ids.isEmpty()) return emptyMap()
        if (expense.mode == SplitMode.EQUAL) return equalSplit(expense.amountPaise, ids, expense.id)
        val freeIds = ids.filter { it !in expense.locked }
        val used = ids.filter { it in expense.locked }.sumOf { lockedAmount(expense, it) }
        val free = equalSplit((expense.amountPaise - used).coerceAtLeast(0L), freeIds, expense.id)
        return ids.associateWith { id -> if (id in expense.locked) lockedAmount(expense, id) else free.getValue(id) }
    }

    private fun lockedAmount(expense: TripExpenseInput, id: Long) = (expense.locked[id] ?: 0L).coerceAtLeast(0L)

    fun splitProblem(expense: TripExpenseInput): SplitProblem? {
        val ids = expense.memberIds
        if (ids.isEmpty()) return SplitProblem.NoMembers
        if (expense.mode == SplitMode.EQUAL) return null
        val used = ids.filter { it in expense.locked }.sumOf { lockedAmount(expense, it) }
        if (used > expense.amountPaise) return SplitProblem.OverAssigned(used - expense.amountPaise)
        val everyoneLocked = ids.all { it in expense.locked }
        if (everyoneLocked && used < expense.amountPaise) return SplitProblem.UnderAssigned(expense.amountPaise - used)
        return null
    }

    fun equalSplit(totalPaise: Long, ids: List<Long>, seed: Long): Map<Long, Long> {
        if (ids.isEmpty()) return emptyMap()
        val n = ids.size
        val base = totalPaise / n
        val remainder = (totalPaise - base * n).toInt()
        val out = LinkedHashMap<Long, Long>()
        ids.forEach { out[it] = base }
        val start = Math.floorMod(seed, n.toLong()).toInt()
        for (i in 0 until remainder) {
            val id = ids[(start + i) % n]
            out[id] = out.getValue(id) + 1
        }
        return out
    }

    fun balances(
        members: List<TripMember>,
        expenses: List<TripExpenseInput>,
        settlements: List<TripSettlementInput>
    ): List<MemberBalance> {
        val paid = HashMap<Long, Long>()
        val share = HashMap<Long, Long>()
        val settled = HashMap<Long, Long>()
        expenses.forEach { e ->
            paid.merge(e.payerId, e.amountPaise, Long::plus)
            shares(e).forEach { (id, v) -> share.merge(id, v, Long::plus) }
        }
        settlements.forEach { s ->
            settled.merge(s.fromId, s.amountPaise, Long::plus)
            settled.merge(s.toId, -s.amountPaise, Long::plus)
        }
        return members.map { MemberBalance(it.id, paid[it.id] ?: 0L, share[it.id] ?: 0L, settled[it.id] ?: 0L) }
    }

    /**
     * The fewest payments that bring every Balance to zero: the largest debtor pays the largest
     * creditor, repeatedly. Ties keep Member order, so recording one payment doesn't reshuffle the rest.
     */
    fun settleUpPlan(balances: List<MemberBalance>): List<PlannedPayment> {
        val order = balances.withIndex().associate { it.value.memberId to it.index }
        val creditors = balances.filter { it.netPaise > 0 }.map { it.memberId to it.netPaise }
            .sortedWith(compareByDescending<Pair<Long, Long>> { it.second }.thenBy { order.getValue(it.first) })
            .toMutableList()
        val debtors = balances.filter { it.netPaise < 0 }.map { it.memberId to -it.netPaise }
            .sortedWith(compareByDescending<Pair<Long, Long>> { it.second }.thenBy { order.getValue(it.first) })
            .toMutableList()
        val out = mutableListOf<PlannedPayment>()
        var i = 0
        var j = 0
        while (i < creditors.size && j < debtors.size) {
            val amount = minOf(creditors[i].second, debtors[j].second)
            if (amount > 0) out += PlannedPayment(debtors[j].first, creditors[i].first, amount)
            creditors[i] = creditors[i].first to creditors[i].second - amount
            debtors[j] = debtors[j].first to debtors[j].second - amount
            if (creditors[i].second == 0L) i++
            if (debtors[j].second == 0L) j++
        }
        return out
    }

    /**
     * Shows that [plan] settles everyone: each Member's Balance, what they pay and receive in the
     * Suggested payments, and what is left, which is zero for every Member when the plan is complete.
     */
    fun settlementCheck(balances: List<MemberBalance>, plan: List<PlannedPayment>): List<CheckRow> =
        balances.map { b ->
            CheckRow(
                memberId = b.memberId,
                balancePaise = b.netPaise,
                paysPaise = plan.filter { it.fromId == b.memberId }.sumOf { it.amountPaise },
                receivesPaise = plan.filter { it.toId == b.memberId }.sumOf { it.amountPaise }
            )
        }

    /** How each expense and Settlement moved one Member's Balance; the lines add up to that Balance. */
    fun memberLedger(
        memberId: Long,
        members: List<TripMember>,
        expenses: List<TripExpenseInput>,
        settlements: List<TripSettlementInput>
    ): MemberLedger {
        val names = members.associate { it.id to it.name }
        val lines = mutableListOf<LedgerLine>()
        expenses.sortedWith(compareBy({ it.date }, { it.id })).forEach { e ->
            val share = shares(e)[memberId] ?: 0L
            val paid = if (e.payerId == memberId) e.amountPaise else 0L
            if (share == 0L && paid == 0L) return@forEach
            val detail = listOfNotNull(
                if (paid > 0) "paid ${rupees(paid)}" else null,
                if (share > 0) "Share ${rupees(share)}" else null
            ).joinToString(" · ")
            lines += LedgerLine(e.purpose, detail, paid - share, LedgerKind.EXPENSE)
        }
        settlements.forEach { s ->
            if (s.fromId == memberId) lines += LedgerLine("Paid ${names[s.toId].orEmpty()}", "Settlement", s.amountPaise, LedgerKind.SETTLEMENT)
            if (s.toId == memberId) lines += LedgerLine("Got paid by ${names[s.fromId].orEmpty()}", "Settlement", -s.amountPaise, LedgerKind.SETTLEMENT)
        }
        return MemberLedger(memberId, lines)
    }

    /** One row per Trip expense where [selfId]'s Share is above zero, by date then id (ADR-0008). */
    fun postPreview(tripName: String, selfId: Long, expenses: List<TripExpenseInput>): List<PostPreviewRow> =
        expenses.sortedWith(compareBy({ it.date }, { it.id })).mapNotNull { e ->
            val mine = shares(e)[selfId] ?: 0L
            if (mine <= 0) null else PostPreviewRow(e.id, e.date, "$tripName · ${e.purpose}", e.category, mine)
        }

    fun rupees(paise: Long): String = formatRupeesExact(paise / 100.0)

    fun decimal(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val abs = kotlin.math.abs(paise)
        return sign + (abs / 100) + "." + (abs % 100).toString().padStart(2, '0')
    }
}
