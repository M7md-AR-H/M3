package com.m3.app.di

import com.m3.core.designsystem.config.UiConfigStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UiConfigModule {
    @Provides
    @Singleton
    fun provideUiConfigStore(): UiConfigStore = UiConfigStore()
}
