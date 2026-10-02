package com.issaczerubbabel.ledgar.di

import com.issaczerubbabel.ledgar.data.local.entity.DropdownRole
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseTableTriggers
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.issaczerubbabel.ledgar.data.local.SheetSyncDatabase
import com.issaczerubbabel.ledgar.data.local.dao.AccountAliasDao
import com.issaczerubbabel.ledgar.data.local.dao.AccountDao
import com.issaczerubbabel.ledgar.data.local.dao.BucketBudgetDao
import com.issaczerubbabel.ledgar.data.local.dao.BudgetDao
import com.issaczerubbabel.ledgar.data.local.dao.CaptureDao
import com.issaczerubbabel.ledgar.data.local.migration.BucketBudgetMigration
import com.issaczerubbabel.ledgar.data.local.migration.CaptureMigration
import com.issaczerubbabel.ledgar.data.local.migration.TripMigration
import com.issaczerubbabel.ledgar.data.local.dao.DropdownOptionDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.dao.MerchantRuleDao
import com.issaczerubbabel.ledgar.data.local.dao.RecurringRuleDao
import com.issaczerubbabel.ledgar.data.local.dao.UnparsedAlertDao
import com.issaczerubbabel.ledgar.data.local.dao.TripDao
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

    internal val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE expense_records ADD COLUMN localVersion INTEGER NOT NULL DEFAULT 0")
            db.execSQL(ExpenseTableTriggers.CREATE_SQL)
        }
    }

    /** Existing rows keep a null Transaction ID: Sync links them to their Sheet row by Remote timestamp. */
    internal val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE expense_records ADD COLUMN syncId TEXT")
            db.execSQL("ALTER TABLE expense_records ADD COLUMN syncedRevision TEXT")
            db.execSQL("ALTER TABLE expense_records ADD COLUMN sheetConflictJson TEXT")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_expense_records_syncId` ON `expense_records` (`syncId`)")
            ExpenseTableTriggers.install(db)
        }
    }

    /**
     * Adds Dropdown option Stats roles (ADR-0004) and gives the defaults once. The column is only
     * added if missing: a development build numbered this migration 18 -> 19 before sync phase 2 took
     * that number.
     */
    internal val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // That development build's "version 19" had roles but not the sync columns, so add them here too.
            if ("syncId" !in columnsOf(db, "expense_records")) MIGRATION_18_19.migrate(db)
            if ("role" !in columnsOf(db, "dropdown_options")) {
                db.execSQL("ALTER TABLE dropdown_options ADD COLUMN role TEXT NOT NULL DEFAULT ''")
            }
            applyDefaultRoles(db)
        }
    }

    /**
     * Adds the auto-capture tables. Development builds of auto-capture numbered them 18 -> 19 and
     * 19 -> 20 before sync phase 2 and Stats roles took those numbers, so an install from one can sit
     * at "20" with the capture tables but without the sync columns or roles. Repair that first, the
     * same way 19 -> 20 repairs its own development builds; both steps are idempotent.
     */
    internal val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if ("syncId" !in columnsOf(db, "expense_records") || "role" !in columnsOf(db, "dropdown_options")) {
                MIGRATION_19_20.migrate(db)
            }
            CaptureMigration.createTables(db)
        }
    }

    /** Adds the local-only Trip tables (see "Trips" in CONTEXT.md). Additive and idempotent. */
    internal val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            TripMigration.createTables(db)
        }
    }

    /** Adds the `recurring_rules` table and the column linking a Transaction back to the rule that created it. */
    internal val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `recurring_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`type` TEXT NOT NULL, `category` TEXT NOT NULL, `description` TEXT NOT NULL, `amount` REAL NOT NULL, " +
                    "`accountId` INTEGER, `remarks` TEXT NOT NULL, `fromAccountId` INTEGER, `toAccountId` INTEGER, " +
                    "`frequency` TEXT NOT NULL, `interval` INTEGER NOT NULL, `anchorDay` INTEGER NOT NULL, " +
                    "`startDate` TEXT NOT NULL, `nextDate` TEXT NOT NULL, `endDate` TEXT, `remainingCount` INTEGER, " +
                    "`autoAdd` INTEGER NOT NULL, `isPaused` INTEGER NOT NULL, `createdAt` TEXT NOT NULL, " +
                    "FOREIGN KEY(`accountId`) REFERENCES `account_records`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                    "FOREIGN KEY(`fromAccountId`) REFERENCES `account_records`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL , " +
                    "FOREIGN KEY(`toAccountId`) REFERENCES `account_records`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_accountId` ON `recurring_rules` (`accountId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_fromAccountId` ON `recurring_rules` (`fromAccountId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_toAccountId` ON `recurring_rules` (`toAccountId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_recurring_rules_nextDate` ON `recurring_rules` (`nextDate`)")

            // Not a foreign key: SQLite can't add one to an existing table. Deleting a rule leaves
            // this column pointing at nothing on the Transactions it already created, which is fine:
            // nothing reads it back through a join, only to look a rule up by id.
            db.execSQL("ALTER TABLE expense_records ADD COLUMN recurringRuleId INTEGER")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expense_records_recurringRuleId` ON `expense_records` (`recurringRuleId`)")
        }
    }

    private fun columnsOf(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info($table)").use { cursor ->
            val nameColumn = cursor.getColumnIndexOrThrow("name")
            generateSequence { if (cursor.moveToNext()) cursor.getString(nameColumn) else null }.toSet()
        }

    /** Matches names case-insensitively and leaves any role the user already chose alone. */
    private fun applyDefaultRoles(db: SupportSQLiteDatabase) {
        DropdownRole.DEFAULTS.forEach { (optionType, name, role) ->
            db.execSQL(
                "UPDATE dropdown_options SET role = ? WHERE optionType = ? AND LOWER(TRIM(name)) = LOWER(?) AND role = ''",
                arrayOf<Any>(role, optionType, name)
            )
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

                applyDefaultRoles(db)
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
                ExpenseTableTriggers.install(db)
                android.util.Log.d("DatabaseModule", "Database created, seeding defaults...")
                seedDropdownDefaultsIfEmpty(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                ExpenseTableTriggers.install(db)
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
            .addMigrations(BucketBudgetMigration.MIGRATION_16_17)
            .addMigrations(MIGRATION_17_18)
            .addMigrations(MIGRATION_18_19)
            .addMigrations(MIGRATION_19_20)
            .addMigrations(MIGRATION_20_21)
            .addMigrations(MIGRATION_21_22)
            .addMigrations(MIGRATION_22_23)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .addCallback(callback)
            .build()
    }

    @Provides
    fun provideExpenseDao(db: SheetSyncDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideBudgetDao(db: SheetSyncDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideBucketBudgetDao(db: SheetSyncDatabase): BucketBudgetDao = db.bucketBudgetDao()

    @Provides
    fun provideAccountDao(db: SheetSyncDatabase): AccountDao = db.accountDao()

    @Provides
    fun provideDropdownOptionDao(db: SheetSyncDatabase): DropdownOptionDao = db.dropdownOptionDao()

    @Provides
    fun provideCaptureDao(db: SheetSyncDatabase): CaptureDao = db.captureDao()

    @Provides
    fun provideMerchantRuleDao(db: SheetSyncDatabase): MerchantRuleDao = db.merchantRuleDao()

    @Provides
    fun provideAccountAliasDao(db: SheetSyncDatabase): AccountAliasDao = db.accountAliasDao()

    @Provides
    fun provideUnparsedAlertDao(db: SheetSyncDatabase): UnparsedAlertDao = db.unparsedAlertDao()

    @Provides
    fun provideTripDao(db: SheetSyncDatabase): TripDao = db.tripDao()

    @Provides
    fun provideRecurringRuleDao(db: SheetSyncDatabase): RecurringRuleDao = db.recurringRuleDao()
}
