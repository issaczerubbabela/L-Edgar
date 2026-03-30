package com.sheetsync.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sheetsync.data.local.SheetSyncDatabase
import com.sheetsync.data.local.dao.AccountDao
import com.sheetsync.data.local.dao.BudgetDao
import com.sheetsync.data.local.dao.DropdownOptionDao
import com.sheetsync.data.local.dao.ExpenseDao
import com.sheetsync.data.local.entity.DropdownOption
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SheetSyncDatabase {
        var database: SheetSyncDatabase? = null

        val callback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                val dropdownOptionDao = database?.dropdownOptionDao() ?: return

                val expenseCategories = listOf(
                    "Food",
                    "Shopping",
                    "Transport",
                    "Bills",
                    "Entertainment"
                ).mapIndexed { index, name ->
                    DropdownOption(
                        optionType = "EXPENSE_CATEGORY",
                        name = name,
                        displayOrder = index
                    )
                }

                val accountGroups = listOf(
                    "Cash",
                    "Accounts",
                    "Card",
                    "Debit Card",
                    "Savings"
                ).mapIndexed { index, name ->
                    DropdownOption(
                        optionType = "ACCOUNT_GROUP",
                        name = name,
                        displayOrder = index
                    )
                }

                val paymentModes = listOf(
                    "Cash",
                    "UPI",
                    "Credit Card",
                    "Debit Card",
                    "Net Banking"
                ).mapIndexed { index, name ->
                    DropdownOption(
                        optionType = "PAYMENT_MODE",
                        name = name,
                        displayOrder = index
                    )
                }

                kotlinx.coroutines.runBlocking {
                    dropdownOptionDao.insertAll(expenseCategories + accountGroups + paymentModes)
                }
            }
        }

        database = Room.databaseBuilder(context, SheetSyncDatabase::class.java, "sheetsync.db")
            .fallbackToDestructiveMigration()
            .addCallback(callback)
            .build()

        return database
    }

    @Provides
    fun provideExpenseDao(db: SheetSyncDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideBudgetDao(db: SheetSyncDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideAccountDao(db: SheetSyncDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideDropdownOptionDao(db: SheetSyncDatabase): DropdownOptionDao = db.dropdownOptionDao()
}
