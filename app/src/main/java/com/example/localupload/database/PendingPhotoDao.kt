package com.example.localupload.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for the pending_photos table.
 *
 * This table is the "draft queue" — photos the user has selected but not yet uploaded.
 * URIs in this table have persistable permissions granted via takePersistableUriPermission().
 *
 * Lifecycle:
 *   INSERT  → when user picks photos from Photo Picker
 *   DELETE  → when upload succeeds (clearAll) or user removes a photo (deleteById)
 */
@Dao
interface PendingPhotoDao {

    /**
     * Insert one photo into the pending queue.
     * IGNORE on conflict — prevents duplicate URIs if user re-picks the same photo.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(photo: PendingPhotoEntity)

    /**
     * Insert multiple photos at once.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(photos: List<PendingPhotoEntity>)

    /**
     * Returns all pending photos as a Flow — UI automatically updates
     * when photos are added or removed from the queue.
     */
    @Query("SELECT * FROM pending_photos ORDER BY added_at ASC")
    fun getAllPending(): Flow<List<PendingPhotoEntity>>

    /**
     * Returns a one-shot snapshot of all pending photos (for upload).
     */
    @Query("SELECT * FROM pending_photos ORDER BY added_at ASC")
    suspend fun getAllPendingOnce(): List<PendingPhotoEntity>

    /**
     * Remove a single photo from the pending queue (user removed it manually).
     */
    @Query("DELETE FROM pending_photos WHERE id = :id")
    suspend fun deleteById(id: Int)

    /**
     * Clear the entire pending queue after a successful upload.
     */
    @Query("DELETE FROM pending_photos")
    suspend fun clearAll()

    /**
     * Returns the count of pending photos (used to show badge).
     */
    @Query("SELECT COUNT(*) FROM pending_photos")
    suspend fun getCount(): Int
}
