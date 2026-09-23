package com.issaczerubbabel.ledgar.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.dao.AccountDao
import com.issaczerubbabel.ledgar.data.local.dao.BudgetDao
import com.issaczerubbabel.ledgar.data.local.dao.DropdownOptionDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE account_records ADD COLUMN displayOrder INTEGER NOT NULL DEFAULT 0")
            db.execSQL(
                """
                UPDATE account_records
                SET displayOrder = (
                    SELECT COUNT(*) FROM account_records a2
                    WHERE a2.id < account_records.id
                )
                """.trimIndent()
            )
        }
    }

    private val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE account_records ADD COLUMN description TEXT")
            db.execSQL("ALTER TABLE account_records ADD COLUMN includeInTotals INTEGER NOT NULL DEFAULT 1")
        }
    }

    private val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE expense_records ADD COLUMN isBookmarked INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE account_records ADD COLUMN initialBalanceDate TEXT NOT NULL DEFAULT '1970-01-01'")
        }
    }

    private val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE expense_records ADD COLUMN toAccountName TEXT")
        }
    }

    private val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE expense_records ADD COLUMN accountName TEXT")
            db.execSQL("ALTER TABLE expense_records ADD COLUMN fromAccountName TEXT")
        }
    }

    private fun seedDropdownDefaultsIfEmpty(db: SupportSQLiteDatabase) {
        try {
            // Check if dropdown_options table has data
            val cursor = db.query("SELECT COUNT(*) FROM dropdown_options")
            val hasRows = cursor.use {
                it.moveToFirst() && it.getInt(0) > 0
            }
            if (hasRows) return

            // Use a transaction for atomic insertion
            db.beginTransaction()
            try {
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

                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        } catch (e: Exception) {
            android.util.Log.e("DatabaseModule", "Failed to seed dropdown defaults", e)
            // Don't rethrow - let the app continue even if seeding fails
            // Users can manually add dropdown options in settings
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SheetSyncDatabase {
        val callback = object : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                android.util.Log.d("DatabaseModule", "Database created, seeding defaults...")
                seedDropdownDefaultsIfEmpty(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Seed defaults on first open as a fallback (in case onCreate wasn't called due to migrations)
                // This is safe because seedDropdownDefaultsIfEmpty checks if data already exists
                seedDropdownDefaultsIfEmpty(db)
            }
        }

        return Room.databaseBuilder(context, SheetSyncDatabase::class.java, "sheetsync.db")
            .addMigrations(MIGRATION_10_11)
            .addMigrations(MIGRATION_11_12)
            .addMigrations(MIGRATION_12_13)
            .addMigrations(MIGRATION_13_14)
            .addMigrations(MIGRATION_14_15)
            .addMigrations(MIGRATION_15_16)
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
