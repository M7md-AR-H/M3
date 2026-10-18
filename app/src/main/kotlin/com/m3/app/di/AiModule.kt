package com.m3.app.di

import android.content.Context
import android.os.storage.StorageManager
import com.m3.ai.bench.BenchmarkRunner
import com.m3.ai.bench.readProcessMemory
import com.m3.ai.bench.resetProcessPeakMemory
import com.m3.ai.bench.shapesTestImagePng
import com.m3.ai.engine.LiteRtLmEngine
import com.m3.ai.model.ModelStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {
    /** Models live in app-private storage that is never backed up (ADR 0002, ADR 0004). */
    @Provides
    @Singleton
    fun provideModelStore(@ApplicationContext context: Context): ModelStore {
        val storage = context.getSystemService(StorageManager::class.java)
        return ModelStore(File(context.noBackupFilesDir, "models")) { directory ->
            // Includes cached data the system can clear to make room.
            // If the query itself fails, let the copy try; a full disk then fails the write.
            runCatching { storage.getAllocatableBytes(storage.getUuidForPath(directory)) }
                .getOrDefault(Long.MAX_VALUE)
        }
    }

    @Provides
    fun provideBenchmarkRunner(@ApplicationContext context: Context, store: ModelStore): BenchmarkRunner =
        BenchmarkRunner(
            store = store,
            newEngine = ::LiteRtLmEngine,
            testImagePng = { shapesTestImagePng() },
            memory = { readProcessMemory() },
            resetPeakMemory = { resetProcessPeakMemory() },
            cacheDir = File(context.cacheDir, "llm").apply { mkdirs() },
        )
}
