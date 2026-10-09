package com.example.localupload.data.local

import com.example.localupload.database.AppDatabase
import com.example.localupload.database.PendingPhotoEntity
import com.example.localupload.database.UploadHistoryEntity
import com.example.localupload.domain.model.Photo
import com.example.localupload.domain.model.UploadHistory
import com.example.localupload.domain.model.UploadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * LocalDataSource — the Room abstraction in the Data layer.
 *
 * Responsibilities:
 *   1. Wraps PendingPhotoDao and UploadHistoryDao (Room)
 *   2. Maps between Room entities (PendingPhotoEntity, UploadHistoryEntity)
 *      and Domain models (Photo, UploadHistory)
 *   3. Exposes only Domain types to PhotoRepositoryImpl — no Room types leak upward
 *
 * Mapping rules:
 *   PendingPhotoEntity ↔ Photo
 *   UploadHistoryEntity ↔ UploadHistory (status string ↔ UploadStatus enum)
 */
class LocalDataSource(db: AppDatabase) {

    private val pendingDao = db.pendingPhotoDao()
    private val historyDao = db.uploadHistoryDao()

    // ── Pending Photos ────────────────────────────────────────────────────────

    fun getPendingPhotos(): Flow<List<Photo>> =
        pendingDao.getAllPending().map { entities -> entities.map { it.toDomain() } }

    suspend fun getPendingPhotosOnce(): List<Photo> =
        pendingDao.getAllPendingOnce().map { it.toDomain() }

    suspend fun addPendingPhotos(photos: List<Photo>) {
        pendingDao.insertAll(photos.map { it.toEntity() })
    }

    suspend fun removePendingPhoto(id: Int) {
        pendingDao.deleteById(id)
    }

    suspend fun clearPendingPhotos() {
        pendingDao.clearAll()
    }

    // ── Upload History ────────────────────────────────────────────────────────

    fun getUploadHistory(): Flow<List<UploadHistory>> =
        historyDao.getAllHistory().map { entities -> entities.map { it.toDomain() } }

    suspend fun saveUploadHistory(records: List<UploadHistory>) {
        historyDao.insertAll(records.map { it.toEntity() })
    }

    suspend fun deleteHistoryRecord(id: Int) {
        historyDao.deleteById(id)
    }

    suspend fun clearAllHistory() {
        historyDao.clearAll()
    }

    // ── Mapping extensions ────────────────────────────────────────────────────

    private fun PendingPhotoEntity.toDomain() = Photo(
        id          = id,
        uriString   = uriString,
        mimeType    = mimeType,
        displayName = displayName,
        addedAt     = addedAt
    )

    private fun Photo.toEntity() = PendingPhotoEntity(
        id          = id,
        uriString   = uriString,
        mimeType    = mimeType,
        displayName = displayName,
        addedAt     = addedAt
    )

    private fun UploadHistoryEntity.toDomain() = UploadHistory(
        id            = id,
        uriString     = uriString,
        displayName   = displayName,
        mimeType      = mimeType,
        status        = UploadStatus.fromDbString(status),
        serverMessage = serverMessage,
        uploadedAt    = uploadedAt
    )

    private fun UploadHistory.toEntity() = UploadHistoryEntity(
        id            = id,
        uriString     = uriString,
        displayName   = displayName,
        mimeType      = mimeType,
        status        = status.toDbString(),
        serverMessage = serverMessage,
        uploadedAt    = uploadedAt
    )
}
