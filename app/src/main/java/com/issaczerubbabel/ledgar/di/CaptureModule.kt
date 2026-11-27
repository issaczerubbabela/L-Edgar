package com.issaczerubbabel.ledgar.di

import android.content.Context
import com.issaczerubbabel.ledgar.capture.CaptureHistorySource
import com.issaczerubbabel.ledgar.capture.CaptureIngestor
import com.issaczerubbabel.ledgar.capture.CaptureReguesser
import com.issaczerubbabel.ledgar.capture.CaptureSettingsSource
import com.issaczerubbabel.ledgar.capture.RoomCaptureHistorySource
import com.issaczerubbabel.ledgar.capture.categorize.CategorizationPipeline
import com.issaczerubbabel.ledgar.capture.categorize.KeywordDictionary
import com.issaczerubbabel.ledgar.data.local.dao.MerchantRuleDao
import com.issaczerubbabel.ledgar.data.preferences.CapturePreferencesRepository
import com.issaczerubbabel.ledgar.data.repository.CaptureRepository
import com.issaczerubbabel.ledgar.data.repository.CaptureRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CaptureBindingsModule {

    @Binds
    @Singleton
    abstract fun bindCaptureRepository(impl: CaptureRepositoryImpl): CaptureRepository

    @Binds
    @Singleton
    abstract fun bindCaptureSettings(impl: CapturePreferencesRepository): CaptureSettingsSource

    @Binds
    abstract fun bindCaptureHistory(impl: RoomCaptureHistorySource): CaptureHistorySource

    @Binds
    abstract fun bindCaptureReguesser(impl: CaptureIngestor): CaptureReguesser
}

@Module
@InstallIn(SingletonComponent::class)
object CaptureProvidersModule {

    @Provides
    @Singleton
    fun provideKeywordDictionary(@ApplicationContext context: Context): KeywordDictionary =
        KeywordDictionary.fromJson(
            context.assets.open("merchant_keywords.json").bufferedReader().use { it.readText() }
        )

    @Provides
    @Singleton
    fun provideCategorizationPipeline(
        keywords: KeywordDictionary,
        ruleDao: MerchantRuleDao
    ): CategorizationPipeline = CategorizationPipeline(keywords) { ruleDao.get(it) }
}
