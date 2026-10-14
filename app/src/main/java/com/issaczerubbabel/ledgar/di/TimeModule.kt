package com.issaczerubbabel.ledgar.di

import com.issaczerubbabel.ledgar.util.CurrentDate
import com.issaczerubbabel.ledgar.util.SystemCurrentDate
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {

    @Binds
    @Singleton
    abstract fun bindCurrentDate(impl: SystemCurrentDate): CurrentDate

    companion object {
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemDefaultZone()
    }
}
