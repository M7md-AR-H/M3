package com.m3.app.di

import android.content.Context
import android.os.SystemClock
import com.m3.core.data.ChatRepository
import com.m3.core.data.PlanOverridesRepository
import com.m3.core.data.ProfileRepository
import com.m3.core.data.TrainingSetupRepository
import com.m3.core.data.UiConfigRepository
import com.m3.core.data.db.DatabaseOpener
import com.m3.core.security.AppLock
import com.m3.core.security.DatabasePassphrase
import com.m3.core.security.TinkSecretCipher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Year
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Lives as long as the app process; for work that must outlive any screen (e.g. saving the UI config). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideAppLock(): AppLock = AppLock(clock = SystemClock::elapsedRealtime)

    /** Keystore → Tink → passphrase → SQLCipher, all on the opener's IO thread (ADR 0004). */
    @Provides
    @Singleton
    fun provideDatabaseOpener(@ApplicationContext context: Context): DatabaseOpener =
        DatabaseOpener(
            context = context,
            passphrase = {
                DatabasePassphrase(
                    file = File(context.noBackupFilesDir, "db.key"),
                    cipher = TinkSecretCipher(context),
                ).getOrCreate()
            },
        )

    @Provides
    @Singleton
    fun provideUiConfigRepository(opener: DatabaseOpener) = UiConfigRepository(opener)

    @Provides
    @Singleton
    fun provideProfileRepository(opener: DatabaseOpener) = ProfileRepository(opener, currentYear = { Year.now().value })

    @Provides
    @Singleton
    fun provideTrainingSetupRepository(opener: DatabaseOpener) = TrainingSetupRepository(opener)

    @Provides
    @Singleton
    fun provideChatRepository(opener: DatabaseOpener) = ChatRepository(opener)

    @Provides
    @Singleton
    fun providePlanOverridesRepository(opener: DatabaseOpener) = PlanOverridesRepository(opener)

    @Provides
    @Singleton
    fun provideWorkoutRepository(opener: DatabaseOpener) = com.m3.core.data.WorkoutRepository(opener)

    @Provides
    @Singleton
    fun provideStepRepository(opener: DatabaseOpener) = com.m3.core.data.StepRepository(opener)
}
