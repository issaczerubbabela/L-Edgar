package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.local.entity.Fund
import com.issaczerubbabel.ledgar.data.local.entity.FundHolding
import com.issaczerubbabel.ledgar.data.local.entity.FundPlan
import com.issaczerubbabel.ledgar.data.remote.FundSyncDto
import com.issaczerubbabel.ledgar.fund.FundsSnapshot
import com.issaczerubbabel.ledgar.fund.RestoredFund
import kotlinx.coroutines.flow.Flow

/** The outcome of adding or changing a holding. */
sealed interface HoldingResult {
    data class Saved(val holdingId: Long) : HoldingResult

    /** Another Fund holds this Account whole, so it can't be held again. */
    data class AccountHeldWhole(val fundId: Long) : HoldingResult

    /** A whole holding was asked for, but other Funds hold fixed amounts of this Account. */
    data object AccountHasOtherHoldings : HoldingResult

    /** This Fund already holds this Account. */
    data object AlreadyInFund : HoldingResult

    data object FundNotFound : HoldingResult
}

/** Funds, their Holdings and Planned contributions (ADR-0011). ViewModels reach Funds only through here. */
interface FundRepository {
    /** Every Fund, archived ones included, in display order. */
    fun observeFunds(): Flow<List<Fund>>
    fun observeFund(fundId: Long): Flow<Fund?>

    /** Every Fund, Holding and Planned contribution, for [com.issaczerubbabel.ledgar.fund.FundMath] and the cycle figures. */
    fun observeSnapshot(): Flow<FundsSnapshot>

    /** The same, read in one transaction. */
    suspend fun getSnapshot(): FundsSnapshot

    suspend fun createFund(fund: Fund): Long
    suspend fun updateFund(fund: Fund)

    /** Archives a Fund: it keeps its history, and its money is released to Free. */
    suspend fun archiveFund(fundId: Long, archivedAt: String)
    suspend fun unarchiveFund(fundId: Long)

    /** Deletes a Fund with its Holdings and Planned contributions. Its Transactions are untouched. */
    suspend fun deleteFund(fundId: Long)

    /**
     * Adds a holding ([FundHolding.id] 0) or changes one. An Account is the whole holding of one Fund
     * at most, and is never both held whole by one Fund and in part by another.
     */
    suspend fun saveHolding(holding: FundHolding): HoldingResult
    suspend fun removeHolding(holdingId: Long)

    fun observePlans(cycleId: Long): Flow<List<FundPlan>>

    /** Sets a Fund's Planned contribution in a cycle; zero or less removes it. */
    suspend fun setPlannedContribution(fundId: Long, cycleId: Long, amount: Double)

    /** Every Fund as it is backed up to the Sheet, with holdings by Account name and plans by cycle start date. */
    suspend fun getBackup(): List<FundSyncDto>

    /** Replaces every Fund, Holding and Planned contribution with [funds], atomically. */
    suspend fun replaceAllFromBackup(funds: List<RestoredFund>)
}
