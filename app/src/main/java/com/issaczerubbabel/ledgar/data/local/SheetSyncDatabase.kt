package com.issaczerubbabel.ledgar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.issaczerubbabel.ledgar.data.local.dao.AccountDao
import com.issaczerubbabel.ledgar.data.local.dao.BudgetDao
import com.issaczerubbabel.ledgar.data.local.dao.DropdownOptionDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.Budget
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord

@Database(
    entities = [ExpenseRecord::class, Budget::class, AccountRecord::class, DropdownOption::class],
    version = 14,
    exportSchema = false
)
abstract class SheetSyncDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun accountDao(): AccountDao
    abstract fun dropdownOptionDao(): DropdownOptionDao
}
