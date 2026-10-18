package com.m3.core.data.db

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * Opens the SQLCipher database once, off the main thread, on first use (ADR 0004).
 *
 * [passphrase] is called once. Its array is handed to SQLCipher, which keeps it to open further
 * connections (WAL readers), so it is deliberately not zeroed (see README decision log).
 *
 * There is no destructive fallback: if the database can't be opened or migrated, this throws and
 * nothing is deleted.
 */
class DatabaseOpener(
    private val context: Context,
    private val passphrase: () -> ByteArray,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val mutex = Mutex()

    @Volatile
    private var database: M3Database? = null

    /** The open runs to completion even if the caller is cancelled, so a finished open is never lost or leaked. */
    suspend fun get(): M3Database = database ?: mutex.withLock {
        database ?: withContext(dispatcher + NonCancellable) { open().also { database = it } }
    }

    private fun open(): M3Database {
        loadSqlCipher()
        val db = Room.databaseBuilder(context.applicationContext, M3Database::class.java, DATABASE_NAME)
            .openHelperFactory(SupportOpenHelperFactory(passphrase()))
            .build()
        try {
            // Open now so a wrong key or a failed migration surfaces here, not in some later query.
            db.openHelper.writableDatabase
        } catch (e: Exception) {
            db.close()
            throw e
        }
        return db
    }

    companion object {
        const val DATABASE_NAME = "m3.db"

        @Volatile
        private var libraryLoaded = false

        @Synchronized
        fun loadSqlCipher() {
            if (!libraryLoaded) {
                System.loadLibrary("sqlcipher")
                libraryLoaded = true
            }
        }
    }
}
