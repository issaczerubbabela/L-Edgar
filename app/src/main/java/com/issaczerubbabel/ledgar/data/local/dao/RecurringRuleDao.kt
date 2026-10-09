package com.issaczerubbabel.ledgar.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import kotlinx.coroutines.flow.Flow

@Dao
interface RecurringRuleDao {

    @Insert
    suspend fun insert(rule: RecurringRule): Long

    @Update
    suspend fun update(rule: RecurringRule)

    @Delete
    suspend fun delete(rule: RecurringRule)

    @Query("SELECT * FROM recurring_rules WHERE id = :id")
    suspend fun getById(id: Long): RecurringRule?

    @Query("SELECT * FROM recurring_rules ORDER BY nextDate ASC, id ASC")
    fun observeAll(): Flow<List<RecurringRule>>

    /** Rules due to materialize on or before [today]: not paused, and set to add automatically. */
    @Query("SELECT id FROM recurring_rules WHERE isPaused = 0 AND autoAdd = 1 AND nextDate <= :today")
    suspend fun getDueAutoRuleIds(today: String): List<Long>
}
