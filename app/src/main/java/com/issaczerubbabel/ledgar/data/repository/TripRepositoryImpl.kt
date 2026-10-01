package com.issaczerubbabel.ledgar.data.repository

import androidx.room.withTransaction
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.dao.CaptureDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.dao.TripDao
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.TripExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.TripExpenseShareRecord
import com.issaczerubbabel.ledgar.data.local.entity.TripMemberRecord
import com.issaczerubbabel.ledgar.data.local.entity.TripRecord
import com.issaczerubbabel.ledgar.data.local.entity.TripSettlementRecord
import com.issaczerubbabel.ledgar.trip.SplitMode
import com.issaczerubbabel.ledgar.trip.TripExpenseInput
import com.issaczerubbabel.ledgar.trip.TripMath
import com.issaczerubbabel.ledgar.trip.TripMember
import com.issaczerubbabel.ledgar.util.TransactionType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

class TripRepositoryImpl @Inject constructor(
    private val database: SheetSyncDatabase,
    private val tripDao: TripDao,
    private val expenseDao: ExpenseDao,
    private val captureDao: CaptureDao
) : TripRepository {

    override fun observeTrips(): Flow<List<TripRecord>> = tripDao.observeTrips()

    override fun observeActiveTrip(): Flow<TripRecord?> = tripDao.observeActiveTrip()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDetail(tripId: Long): Flow<TripDetail?> =
        tripDao.observeTrip(tripId).flatMapLatest { trip ->
            if (trip == null) {
                flowOf(null)
            } else {
                combine(
                    tripDao.observeMembers(tripId),
                    tripDao.observeExpenses(tripId),
                    tripDao.observeShares(tripId),
                    tripDao.observeSettlements(tripId)
                ) { members, expenses, shares, settlements ->
                    val byExpense = shares.groupBy { it.expenseId }
                    val order = members.withIndex().associate { it.value.id to it.index }
                    TripDetail(
                        trip = trip,
                        members = members.map { TripMember(it.id, it.name, it.isSelf, it.upiId) },
                        expenses = expenses.map { e ->
                            val rows = byExpense[e.id].orEmpty().sortedBy { order[it.memberId] ?: Int.MAX_VALUE }
                            TripExpenseInput(
                                id = e.id,
                                date = e.date,
                                purpose = e.purpose,
                                amountPaise = e.amountPaise,
                                payerId = e.payerMemberId,
                                mode = runCatching { SplitMode.valueOf(e.splitMode) }.getOrDefault(SplitMode.EQUAL),
                                memberIds = rows.map { it.memberId },
                                inputs = rows.filter { it.inputPaise != 0L }.associate { it.memberId to it.inputPaise },
                                category = e.category
                            )
                        },
                        settlements = settlements,
                        postedCount = expenses.count { it.postedExpenseId != null }
                    )
                }
            }
        }

    override suspend fun createTrip(name: String, startDate: String, endDate: String?, otherMembers: List<String>): Long =
        database.withTransaction {
            val tripId = tripDao.insertTrip(TripRecord(name = name.trim(), startDate = startDate, endDate = endDate))
            tripDao.insertMember(TripMemberRecord(tripId = tripId, name = "You", isSelf = true, displayOrder = 0))
            otherMembers.map(String::trim).filter(String::isNotEmpty).forEachIndexed { i, member ->
                tripDao.insertMember(TripMemberRecord(tripId = tripId, name = member, displayOrder = i + 1))
            }
            tripId
        }

    override suspend fun addMember(tripId: Long, name: String): Long = database.withTransaction {
        requireActive(tripId)
        val order = (tripDao.getMembers(tripId).maxOfOrNull { it.displayOrder } ?: 0) + 1
        tripDao.insertMember(TripMemberRecord(tripId = tripId, name = name.trim(), displayOrder = order))
    }

    override suspend fun updateMember(memberId: Long, name: String, upiId: String?) = database.withTransaction {
        val member = tripDao.getMember(memberId) ?: return@withTransaction
        requireActive(member.tripId)
        tripDao.updateMember(member.copy(name = name.trim().ifEmpty { member.name }, upiId = upiId?.trim()?.ifEmpty { null }))
    }

    override suspend fun removeMember(memberId: Long): Boolean = database.withTransaction {
        val member = tripDao.getMember(memberId) ?: return@withTransaction true
        requireActive(member.tripId)
        if (member.isSelf || tripDao.countMemberUses(memberId) > 0) return@withTransaction false
        tripDao.deleteMember(memberId)
        true
    }

    override suspend fun saveExpense(tripId: Long, expense: TripExpenseInput): Long =
        database.withTransaction { saveExpenseInTransaction(tripId, expense) }

    private suspend fun saveExpenseInTransaction(tripId: Long, expense: TripExpenseInput): Long {
        requireActive(tripId)
        require(expense.amountPaise > 0) { "A trip expense needs an amount" }
        require(TripMath.splitProblem(expense) == null) { "The split doesn't add up" }
        val record = TripExpenseRecord(
            id = expense.id,
            tripId = tripId,
            date = expense.date,
            purpose = expense.purpose.trim(),
            amountPaise = expense.amountPaise,
            payerMemberId = expense.payerId,
            splitMode = expense.mode.name,
            category = expense.category
        )
        val id = if (expense.id == 0L) {
            tripDao.insertExpense(record)
        } else {
            tripDao.updateExpense(record)
            tripDao.deleteSharesFor(expense.id)
            expense.id
        }
        val shares = TripMath.shares(expense.copy(id = id))
        val keepInputs = expense.mode != SplitMode.EQUAL
        tripDao.insertShares(
            expense.memberIds.map { memberId ->
                TripExpenseShareRecord(
                    expenseId = id,
                    memberId = memberId,
                    inputPaise = if (keepInputs) expense.inputs[memberId] ?: 0L else 0L,
                    sharePaise = shares[memberId] ?: 0L
                )
            }
        )
        return id
    }

    override suspend fun deleteExpense(expenseId: Long) = database.withTransaction {
        val expense = tripDao.getExpense(expenseId) ?: return@withTransaction
        requireActive(expense.tripId)
        tripDao.deleteExpense(expenseId)
    }

    override suspend fun recordSettlement(tripId: Long, fromMemberId: Long, toMemberId: Long, amountPaise: Long, date: String): Long =
        database.withTransaction {
            requireActive(tripId)
            require(amountPaise > 0 && fromMemberId != toMemberId)
            tripDao.insertSettlement(TripSettlementRecord(tripId = tripId, fromMemberId = fromMemberId, toMemberId = toMemberId, amountPaise = amountPaise, date = date))
        }

    override suspend fun deleteSettlement(settlementId: Long) = database.withTransaction {
        val settlement = tripDao.getSettlement(settlementId) ?: return@withTransaction
        requireActive(settlement.tripId)
        tripDao.deleteSettlement(settlementId)
    }

    override suspend fun post(tripId: Long, rows: List<PostRow>): Int = database.withTransaction {
        val trip = requireActive(tripId)
        val tripExpenseIds = tripDao.getExpenses(tripId).map { it.id }.toSet()
        rows.forEach { row ->
            require(row.tripExpenseId in tripExpenseIds) { "Row is not from this trip" }
            require(row.category.isNotBlank()) { "Every posted row needs a Category" }
            require(row.amountPaise > 0) { "Posted amounts must be above zero" }
        }
        rows.forEach { row ->
            // Written exactly as a manual Expense: SyncTriggers sees the unsynced row and schedules Sync.
            val expenseId = expenseDao.insert(
                ExpenseRecord(
                    date = row.date,
                    type = TransactionType.EXPENSE,
                    category = row.category,
                    description = row.description,
                    amount = row.amountPaise / 100.0,
                    accountId = row.accountId,
                    remarks = "",
                    fromAccountId = row.accountId,
                    isSynced = false,
                    syncAction = "INSERT"
                )
            )
            tripDao.setPostedExpenseId(row.tripExpenseId, expenseId)
        }
        tripDao.updateTrip(trip.copy(status = TripRecord.STATUS_ARCHIVED, postedAt = System.currentTimeMillis()))
        rows.size
    }

    override suspend fun unarchive(tripId: Long): Int = database.withTransaction {
        val trip = tripDao.getTrip(tripId) ?: return@withTransaction 0
        var removed = 0
        tripDao.getExpenses(tripId).forEach { expense ->
            val posted = expense.postedExpenseId ?: return@forEach
            // The ordinary delete: a soft delete that Sync carries to the Sheet before removing the row.
            expenseDao.markTransactionDeletedById(posted)
            tripDao.setPostedExpenseId(expense.id, null)
            removed++
        }
        tripDao.updateTrip(trip.copy(status = TripRecord.STATUS_ACTIVE, postedAt = null))
        removed
    }

    override suspend fun addCaptureToTrip(captureId: Long, tripId: Long, expense: TripExpenseInput): Long =
        database.withTransaction {
            val id = saveExpenseInTransaction(tripId, expense)
            captureDao.markAddedToTrip(captureId)
            id
        }

    private suspend fun requireActive(tripId: Long): TripRecord {
        val trip = tripDao.getTrip(tripId) ?: throw IllegalArgumentException("No trip $tripId")
        if (trip.isArchived) throw TripArchivedException()
        return trip
    }
}
