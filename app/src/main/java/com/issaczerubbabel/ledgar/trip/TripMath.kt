package com.issaczerubbabel.ledgar.trip

import com.issaczerubbabel.ledgar.util.formatRupeesExact

/**
 * Everything a Trip's screens show and Post uses, worked out from its Members, Trip expenses and
 * Settlements (see the Trips section of CONTEXT.md). Pure: no Room, no Android. Amounts are whole
 * paise so Shares always add up to the expense exactly.
 */
enum class SplitMode { EQUAL, ADJUST, EXACT }

data class TripMember(val id: Long, val name: String, val isSelf: Boolean = false, val upiId: String? = null)

data class TripExpenseInput(
    val id: Long,
    val date: String, // yyyy-MM-dd
    val purpose: String,
    val amountPaise: Long,
    val payerId: Long,
    val mode: SplitMode,
    /** Members the expense is split between, in Trip Member order. */
    val memberIds: List<Long>,
    /** ADJUST: each Member's fixed extra. EXACT: each Member's whole Share. Unused for EQUAL. */
    val inputs: Map<Long, Long> = emptyMap(),
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
    data class ExactLeft(val leftPaise: Long) : SplitProblem // negative: assigned too much
    data object AdjustTooLarge : SplitProblem
}

object TripMath {

    /**
     * Each Member's Share of [expense]. EQUAL and ADJUST use a largest-remainder split: the leftover
     * paise go one each to Members in a rotation seeded by the expense id, so the same person isn't
     * always charged the extra paisa. EXACT returns the entered amounts as they are.
     */
    fun shares(expense: TripExpenseInput): Map<Long, Long> {
        val ids = expense.memberIds
        if (ids.isEmpty()) return emptyMap()
        return when (expense.mode) {
            SplitMode.EXACT -> ids.associateWith { expense.inputs[it] ?: 0L }
            SplitMode.ADJUST -> {
                val extras = ids.associateWith { (expense.inputs[it] ?: 0L).coerceAtLeast(0L) }
                val rest = (expense.amountPaise - extras.values.sum()).coerceAtLeast(0L)
                val equal = equalSplit(rest, ids, expense.id)
                ids.associateWith { equal.getValue(it) + extras.getValue(it) }
            }
            SplitMode.EQUAL -> equalSplit(expense.amountPaise, ids, expense.id)
        }
    }

    fun splitProblem(expense: TripExpenseInput): SplitProblem? {
        if (expense.memberIds.isEmpty()) return SplitProblem.NoMembers
        return when (expense.mode) {
            SplitMode.EXACT -> {
                val left = expense.amountPaise - expense.memberIds.sumOf { expense.inputs[it] ?: 0L }
                if (left != 0L) SplitProblem.ExactLeft(left) else null
            }
            SplitMode.ADJUST ->
                if (expense.memberIds.sumOf { expense.inputs[it] ?: 0L } > expense.amountPaise) SplitProblem.AdjustTooLarge else null
            SplitMode.EQUAL -> null
        }
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
            .map { it.first to it.second }.toMutableList()
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

    /** One row per Trip expense where [selfId]'s Share is above zero, by date then id (ADR-0008). */
    fun postPreview(tripName: String, selfId: Long, expenses: List<TripExpenseInput>): List<PostPreviewRow> =
        expenses.sortedWith(compareBy({ it.date }, { it.id })).mapNotNull { e ->
            val mine = shares(e)[selfId] ?: 0L
            if (mine <= 0) null else PostPreviewRow(e.id, e.date, "$tripName · ${e.purpose}", e.category, mine)
        }

    fun summaryText(
        tripName: String,
        members: List<TripMember>,
        expenses: List<TripExpenseInput>,
        settlements: List<TripSettlementInput>
    ): String {
        val balances = balances(members, expenses, settlements)
        val names = members.associate { it.id to it.name }
        val plan = settleUpPlan(balances)
        return buildString {
            append(tripName).append('\n')
            append("Total spent: ").append(rupees(expenses.sumOf { it.amountPaise })).append("\n\n")
            append("Shares\n")
            balances.forEach { b ->
                append("• ").append(names[b.memberId]).append(": ").append(rupees(b.sharePaise))
                    .append(" (paid ").append(rupees(b.paidPaise)).append(")\n")
            }
            append('\n')
            if (plan.isEmpty()) {
                append("Everyone is settled.")
            } else {
                append("To settle up\n")
                append(plan.joinToString("\n") { "• ${names[it.fromId]} pays ${names[it.toId]} ${rupees(it.amountPaise)}" })
            }
        }
    }

    fun csv(members: List<TripMember>, expenses: List<TripExpenseInput>): String {
        val names = members.associate { it.id to it.name }
        val header = listOf("date", "purpose", "category", "amount", "paid_by") + members.map { it.name }
        val rows = expenses.sortedWith(compareBy({ it.date }, { it.id })).map { e ->
            val s = shares(e)
            listOf(e.date, e.purpose, e.category, decimal(e.amountPaise), names[e.payerId].orEmpty()) +
                members.map { decimal(s[it.id] ?: 0L) }
        }
        return (listOf(header) + rows).joinToString("\n") { row -> row.joinToString(",") { csvField(it) } }
    }

    fun rupees(paise: Long): String = formatRupeesExact(paise / 100.0)

    fun decimal(paise: Long): String {
        val sign = if (paise < 0) "-" else ""
        val abs = kotlin.math.abs(paise)
        return sign + (abs / 100) + "." + (abs % 100).toString().padStart(2, '0')
    }

    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
