package com.issaczerubbabel.ledgar.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import kotlinx.coroutines.flow.Flow

@Dao
interface DropdownOptionDao {

    @Query("SELECT * FROM dropdown_options WHERE optionType = :optionType ORDER BY displayOrder ASC")
    fun getOptionsByType(optionType: String): Flow<List<DropdownOption>>

    @Query("SELECT * FROM dropdown_options ORDER BY optionType ASC, displayOrder ASC")
    suspend fun getAllOptionsSnapshot(): List<DropdownOption>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(option: DropdownOption): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(options: List<DropdownOption>)

    @Update
    suspend fun update(option: DropdownOption)

    @Delete
    suspend fun delete(option: DropdownOption)

    @Query("DELETE FROM dropdown_options")
    suspend fun deleteAllOptions()

    @Update
    suspend fun updateAll(options: List<DropdownOption>)

    @Transaction
    suspend fun updateOptions(options: List<DropdownOption>) {
        updateAll(options)
    }

    @Transaction
    suspend fun overwriteAllOptions(options: List<DropdownOption>) {
        deleteAllOptions()
        insertAll(options)
    }
}
