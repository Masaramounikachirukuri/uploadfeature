package com.example.localupload.domain.repository

import android.net.Uri
import com.example.localupload.domain.model.Photo
import com.example.localupload.domain.model.UploadHistory
import com.example.localupload.domain.model.UploadResponse

/**
 * Defines the contract for Picsum gallery loading, remote photo caching,
 * local photo uploading, and upload-history operations.
 */
interface PhotoRepository {

    // -------------------------------------------------------------------------
    // Picsum gallery
    // -------------------------------------------------------------------------

    /**
     * Fetches a paginated list of photos from the Picsum API.
     */
    suspend fun fetchPhotos(
        page: Int = 1,
        limit: Int = 30
    ): List<Photo>

    /**
     * Downloads selected Picsum photos into the application's cache directory.
     *
     * Picsum photos contain remote web URLs. UploadWorker expects local URIs,
     * so selected remote photos must be downloaded before starting the upload.
     *
     * @param photos selected Picsum photos
     * @return local cached URIs that UploadWorker can read
     */
    suspend fun downloadPhotosToCache(
        photos: List<Photo>
    ): List<Uri>

    // -------------------------------------------------------------------------
    // Upload
    // -------------------------------------------------------------------------

    /**
     * Uploads local photo URIs to the configured server.
     *
     * The URIs may come from:
     * - Camera
     * - Android Photo Picker
     * - Storage document picker
     * - Picsum images downloaded into the app cache
     */
    suspend fun uploadPhotos(
        uris: List<Uri>
    ): UploadResponse

    // -------------------------------------------------------------------------
    // Upload history
    // -------------------------------------------------------------------------

    /**
     * Returns all upload-history records, with the most recent records first.
     */
    fun getUploadHistory(): List<UploadHistory>

    /**
     * Returns records waiting for upload or retry.
     */
    fun getPendingPhotos(): List<UploadHistory>

    /**
     * Adds an upload-history record.
     */
    fun addToHistory(
        item: UploadHistory
    )

    /**
     * Updates an existing upload-history record.
     */
    fun updateHistory(
        item: UploadHistory
    )

    /**
     * Deletes one upload-history record.
     */
    fun deleteHistory(
        id: String
    )

    /**
     * Deletes all upload-history records.
     */
    fun clearHistory()
}
