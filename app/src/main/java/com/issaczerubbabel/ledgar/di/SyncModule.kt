package com.issaczerubbabel.ledgar.di

import com.issaczerubbabel.ledgar.sync.RoomTransactionSyncStore
import com.issaczerubbabel.ledgar.sync.TransactionSyncStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    abstract fun bindTransactionSyncStore(impl: RoomTransactionSyncStore): TransactionSyncStore

    companion object {
        @Provides
        fun provideClock(): Clock = Clock.systemDefaultZone()
    }
}
