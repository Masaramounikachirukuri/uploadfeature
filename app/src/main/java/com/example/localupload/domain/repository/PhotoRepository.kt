package com.example.localupload.domain.repository

import com.example.localupload.domain.model.Photo
import com.example.localupload.domain.model.PicsumPhoto
import com.example.localupload.domain.model.UploadHistory
import com.example.localupload.domain.model.UploadResult
import kotlinx.coroutines.flow.Flow

interface PhotoRepository {

    fun getPendingPhotos(): Flow<List<Photo>>

    suspend fun getPendingPhotosOnce(): List<Photo>

    suspend fun addPendingPhotos(
        photos: List<Photo>
    )

    suspend fun removePendingPhoto(
        id: Int
    )

    suspend fun clearPendingPhotos()

    suspend fun getPicsumPhotos(
        page: Int,
        limit: Int
    ): List<PicsumPhoto>

    suspend fun downloadPicsumPhotos(
        photos: List<PicsumPhoto>
    ): List<Photo>

    suspend fun uploadPhotos(
        photos: List<Photo>
    ): UploadResult

    fun getUploadHistory(): Flow<List<UploadHistory>>

    suspend fun saveUploadHistory(
        records: List<UploadHistory>
    )

    suspend fun deleteHistoryRecord(
        id: Int
    )

    suspend fun clearAllHistory()
}