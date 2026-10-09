package com.example.localupload.domain.usecase

import com.example.localupload.domain.model.UploadResult

/**
 * RetryUploadUseCase — retries a failed upload.
 *
 * The pending queue is NOT cleared on failure, so photos that failed to upload
 * are still present in Room. This use case simply re-invokes UploadPhotoUseCase,
 * which reads the current pending queue and attempts upload again.
 *
 * This is the Retry button pattern:
 *   UI "Retry" tap → ViewModel → RetryUploadUseCase → UploadPhotoUseCase → Repository
 *
 * Keeping it as a separate use case (rather than calling UploadPhotoUseCase directly)
 * allows future retry logic like exponential back-off or max-retry limits to be added
 * in one place without changing the ViewModel or UI.
 */
class RetryUploadUseCase(private val uploadPhotoUseCase: UploadPhotoUseCase) {

    suspend operator fun invoke(): UploadResult = uploadPhotoUseCase()
}
