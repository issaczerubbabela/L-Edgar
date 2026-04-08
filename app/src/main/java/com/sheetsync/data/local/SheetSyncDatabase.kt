package com.sheetsync.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sheetsync.data.local.dao.AccountDao
import com.sheetsync.data.local.dao.BudgetDao
import com.sheetsync.data.local.dao.DropdownOptionDao
import com.sheetsync.data.local.dao.ExpenseDao
import com.sheetsync.data.local.entity.AccountRecord
import com.sheetsync.data.local.entity.Budget
import com.sheetsync.data.local.entity.DropdownOption
import com.sheetsync.data.local.entity.ExpenseRecord

@Database(
    entities = [ExpenseRecord::class, Budget::class, AccountRecord::class, DropdownOption::class],
    version = 12,
    exportSchema = false
)
abstract class SheetSyncDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun accountDao(): AccountDao
    abstract fun dropdownOptionDao(): DropdownOptionDao
}
