package com.sheetsync.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sheetsync.data.local.dao.ExpenseDao
import com.sheetsync.data.local.entity.ExpenseRecord

@Database(entities = [ExpenseRecord::class], version = 1, exportSchema = false)
abstract class SheetSyncDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
}
