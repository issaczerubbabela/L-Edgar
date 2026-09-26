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
        AccountAlias::class
    ],
    version = 19,
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
}
