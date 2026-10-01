package com.issaczerubbabel.ledgar.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.issaczerubbabel.ledgar.data.local.entity.UnparsedAlert
import kotlinx.coroutines.flow.Flow

@Dao
interface UnparsedAlertDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(alert: UnparsedAlert): Long

    @Query("SELECT * FROM unparsed_alerts ORDER BY capturedAt DESC, id DESC")
    fun observeAll(): Flow<List<UnparsedAlert>>

    @Query("SELECT COUNT(*) FROM unparsed_alerts")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM unparsed_alerts ORDER BY capturedAt DESC, id DESC")
    suspend fun getAllSnapshot(): List<UnparsedAlert>

    @Query("DELETE FROM unparsed_alerts WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM unparsed_alerts WHERE capturedAt < :cutoff")
    suspend fun purgeOlderThan(cutoff: Long)

    @Query("DELETE FROM unparsed_alerts")
    suspend fun deleteAll()
}
