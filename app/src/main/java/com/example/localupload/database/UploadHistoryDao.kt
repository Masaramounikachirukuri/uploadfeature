package com.example.localupload.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the upload_history table.
 *
 * This table is a permanent audit log of every upload attempt.
 * It is written after each upload (success or failure) and never cleared automatically.
 * The PhotoHistoryScreen reads from this table to show the user their upload history.
 */
@Dao
interface UploadHistoryDao {

    /**
     * Insert a single upload history record.
     * Called once per photo after the upload attempt completes (success or fail).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: UploadHistoryEntity)

    /**
     * Insert multiple history records in a single transaction.
     * Called after a batch upload — one record per photo.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<UploadHistoryEntity>)

    /**
     * Get all upload history records, newest first.
     * Returns a Flow — the History screen automatically refreshes when new records are added.
     */
    @Query("SELECT * FROM upload_history ORDER BY uploaded_at DESC")
    fun getAllHistory(): Flow<List<UploadHistoryEntity>>

    /**
     * Get count of successful uploads.
     */
    @Query("SELECT COUNT(*) FROM upload_history WHERE status = 'success'")
    suspend fun getSuccessCount(): Int

    /**
     * Get count of failed uploads.
     */
    @Query("SELECT COUNT(*) FROM upload_history WHERE status = 'failed'")
    suspend fun getFailedCount(): Int

    /**
     * Delete a single history record by ID (user manually removes from history).
     */
    @Query("DELETE FROM upload_history WHERE id = :id")
    suspend fun deleteById(id: Int)

    /**
     * Clear all upload history records.
     */
    @Query("DELETE FROM upload_history")
    suspend fun clearAll()
}
