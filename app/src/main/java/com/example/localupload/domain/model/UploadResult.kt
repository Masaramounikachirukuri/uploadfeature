package com.example.localupload.domain.model

/**
 * Typed result returned by UploadPhotoUseCase and RetryUploadUseCase.
 *
 * Replaces raw String error messages and boolean flags with a proper sealed hierarchy.
 * The ViewModel maps this to UploadState for the UI layer.
 */
sealed class UploadResult {

    /** All photos uploaded successfully */
    data class Success(val uploadedCount: Int) : UploadResult()

    /** Upload failed with a typed reason */
    data class Failure(val reason: UploadError) : UploadResult()
}

/**
 * Typed upload error categories.
 * Allows UI to show actionable messages per error type.
 */
sealed class UploadError {

    /** No photos in the pending queue to upload */
    object NoPendingPhotos : UploadError()

    /** Validation rejected one or more photos before upload */
    data class ValidationFailed(val violations: List<String>) : UploadError()

    /** Network / server error — retry is appropriate */
    data class NetworkError(val message: String) : UploadError()

    /** Server accepted the request but responded with failure */
    data class ServerError(val message: String) : UploadError()

    /** Could not read photo bytes from the URI (permission expired or file deleted) */
    data class FileReadError(val message: String) : UploadError()

    /** Catch-all for unexpected errors */
    data class Unknown(val message: String) : UploadError()
}
