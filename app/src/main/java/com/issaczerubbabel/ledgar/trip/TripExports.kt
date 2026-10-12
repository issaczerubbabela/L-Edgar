package com.issaczerubbabel.ledgar.trip

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One block of the PDF report; the Android side lays these out on pages. */
sealed interface ReportBlock {
    data class Title(val text: String) : ReportBlock
    data class Subtitle(val text: String) : ReportBlock
    data class Heading(val text: String) : ReportBlock
    data class Line(val text: String) : ReportBlock
    /** A table row; [weights] set the relative width of each column. */
    data class Row(val cells: List<String>, val weights: List<Float>, val bold: Boolean = false) : ReportBlock
}

/** Text a Trip can be exported as, built from the same numbers the screens show. */
object TripExports {

    private val dayFormat = DateTimeFormatter.ofPattern("d MMM")

    private fun day(date: String): String = runCatching { LocalDate.parse(date).format(dayFormat) }.getOrDefault(date)

    private fun name(members: List<TripMember>, id: Long) = members.firstOrNull { it.id == id }?.name.orEmpty()

    fun summaryText(
        tripName: String,
        members: List<TripMember>,
        expenses: List<TripExpenseInput>,
        settlements: List<TripSettlementInput>
    ): String {
        val balances = TripMath.balances(members, expenses, settlements)
        val plan = TripMath.settleUpPlan(balances)
        return buildString {
            append(tripName).append('\n')
            append("Total spent: ").append(TripMath.rupees(expenses.sumOf { it.amountPaise })).append("\n\n")
            append("Shares\n")
            balances.forEach { b ->
                append("• ").append(name(members, b.memberId)).append(": ").append(TripMath.rupees(b.sharePaise))
                    .append(" (paid ").append(TripMath.rupees(b.paidPaise)).append(")\n")
            }
            append('\n')
            if (plan.isEmpty()) {
                append("Everyone is settled.")
            } else {
                append("To settle up\n")
                append(plan.joinToString("\n") { "• ${name(members, it.fromId)} pays ${name(members, it.toId)} ${TripMath.rupees(it.amountPaise)}" })
            }
        }
    }

    /** What one person needs to know: their lines, their totals and what they pay or get. */
    fun statementText(
        tripName: String,
        memberId: Long,
        members: List<TripMember>,
        expenses: List<TripExpenseInput>,
        settlements: List<TripSettlementInput>
    ): String {
        val balances = TripMath.balances(members, expenses, settlements)
        val mine = balances.first { it.memberId == memberId }
        val plan = TripMath.settleUpPlan(balances)
        return buildString {
            append(tripName).append(" · ").append(name(members, memberId)).append("\n\n")
            val ownLines = expenses.sortedWith(compareBy({ it.date }, { it.id })).mapNotNull { e ->
                val share = TripMath.shares(e)[memberId] ?: 0L
                val paid = if (e.payerId == memberId) e.amountPaise else 0L
                if (share == 0L && paid == 0L) null
                else "• ${day(e.date)}  ${e.purpose}: " + listOfNotNull(
                    if (paid > 0) "paid ${TripMath.rupees(paid)}" else null,
                    if (share > 0) "Share ${TripMath.rupees(share)}" else null
                ).joinToString(", ")
            }
            ownLines.forEach { append(it).append('\n') }
            if (ownLines.isEmpty()) append("Nothing recorded yet.\n")
            append('\n')
            append("Paid ").append(TripMath.rupees(mine.paidPaise)).append(" · Share ").append(TripMath.rupees(mine.sharePaise)).append('\n')
            val actions = plan.mapNotNull { p ->
                when (memberId) {
                    p.fromId -> "Pay ${name(members, p.toId)} ${TripMath.rupees(p.amountPaise)}"
                    p.toId -> "${name(members, p.fromId)} pays you ${TripMath.rupees(p.amountPaise)}"
                    else -> null
                }
            }
            if (actions.isEmpty()) append("You're settled.") else append(actions.joinToString("\n"))
        }
    }

