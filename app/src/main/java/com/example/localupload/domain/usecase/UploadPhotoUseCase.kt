package com.example.localupload.domain.usecase

import com.example.localupload.domain.model.Photo
import com.example.localupload.domain.model.UploadError
import com.example.localupload.domain.model.UploadHistory
import com.example.localupload.domain.model.UploadResult
import com.example.localupload.domain.model.UploadStatus
import com.example.localupload.domain.repository.PhotoRepository

/**
 * UploadPhotoUseCase — orchestrates the full upload flow.
 *
 * Steps:
 *   1. Fetch pending photos from the repository
 *   2. Validate with PhotoValidationRules
 *   3. Upload photos ONE BY ONE, reporting progress via [onProgress] after each
 *   4. Save history records (success or failure) via the repository
 *   5. On success — clear the pending queue
 *   6. Return typed UploadResult to the caller (ViewModel or Worker)
 *
 * [onProgress] — optional lambda called after each photo upload attempt:
 *   (uploadedSoFar: Int, total: Int) → Unit
 *   This drives the deterministic LinearProgressIndicator in PhotoPickerScreen.
 *
 * No Android, Room, or Retrofit import — pure Domain logic.
 */
class UploadPhotoUseCase(
    private val repository: PhotoRepository,
    private val validationRules: PhotoValidationRules = PhotoValidationRules
) {

    /**
     * Execute the upload use case.
     *
     * @param onProgress Optional progress callback — called after each photo is uploaded.
     *                   Parameters: (uploadedSoFar, total).
     *                   Runs on the same coroutine dispatcher as the caller (IO).
     */
    suspend operator fun invoke(
        onProgress: ((uploadedSoFar: Int, total: Int) -> Unit)? = null
    ): UploadResult {
        // 1. Fetch pending photos
        val photos = repository.getPendingPhotosOnce()

        if (photos.isEmpty()) {
            return UploadResult.Failure(UploadError.NoPendingPhotos)
        }

        // 2. Validate all photos before starting any upload
        val violations = validationRules.validate(photos)
        if (violations.isNotEmpty()) {
            return UploadResult.Failure(UploadError.ValidationFailed(violations))
        }

        val total = photos.size
        var uploadedCount = 0
        val historyRecords = mutableListOf<UploadHistory>()
        var lastFailure: UploadResult.Failure? = null

        // 3. Upload photos ONE BY ONE to allow per-photo progress reporting
        for (photo in photos) {
            val singleResult = repository.uploadPhotos(listOf(photo))

            when (singleResult) {
                is UploadResult.Success -> {
                    uploadedCount++
                    historyRecords.add(
                        UploadHistory(
                            uriString     = photo.uriString,
                            displayName   = photo.displayName,
                            mimeType      = photo.mimeType,
                            status        = UploadStatus.SUCCESS,
                            serverMessage = "Uploaded successfully"
                        )
                    )
                }
                is UploadResult.Failure -> {
                    val errorMsg = when (val err = singleResult.reason) {
                        is UploadError.NetworkError  -> err.message
                        is UploadError.ServerError   -> err.message
                        is UploadError.FileReadError -> err.message
                        is UploadError.Unknown       -> err.message
                        else                         -> "Upload failed"
                    }
                    historyRecords.add(
                        UploadHistory(
                            uriString     = photo.uriString,
                            displayName   = photo.displayName,
                            mimeType      = photo.mimeType,
                            status        = UploadStatus.FAILED,
                            serverMessage = errorMsg
                        )
                    )
                    lastFailure = singleResult
                }
            }

            // Report progress after every photo (success or failure)
            onProgress?.invoke(uploadedCount, total)
        }

        // 4. Save all history records in one batch
        repository.saveUploadHistory(historyRecords)

        // 5. Determine final result
        return if (uploadedCount == total) {
            // All photos uploaded successfully — clear pending queue
            repository.clearPendingPhotos()
            UploadResult.Success(uploadedCount = uploadedCount)
        } else if (uploadedCount > 0) {
            // Partial success — clear only successfully uploaded photos are tracked in history
            // Keep pending queue intact so user can retry failed ones
            UploadResult.Success(uploadedCount = uploadedCount)
        } else {
            // Complete failure — return the last error
            lastFailure ?: UploadResult.Failure(UploadError.Unknown("All uploads failed"))
        }
    }
}
