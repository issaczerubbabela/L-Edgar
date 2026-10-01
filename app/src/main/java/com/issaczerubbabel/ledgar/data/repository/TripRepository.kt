package com.issaczerubbabel.ledgar.data.repository

import com.issaczerubbabel.ledgar.data.local.entity.TripRecord
import com.issaczerubbabel.ledgar.data.local.entity.TripSettlementRecord
import com.issaczerubbabel.ledgar.trip.TripExpenseInput
import com.issaczerubbabel.ledgar.trip.TripMember
import kotlinx.coroutines.flow.Flow

/** A Trip with everything its screens need, in the shapes [com.issaczerubbabel.ledgar.trip.TripMath] takes. */
data class TripDetail(
    val trip: TripRecord,
    val members: List<TripMember>,
    val expenses: List<TripExpenseInput>,
    val settlements: List<TripSettlementRecord>,
    /** How many Trip expenses the last Post turned into Transactions. */
    val postedCount: Int = 0
) {
    val self: TripMember? get() = members.firstOrNull { it.isSelf }
}

/** One Transaction a Post will write: the user's Share of one Trip expense, as reviewed. */
data class PostRow(
    val tripExpenseId: Long,
    val date: String,
    val description: String,
    val category: String,
    val amountPaise: Long,
    val accountId: Long?
)

/** Thrown when something tries to change an Archived Trip, which is read-only until Un-archived. */
class TripArchivedException : IllegalStateException("This trip is archived")

interface TripRepository {
    fun observeTrips(): Flow<List<TripRecord>>
    fun observeActiveTrip(): Flow<TripRecord?>
    fun observeDetail(tripId: Long): Flow<TripDetail?>

    /** Creates a Trip whose first Member is the user ("You"), followed by [otherMembers]. */
    suspend fun createTrip(name: String, startDate: String, endDate: String?, otherMembers: List<String>): Long

    suspend fun addMember(tripId: Long, name: String): Long
    suspend fun updateMember(memberId: Long, name: String, upiId: String?)

    /** False when the Member still pays, shares in or settles anything on the Trip. */
    suspend fun removeMember(memberId: Long): Boolean

    /** Inserts when [expense] has id 0, else replaces it; stores each Member's Share. */
    suspend fun saveExpense(tripId: Long, expense: TripExpenseInput): Long
    suspend fun deleteExpense(expenseId: Long)

    suspend fun recordSettlement(tripId: Long, fromMemberId: Long, toMemberId: Long, amountPaise: Long, date: String): Long
    suspend fun deleteSettlement(settlementId: Long)

    /** Writes [rows] as Expense Transactions and archives the Trip, in one transaction (ADR-0008). */
    suspend fun post(tripId: Long, rows: List<PostRow>): Int

    /** Deletes the Transactions the Post created (through the normal delete, so Sync removes them) and reactivates the Trip. */
    suspend fun unarchive(tripId: Long): Int

    /** Saves [expense] on the Trip and takes the Captured transaction out of the inbox. Never creates a Transaction. */
    suspend fun addCaptureToTrip(captureId: Long, tripId: Long, expense: TripExpenseInput): Long
}
