package com.example.localupload.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Room Database — single SQLite database for the entire app.
 *
 * Contains two tables:
 *   1. pending_photos   — selected photos awaiting upload (draft queue)
 *   2. upload_history   — permanent audit log of all upload attempts
 *
 * Database file: /data/data/com.example.localupload/databases/upload_app.db
 *
 * Persists across:
 *   - App close / reopen
 *   - Phone restart
 * Deleted only when the app is uninstalled or data is cleared.
 */
@Database(
    entities = [
        PendingPhotoEntity::class,
        UploadHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pendingPhotoDao(): PendingPhotoDao
    abstract fun uploadHistoryDao(): UploadHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Returns the singleton database instance.
         * Creates the database on first call, reuses it on subsequent calls.
         * Thread-safe via double-checked locking.
         */
        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "upload_app.db"
                )
                    .fallbackToDestructiveMigration() // recreate DB if schema changes during dev
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
