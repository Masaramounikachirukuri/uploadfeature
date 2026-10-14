package com.example.localupload.data.repository

import android.net.Uri
import com.example.localupload.data.local.LocalDataSource
import com.example.localupload.data.remote.RemoteDataSource
import com.example.localupload.domain.model.Photo
import com.example.localupload.domain.model.UploadHistory
import com.example.localupload.domain.model.UploadResponse
import com.example.localupload.domain.repository.PhotoRepository

class PhotoRepositoryImpl(
    private val remote: RemoteDataSource,
    private val local: LocalDataSource
) : PhotoRepository {

    // -------------------------------------------------------------------------
    // Picsum Gallery
    // -------------------------------------------------------------------------

    override suspend fun fetchPhotos(
        page: Int,
        limit: Int
    ): List<Photo> {
        return remote.fetchPhotos(
            page = page,
            limit = limit
        )
    }

    /**
     * Downloads selected Picsum photos to local cache
     * and returns local URIs.
     */
    override suspend fun downloadPhotosToCache(
        photos: List<Photo>
    ): List<Uri> {
        return remote.downloadPhotosToCache(
            photos = photos
        )
    }

    // -------------------------------------------------------------------------
    // Upload
    // -------------------------------------------------------------------------

    override suspend fun uploadPhotos(
        uris: List<Uri>
    ): UploadResponse {
        return remote.uploadPhotos(
            uris = uris
        )
    }

    // -------------------------------------------------------------------------
    // Upload History
    // -------------------------------------------------------------------------

    override fun getUploadHistory(): List<UploadHistory> {
        return local.getAll()
    }

    override fun getPendingPhotos(): List<UploadHistory> {
        return local.getPending()
    }

    override fun addToHistory(
        item: UploadHistory
    ) {
        local.insert(item)
    }

    override fun updateHistory(
        item: UploadHistory
    ) {
        local.update(item)
    }

    override fun deleteHistory(
        id: String
    ) {
        local.delete(id)
    }

    override fun clearHistory() {
        local.clearAll()
    }
}