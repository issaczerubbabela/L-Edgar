package com.issaczerubbabel.ledgar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.issaczerubbabel.ledgar.data.local.entity.TripExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.TripExpenseShareRecord
import com.issaczerubbabel.ledgar.data.local.entity.TripMemberRecord
import com.issaczerubbabel.ledgar.data.local.entity.TripRecord
import com.issaczerubbabel.ledgar.data.local.entity.TripSettlementRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {

    @Query("SELECT * FROM trips ORDER BY CASE status WHEN 'ACTIVE' THEN 0 ELSE 1 END, startDate DESC, id DESC")
    fun observeTrips(): Flow<List<TripRecord>>

    @Query("SELECT * FROM trips WHERE status = 'ACTIVE' ORDER BY startDate DESC, id DESC LIMIT 1")
    fun observeActiveTrip(): Flow<TripRecord?>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    fun observeTrip(id: Long): Flow<TripRecord?>

    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1")
    suspend fun getTrip(id: Long): TripRecord?

    @Insert
    suspend fun insertTrip(trip: TripRecord): Long

    @Update
    suspend fun updateTrip(trip: TripRecord)

    @Query("SELECT * FROM trip_members WHERE tripId = :tripId ORDER BY isSelf DESC, displayOrder, id")
    fun observeMembers(tripId: Long): Flow<List<TripMemberRecord>>

    @Query("SELECT * FROM trip_members WHERE tripId = :tripId ORDER BY isSelf DESC, displayOrder, id")
    suspend fun getMembers(tripId: Long): List<TripMemberRecord>

    @Query("SELECT * FROM trip_members WHERE id = :id LIMIT 1")
    suspend fun getMember(id: Long): TripMemberRecord?

    @Insert
    suspend fun insertMember(member: TripMemberRecord): Long

    @Update
    suspend fun updateMember(member: TripMemberRecord)

    @Query("DELETE FROM trip_members WHERE id = :id")
    suspend fun deleteMember(id: Long)

    @Query(
        """
        SELECT (SELECT COUNT(*) FROM trip_expenses WHERE payerMemberId = :memberId)
             + (SELECT COUNT(*) FROM trip_expense_shares WHERE memberId = :memberId)
             + (SELECT COUNT(*) FROM trip_settlements WHERE fromMemberId = :memberId OR toMemberId = :memberId)
        """
    )
    suspend fun countMemberUses(memberId: Long): Int

    @Query("SELECT * FROM trip_expenses WHERE tripId = :tripId ORDER BY date, id")
    fun observeExpenses(tripId: Long): Flow<List<TripExpenseRecord>>

    @Query("SELECT * FROM trip_expenses WHERE tripId = :tripId ORDER BY date, id")
    suspend fun getExpenses(tripId: Long): List<TripExpenseRecord>

    @Query("SELECT * FROM trip_expenses WHERE id = :id LIMIT 1")
    suspend fun getExpense(id: Long): TripExpenseRecord?

    @Insert
    suspend fun insertExpense(expense: TripExpenseRecord): Long

    @Update
    suspend fun updateExpense(expense: TripExpenseRecord)

    @Query("DELETE FROM trip_expenses WHERE id = :id")
    suspend fun deleteExpense(id: Long)

    @Query("UPDATE trip_expenses SET postedExpenseId = :postedExpenseId WHERE id = :id")
    suspend fun setPostedExpenseId(id: Long, postedExpenseId: Long?)

    @Query("SELECT s.* FROM trip_expense_shares s INNER JOIN trip_expenses e ON e.id = s.expenseId WHERE e.tripId = :tripId")
    fun observeShares(tripId: Long): Flow<List<TripExpenseShareRecord>>

    @Query("SELECT * FROM trip_expense_shares WHERE expenseId = :expenseId")
    suspend fun getSharesFor(expenseId: Long): List<TripExpenseShareRecord>

    @Insert
    suspend fun insertShares(shares: List<TripExpenseShareRecord>)

    @Query("DELETE FROM trip_expense_shares WHERE expenseId = :expenseId")
    suspend fun deleteSharesFor(expenseId: Long)

    @Query("SELECT * FROM trip_settlements WHERE tripId = :tripId ORDER BY id")
    fun observeSettlements(tripId: Long): Flow<List<TripSettlementRecord>>

    @Query("SELECT * FROM trip_settlements WHERE id = :id LIMIT 1")
    suspend fun getSettlement(id: Long): TripSettlementRecord?

    @Insert
    suspend fun insertSettlement(settlement: TripSettlementRecord): Long

    @Query("DELETE FROM trip_settlements WHERE id = :id")
    suspend fun deleteSettlement(id: Long)
}