    fun expensesCsv(members: List<TripMember>, expenses: List<TripExpenseInput>): String {
        val header = listOf("date", "purpose", "category", "amount", "paid_by") + members.map { it.name }
        val rows = expenses.sortedWith(compareBy({ it.date }, { it.id })).map { e ->
            val s = TripMath.shares(e)
            listOf(e.date, e.purpose, e.category, TripMath.decimal(e.amountPaise), name(members, e.payerId)) +
                members.map { TripMath.decimal(s[it.id] ?: 0L) }
        }
        return (listOf(header) + rows).joinToString("\n") { row -> row.joinToString(",") { csvField(it) } }
    }

    /** One row per person, then the recorded Settlements. */
    fun balancesCsv(
        members: List<TripMember>,
        expenses: List<TripExpenseInput>,
        settlements: List<TripSettlementInput>
    ): String {
        val balances = TripMath.balances(members, expenses, settlements)
        val people = listOf(listOf("person", "paid", "share", "settled", "balance")) +
            balances.map { b ->
                listOf(name(members, b.memberId), TripMath.decimal(b.paidPaise), TripMath.decimal(b.sharePaise), TripMath.decimal(b.settledPaise), TripMath.decimal(b.netPaise))
            }
        val log = listOf(listOf("settlement_from", "settlement_to", "amount")) +
            settlements.map { listOf(name(members, it.fromId), name(members, it.toId), TripMath.decimal(it.amountPaise)) }
        return (people + listOf(emptyList()) + log).joinToString("\n") { row -> row.joinToString(",") { csvField(it) } }
    }

    fun reportBlocks(
        tripName: String,
        dates: String,
        members: List<TripMember>,
        expenses: List<TripExpenseInput>,
        settlements: List<TripSettlementInput>
    ): List<ReportBlock> {
        val balances = TripMath.balances(members, expenses, settlements)
        val plan = TripMath.settleUpPlan(balances)
        val out = mutableListOf<ReportBlock>()
        out += ReportBlock.Title(tripName)
        out += ReportBlock.Subtitle("$dates · ${members.size} people · ${expenses.size} expenses · ${TripMath.rupees(expenses.sumOf { it.amountPaise })}")
        out += ReportBlock.Heading("People")
        out += ReportBlock.Row(listOf("Person", "Paid", "Share", "Settled", "Balance"), listOf(2f, 1.5f, 1.5f, 1.5f, 1.7f), bold = true)
        balances.forEach { b ->
            out += ReportBlock.Row(
                listOf(name(members, b.memberId), TripMath.rupees(b.paidPaise), TripMath.rupees(b.sharePaise), TripMath.rupees(b.settledPaise), signed(b.netPaise)),
                listOf(2f, 1.5f, 1.5f, 1.5f, 1.7f)
            )
        }
        out += ReportBlock.Heading("Expenses")
        out += ReportBlock.Row(listOf("Date", "Purpose", "Paid by", "Amount"), listOf(1f, 3.2f, 1.6f, 1.6f), bold = true)
        expenses.sortedWith(compareBy({ it.date }, { it.id })).forEach { e ->
            out += ReportBlock.Row(listOf(day(e.date), e.purpose, name(members, e.payerId), TripMath.rupees(e.amountPaise)), listOf(1f, 3.2f, 1.6f, 1.6f))
        }
        out += ReportBlock.Heading("Settle up")
        if (plan.isEmpty()) out += ReportBlock.Line("Everyone is settled.")
        plan.forEach { out += ReportBlock.Line("${name(members, it.fromId)} pays ${name(members, it.toId)} ${TripMath.rupees(it.amountPaise)}") }
        if (settlements.isNotEmpty()) {
            out += ReportBlock.Heading("Recorded Settlements")
            settlements.forEach { out += ReportBlock.Line("${name(members, it.fromId)} paid ${name(members, it.toId)} ${TripMath.rupees(it.amountPaise)}") }
        }
        return out
    }

    private fun signed(paise: Long) = (if (paise > 0) "+" else if (paise < 0) "-" else "") + TripMath.rupees(kotlin.math.abs(paise))

    private fun csvField(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
