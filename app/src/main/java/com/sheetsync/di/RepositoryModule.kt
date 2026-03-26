package com.sheetsync.di

import com.sheetsync.data.repository.AccountRepository
import com.sheetsync.data.repository.AccountRepositoryImpl
import com.sheetsync.data.repository.BudgetRepository
import com.sheetsync.data.repository.BudgetRepositoryImpl
import com.sheetsync.data.repository.ExpenseRepository
import com.sheetsync.data.repository.ExpenseRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository

    @Binds
    @Singleton
    abstract fun bindBudgetRepository(impl: BudgetRepositoryImpl): BudgetRepository

    @Binds
    @Singleton
    abstract fun bindExpenseRepository(impl: ExpenseRepositoryImpl): ExpenseRepository
}
