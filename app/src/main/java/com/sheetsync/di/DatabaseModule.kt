package com.sheetsync.di

import android.content.Context
import androidx.room.Room
import com.sheetsync.data.local.SheetSyncDatabase
import com.sheetsync.data.local.dao.AccountDao
import com.sheetsync.data.local.dao.BudgetDao
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SheetSyncDatabase =
        Room.databaseBuilder(context, SheetSyncDatabase::class.java, "sheetsync.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideExpenseDao(db: SheetSyncDatabase): ExpenseDao = db.expenseDao()

    @Provides
    fun provideBudgetDao(db: SheetSyncDatabase): BudgetDao = db.budgetDao()

    @Provides
    fun provideAccountDao(db: SheetSyncDatabase): AccountDao = db.accountDao()
}
