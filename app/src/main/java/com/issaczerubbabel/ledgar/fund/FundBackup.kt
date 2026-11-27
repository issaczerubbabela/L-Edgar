package com.issaczerubbabel.ledgar.fund

import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.data.remote.FundHoldingImportDto
import com.issaczerubbabel.ledgar.data.remote.FundHoldingSyncDto
import com.issaczerubbabel.ledgar.data.remote.FundImportDto
import com.issaczerubbabel.ledgar.data.remote.FundPlanImportDto
import com.issaczerubbabel.ledgar.data.remote.FundPlanSyncDto
import com.issaczerubbabel.ledgar.data.remote.FundSyncDto
import java.time.LocalDate

/** A holding ready to be written back, pointing at an Account id on this phone. */
data class RestoredHolding(
    val accountId: Long,
    val kind: HoldingKind,
    val amount: Double,
    val openingAmount: Double,
    val startDate: String
)

/** A Planned contribution ready to be written back, pointing at a cycle id on this phone. */
data class RestoredPlan(val cycleId: Long, val plannedAmount: Double)

/** A Fund ready to be written back, with no Fund id: that is assigned on insert. */
data class RestoredFund(
    val name: String,
    val emoji: String,
    val colorIndex: Int,
    val targetAmount: Double?,
    val dueDate: String?,
    val archivedAt: String?,
    val sortOrder: Int,
    val createdDate: String,
    val holdings: List<RestoredHolding>,
    val plans: List<RestoredPlan>
)

/**
 * Converts Funds between the database rows and the nested shape stored in Google Sheets. Pure.
 * Holdings travel by Account name and Planned contributions by cycle start date, because Import
 * rebuilds Account and cycle ids (ADR-0011). Restoring treats the Sheet as hand-edited and
 * untrusted: anything that can't be placed on this phone, or would break a database rule, is dropped.
 */
object FundBackupMapper {

    fun toSyncDtos(snapshot: FundsSnapshot, accounts: List<AccountRecord>, cycles: List<BudgetCycle>): List<FundSyncDto> {
        val accountNames = accounts.associate { it.id to it.accountName }
        val cycleStarts = cycles.associate { it.id to it.startDate }
        val holdingsByFund = snapshot.holdings.groupBy { it.fundId }
        val plansByFund = snapshot.plans.groupBy { it.fundId }
        return snapshot.funds
            .sortedWith(compareBy({ it.sortOrder }, { it.id }))
            .map { fund ->
                FundSyncDto(
                    id = fund.id,
                    name = fund.name,
                    emoji = fund.emoji,
                    colorIndex = fund.colorIndex,
                    targetAmount = fund.targetAmount,
                    dueDate = fund.dueDate,
                    archivedAt = fund.archivedAt,
                    sortOrder = fund.sortOrder,
                    createdDate = fund.createdDate,
                    holdings = holdingsByFund[fund.id].orEmpty().sortedBy { it.id }.mapNotNull { holding ->
                        FundHoldingSyncDto(
                            accountName = accountNames[holding.accountId] ?: return@mapNotNull null,
                            kind = holding.holdingKind.name,
                            amount = holding.amount,
                            openingAmount = holding.openingAmount,
                            startDate = holding.startDate
                        )
                    },
                    plans = plansByFund[fund.id].orEmpty()
                        .mapNotNull { plan -> cycleStarts[plan.cycleId]?.let { FundPlanSyncDto(it, plan.plannedAmount) } }
                        .sortedBy { it.cycleStartDate }
                )
            }
    }

    /**
     * Resolves [dtos] against this phone's [accounts] (by name, ignoring case) and [cycles] (by start
     * date). An Account stays the whole holding of one Fund: the first Fund to claim it keeps it.
     */
    fun fromImportDtos(
        dtos: List<FundImportDto>,
        accounts: List<AccountRecord>,
        cycles: List<BudgetCycle>,
        today: LocalDate
    ): List<RestoredFund> {
        val accountIdByName = LinkedHashMap<String, Long>()
        accounts.sortedBy { it.id }.forEach { accountIdByName.putIfAbsent(key(it.accountName), it.id) }
        val cycleIdByStart = LinkedHashMap<String, Long>()
        cycles.sortedBy { it.id }.forEach { cycleIdByStart.putIfAbsent(it.startDate, it.id) }
        val wholeClaimed = HashSet<Long>()

        return dtos.mapIndexedNotNull { index, dto ->
            val name = dto.name?.trim().orEmpty()
            if (name.isEmpty()) return@mapIndexedNotNull null
            val createdDate = parseDate(dto.createdDate) ?: today

            val heldAccounts = HashSet<Long>()
            val holdings = dto.holdings.orEmpty().mapNotNull { holding ->
                val accountId = accountIdByName[key(holding.accountName.orEmpty())] ?: return@mapNotNull null
                val kind = HoldingKind.parse(holding.kind) ?: return@mapNotNull null
                if (!heldAccounts.add(accountId)) return@mapNotNull null
                if (kind == HoldingKind.WHOLE && !wholeClaimed.add(accountId)) return@mapNotNull null
                RestoredHolding(
                    accountId = accountId,
                    kind = kind,
                    amount = if (kind == HoldingKind.FIXED) amount(holding.amount) else 0.0,
                    openingAmount = amount(holding.openingAmount),
                    startDate = (parseDate(holding.startDate) ?: createdDate).toString()
                )
            }

            val plannedCycles = HashSet<Long>()
            val plans = dto.plans.orEmpty().mapNotNull { plan ->
                val start = parseDate(plan.cycleStartDate)?.toString() ?: return@mapNotNull null
                val cycleId = cycleIdByStart[start] ?: return@mapNotNull null
                if (!plannedCycles.add(cycleId)) return@mapNotNull null
                RestoredPlan(cycleId, amount(plan.plannedAmount))
            }

            RestoredFund(
                name = name,
                emoji = dto.emoji?.trim().orEmpty(),
                colorIndex = Math.floorMod(dto.colorIndex ?: BudgetBucket.colorIndexFor(name), BudgetBucket.COLOR_COUNT),
                targetAmount = dto.targetAmount?.takeIf { it.isFinite() && it > 0.0 },
                dueDate = parseDate(dto.dueDate)?.toString(),
                archivedAt = dto.archivedAt?.trim()?.takeIf { it.isNotEmpty() }?.let { parseDate(it)?.toString() ?: createdDate.toString() },
                sortOrder = dto.sortOrder ?: index,
                createdDate = createdDate.toString(),
                holdings = holdings,
                plans = plans
            )
        }
    }

    /** [dto] as a restore reads it, so the phone's own Funds can be put back by name after Import rebuilds ids. */
    fun asImportDto(dto: FundSyncDto): FundImportDto = FundImportDto(
        name = dto.name,
        emoji = dto.emoji,
        colorIndex = dto.colorIndex,
        targetAmount = dto.targetAmount,
        dueDate = dto.dueDate,
        archivedAt = dto.archivedAt,
        sortOrder = dto.sortOrder,
        createdDate = dto.createdDate,
        holdings = dto.holdings.map {
            FundHoldingImportDto(it.accountName, it.kind, it.amount, it.openingAmount, it.startDate)
        },
        plans = dto.plans.map { FundPlanImportDto(it.cycleStartDate, it.plannedAmount) }
    )

    private fun amount(raw: Double?): Double = raw?.takeIf { it.isFinite() && it >= 0.0 } ?: 0.0

    private fun key(name: String) = name.trim().lowercase()

    private fun parseDate(raw: String?): LocalDate? =
        raw?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }
}
