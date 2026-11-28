package com.issaczerubbabel.ledgar.di

import com.issaczerubbabel.ledgar.util.CurrentDate
import com.issaczerubbabel.ledgar.util.DeviceZoneClock
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
        /** The device clock, in whatever time zone the device is in at each read. */
        @Provides
        @Singleton
        fun provideClock(): Clock = DeviceZoneClock
    }
}
