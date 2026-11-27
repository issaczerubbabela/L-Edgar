package com.issaczerubbabel.ledgar.data.repository

import androidx.room.withTransaction
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.dao.FundDao
import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.FundPlan
import com.issaczerubbabel.ledgar.data.local.entity.HoldingKind
import com.issaczerubbabel.ledgar.data.remote.FundSyncDto
import com.issaczerubbabel.ledgar.fund.FundBackupMapper
import com.issaczerubbabel.ledgar.fund.FundsSnapshot
import com.issaczerubbabel.ledgar.fund.RestoredFund
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class FundRepositoryImpl @Inject constructor(
    private val db: SheetSyncDatabase,
    private val dao: FundDao
) : FundRepository {

    override fun observeFunds(): Flow<List<Fund>> = dao.observeFunds()

    override fun observeSnapshot(): Flow<FundsSnapshot> =
        combine(dao.observeFunds(), dao.observeHoldings(), dao.observeAllPlans(), ::FundsSnapshot)

    override suspend fun getSnapshot(): FundsSnapshot = db.withTransaction {
        FundsSnapshot(dao.getAllFunds(), dao.getAllHoldings(), dao.getAllPlans())
    }

    override suspend fun saveFund(fund: Fund, holdings: List<FundHolding>, removedHoldingIds: List<Long>): SaveFundResult =
        try {
            db.withTransaction { writeFund(fund, holdings, removedHoldingIds) }
        } catch (refusal: Refusal) {
            refusal.result
        }

    /** Thrown inside the transaction so Room rolls it back. */
    private class Refusal(val result: SaveFundResult) : Exception()

    private suspend fun writeFund(fund: Fund, holdings: List<FundHolding>, removedHoldingIds: List<Long>): SaveFundResult {
        val fundId = if (fund.id == 0L) {
            createFund(fund)
        } else {
            if (dao.getFund(fund.id) == null) throw Refusal(SaveFundResult.FundNotFound)
            updateFund(fund)
            fund.id
        }
        removedHoldingIds.forEach { removeHolding(it) }
        val ids = LinkedHashMap<Long, Long>()
        holdings.forEach { holding ->
            when (val result = saveHolding(holding.copy(fundId = fundId))) {
                is HoldingResult.Saved -> ids[holding.accountId] = result.holdingId
                else -> throw Refusal(SaveFundResult.Refused(holding.accountId, result))
            }
        }
        return SaveFundResult.Saved(fundId, ids)
    }

    override suspend fun createFund(fund: Fund): Long = dao.insertFund(fund.copy(id = 0, name = fund.name.trim()))

    override suspend fun updateFund(fund: Fund) = dao.updateFund(fund.copy(name = fund.name.trim()))

    override suspend fun archiveFund(fundId: Long, archivedAt: String) {
        db.withTransaction { dao.getFund(fundId)?.let { dao.updateFund(it.copy(archivedAt = archivedAt)) } }
    }

    override suspend fun unarchiveFund(fundId: Long) {
        db.withTransaction { dao.getFund(fundId)?.let { dao.updateFund(it.copy(archivedAt = null)) } }
    }

    override suspend fun deleteFund(fundId: Long) {
        db.withTransaction { dao.getFund(fundId)?.let { dao.deleteFund(it) } }
    }

    override suspend fun saveHolding(holding: FundHolding): HoldingResult = db.withTransaction {
        if (dao.getFund(holding.fundId) == null) return@withTransaction HoldingResult.FundNotFound
        val kind = holding.holdingKind
        val others = dao.getAllHoldings().filter { it.accountId == holding.accountId && it.id != holding.id }

        if (others.any { it.fundId == holding.fundId }) return@withTransaction HoldingResult.AlreadyInFund
        others.firstOrNull { it.holdingKind == HoldingKind.WHOLE }?.let {
            return@withTransaction HoldingResult.AccountHeldWhole(it.fundId)
        }
        if (kind == HoldingKind.WHOLE && others.isNotEmpty()) return@withTransaction HoldingResult.AccountHasOtherHoldings

        val clean = holding.copy(kind = kind.name, amount = if (kind == HoldingKind.FIXED) holding.amount else 0.0)
        if (holding.id == 0L) {
            HoldingResult.Saved(dao.insertHolding(clean))
        } else {
            dao.updateHolding(clean)
            HoldingResult.Saved(holding.id)
        }
    }

    override suspend fun removeHolding(holdingId: Long) {
        db.withTransaction { dao.getHolding(holdingId)?.let { dao.deleteHolding(it) } }
    }

    override suspend fun setPlannedContribution(fundId: Long, cycleId: Long, amount: Double) {
        if (amount > 0.0 && amount.isFinite()) dao.upsertPlan(FundPlan(fundId, cycleId, amount))
        else dao.deletePlan(fundId, cycleId)
    }

    override suspend fun getBackup(): List<FundSyncDto> = db.withTransaction {
        FundBackupMapper.toSyncDtos(
            FundsSnapshot(dao.getAllFunds(), dao.getAllHoldings(), dao.getAllPlans()),
            db.accountDao().getAllAccountsSnapshot(),
            db.bucketBudgetDao().getAllCycles()
        )
    }

    override suspend fun replaceAllFromBackup(funds: List<RestoredFund>) {
        db.withTransaction {
            dao.deleteAllPlans()
            dao.deleteAllHoldings()
            dao.deleteAllFunds()
            funds.forEach { restored ->
                val fundId = dao.insertFund(
                    Fund(
                        name = restored.name,
                        emoji = restored.emoji,
                        colorIndex = restored.colorIndex,
                        targetAmount = restored.targetAmount,
                        dueDate = restored.dueDate,
                        archivedAt = restored.archivedAt,
                        sortOrder = restored.sortOrder,
                        createdDate = restored.createdDate
                    )
                )
                restored.holdings.forEach { holding ->
                    dao.insertHolding(
                        FundHolding(
                            fundId = fundId,
                            accountId = holding.accountId,
                            kind = holding.kind.name,
                            amount = holding.amount,
                            openingAmount = holding.openingAmount,
                            startDate = holding.startDate
                        )
                    )
                }
                dao.upsertPlans(restored.plans.map { FundPlan(fundId, it.cycleId, it.plannedAmount) })
            }
        }
    }
}
