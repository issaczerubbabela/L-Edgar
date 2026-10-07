package com.issaczerubbabel.ledgar.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.issaczerubbabel.ledgar.data.local.dao.AccountAliasDao
import com.issaczerubbabel.ledgar.data.local.dao.AccountDao
import com.issaczerubbabel.ledgar.data.local.dao.BucketBudgetDao
import com.issaczerubbabel.ledgar.data.local.dao.BudgetDao
import com.issaczerubbabel.ledgar.data.local.dao.CaptureDao
import com.issaczerubbabel.ledgar.data.local.dao.DropdownOptionDao
import com.issaczerubbabel.ledgar.data.local.dao.ExpenseDao
import com.issaczerubbabel.ledgar.data.local.dao.MerchantRuleDao
import com.issaczerubbabel.ledgar.data.local.dao.RecurringRuleDao
import com.issaczerubbabel.ledgar.data.local.dao.TripDao
import com.issaczerubbabel.ledgar.data.local.dao.UnparsedAlertDao
import com.issaczerubbabel.ledgar.data.local.entity.AccountAlias
import com.issaczerubbabel.ledgar.data.local.entity.AccountRecord
import com.issaczerubbabel.ledgar.data.local.entity.BucketCategory
import com.issaczerubbabel.ledgar.data.local.entity.Budget
import com.issaczerubbabel.ledgar.data.local.entity.BudgetBucket
import com.issaczerubbabel.ledgar.data.local.entity.BudgetCycle
import com.issaczerubbabel.ledgar.data.local.entity.CapturedTransaction
import com.issaczerubbabel.ledgar.data.local.entity.DropdownOption
import com.issaczerubbabel.ledgar.data.local.entity.ExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.MerchantRule
import com.issaczerubbabel.ledgar.data.local.entity.RecurringRule
import com.issaczerubbabel.ledgar.data.local.entity.TripExpenseRecord
import com.issaczerubbabel.ledgar.data.local.entity.TripExpenseShareRecord
import com.issaczerubbabel.ledgar.data.local.entity.TripMemberRecord
import com.issaczerubbabel.ledgar.data.local.entity.TripRecord
import com.issaczerubbabel.ledgar.data.local.entity.TripSettlementRecord
import com.issaczerubbabel.ledgar.data.local.entity.UnparsedAlert

@Database(
    entities = [
        ExpenseRecord::class,
        Budget::class,
        AccountRecord::class,
        DropdownOption::class,
        BudgetCycle::class,
        BudgetBucket::class,
        BucketCategory::class,
        CapturedTransaction::class,
        MerchantRule::class,
        AccountAlias::class,
        UnparsedAlert::class,
        TripRecord::class,
        TripMemberRecord::class,
        TripExpenseRecord::class,
        TripExpenseShareRecord::class,
        TripSettlementRecord::class,
        RecurringRule::class
    ],
    version = 24,
    exportSchema = false
)
abstract class SheetSyncDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao
    abstract fun budgetDao(): BudgetDao
    abstract fun bucketBudgetDao(): BucketBudgetDao
    abstract fun accountDao(): AccountDao
    abstract fun dropdownOptionDao(): DropdownOptionDao
    abstract fun captureDao(): CaptureDao
    abstract fun merchantRuleDao(): MerchantRuleDao
    abstract fun accountAliasDao(): AccountAliasDao
    abstract fun unparsedAlertDao(): UnparsedAlertDao
    abstract fun tripDao(): TripDao
    abstract fun recurringRuleDao(): RecurringRuleDao
}
