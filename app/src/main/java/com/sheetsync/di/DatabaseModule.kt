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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private fun seedDropdownDefaultsIfEmpty(db: SupportSQLiteDatabase) {
        val cursor = db.query("SELECT COUNT(*) FROM dropdown_options")
        val hasRows = cursor.use {
            it.moveToFirst() && it.getInt(0) > 0
        }
        if (hasRows) return

        fun esc(value: String): String = value.replace("'", "''")

        fun insertType(optionType: String, names: List<String>) {
            names.forEachIndexed { index, name ->
                db.execSQL(
                    """
                    INSERT INTO dropdown_options(optionType, name, displayOrder)
                    VALUES('${esc(optionType)}', '${esc(name)}', $index)
                    """.trimIndent()
                )
            }
        }

        insertType(
            optionType = "EXPENSE_CATEGORY",
            names = listOf(
                "Food & Snacks",
                "Rent",
                "Transportation",
                "Utilties",
                "Investments/Savings",
                "Amenities/Personal Care",
                "Books & Stationery",
                "Clothing",
                "Family Support",
                "Gifts",
                "Education & Courses, Events",
                "Shopping",
                "Recharge/Subscriptions",
                "Medical"
            )
        )

        insertType(
            optionType = "INCOME_CATEGORY",
            names = listOf(
                "Salary",
                "Investment Income",
                "Family Support",
                "Gift",
                "Return"
            )
        )

        insertType(
            optionType = "ACCOUNT_GROUP",
            names = listOf(
                "Cash",
                "Accounts",
                "Card",
                "Debit Card",
                "Savings",
                "Top-Up/Prepaid",
                "Investments",
                "Overdrafts",
                "Loan",
                "Insurance",
                "Others"
            )
        )

        insertType(
            optionType = "PAYMENT_MODE",
            names = listOf(
                "UPI",
                "Cash",
                "Debit Card/Credit Card",
                "Bank Transfer/Net Banking"
            )
        )
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SheetSyncDatabase {
        val callback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                seedDropdownDefaultsIfEmpty(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                seedDropdownDefaultsIfEmpty(db)
            }
        }

        return Room.databaseBuilder(context, SheetSyncDatabase::class.java, "sheetsync.db")
            .fallbackToDestructiveMigration()
            .addCallback(callback)
            .build()
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
