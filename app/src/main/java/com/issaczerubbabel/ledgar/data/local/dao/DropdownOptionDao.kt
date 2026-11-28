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

    // ── What uses an option ───────────────────────────────────────────────────
    // Everything keeps an option by its name, as text, so these match the name ignoring case.

    /** Transactions of [type] ("Expense" or "Income") in the Category [name], leaving out deleted ones. */
    @Query(
        "SELECT COUNT(*) FROM expense_records " +
            "WHERE type = :type AND category = :name COLLATE NOCASE AND syncAction != 'DELETE'"
    )
    suspend fun countTransactionsInCategory(type: String, name: String): Int

    /** Bucket routes of the expense Category [name] in the running cycle; past cycles are history. */
    @Query(
        "SELECT COUNT(*) FROM bucket_categories WHERE category = :name COLLATE NOCASE AND cycleId = " +
            "(SELECT id FROM budget_cycles WHERE closedAt IS NULL ORDER BY startDate DESC, id DESC LIMIT 1)"
    )
    suspend fun countBucketRoutes(name: String): Int

    /** Merchant rules of [type] that file into the Category [name]; a candidate isn't a rule yet. */
    @Query(
        "SELECT COUNT(*) FROM merchant_rules " +
            "WHERE type = :type AND category = :name COLLATE NOCASE AND origin != 'CANDIDATE'"
    )
    suspend fun countMerchantRules(type: String, name: String): Int

    /** Repeat rules of [type] in the Category [name]. */
    @Query("SELECT COUNT(*) FROM recurring_rules WHERE type = :type AND category = :name COLLATE NOCASE")
    suspend fun countRepeatRules(type: String, name: String): Int

    /** Accounts in the Account group [name], hidden ones included. */
    @Query("SELECT COUNT(*) FROM account_records WHERE groupName = :name COLLATE NOCASE")
    suspend fun countAccountsInGroup(name: String): Int
}

/** What still uses a Dropdown option's name, which the confirm before Delete names. */
data class DropdownUsage(
    val transactions: Int = 0,
    val bucketRoutes: Int = 0,
    val merchantRules: Int = 0,
    val repeatRules: Int = 0,
    val accounts: Int = 0
) {
    val isUnused: Boolean get() = transactions + bucketRoutes + merchantRules + repeatRules + accounts == 0
}
