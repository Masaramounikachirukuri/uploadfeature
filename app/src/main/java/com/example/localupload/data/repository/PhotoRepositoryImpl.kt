package com.example.localupload.data.repository

import com.example.localupload.data.local.LocalDataSource
import com.example.localupload.data.remote.PicsumRemoteDataSource
import com.example.localupload.data.remote.RemoteDataSource
import com.example.localupload.domain.model.Photo
import com.example.localupload.domain.model.PicsumPhoto
import com.example.localupload.domain.model.UploadHistory
import com.example.localupload.domain.model.UploadResult
import com.example.localupload.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.Flow

class PhotoRepositoryImpl(
    private val localDataSource: LocalDataSource,
    private val remoteDataSource: RemoteDataSource,
    private val picsumRemoteDataSource: PicsumRemoteDataSource
) : PhotoRepository {

    override fun getPendingPhotos(): Flow<List<Photo>> {
        return localDataSource.getPendingPhotos()
    }

    override suspend fun getPendingPhotosOnce(): List<Photo> {
        return localDataSource.getPendingPhotosOnce()
    }

    override suspend fun addPendingPhotos(
        photos: List<Photo>
    ) {
        localDataSource.addPendingPhotos(photos)
    }

    override suspend fun removePendingPhoto(
        id: Int
    ) {
        localDataSource.removePendingPhoto(id)
    }

    override suspend fun clearPendingPhotos() {
        localDataSource.clearPendingPhotos()
    }

    override suspend fun getPicsumPhotos(
        page: Int,
        limit: Int
    ): List<PicsumPhoto> {
        return picsumRemoteDataSource.getPhotos(
            page = page,
            limit = limit
        )
    }

    override suspend fun downloadPicsumPhotos(
        photos: List<PicsumPhoto>
    ): List<Photo> {
        return picsumRemoteDataSource.downloadPhotos(
            photos = photos
        )
    }

    override suspend fun uploadPhotos(
        photos: List<Photo>
    ): UploadResult {
        return remoteDataSource.uploadPhotos(
            photos = photos
        )
    }

    override fun getUploadHistory(): Flow<List<UploadHistory>> {
        return localDataSource.getUploadHistory()
    }

    override suspend fun saveUploadHistory(
        records: List<UploadHistory>
    ) {
        localDataSource.saveUploadHistory(records)
    }

    override suspend fun deleteHistoryRecord(
        id: Int
    ) {
        localDataSource.deleteHistoryRecord(id)
    }

    override suspend fun clearAllHistory() {
        localDataSource.clearAllHistory()
    }
}