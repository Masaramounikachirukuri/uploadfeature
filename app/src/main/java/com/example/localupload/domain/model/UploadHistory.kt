package com.example.localupload.domain.model

/**
 * UploadStatus enum tracks the lifecycle state of each pending upload.
 */
enum class UploadStatus {
    PENDING,
    UPLOADING,
    SUCCESS,
    FAILED
}

/**
 * UploadHistory is an in-memory record of a photo that was queued or completed for upload.
 * It holds the local URI string plus all the state needed to show progress and retry.
 */
data class UploadHistory(
    val id: String,                              // unique ID (UUID)
    val localUri: String,                        // content:// URI of the local image
    val fileName: String,                        // display name like "photo_1.jpg"
    val fileSizeBytes: Long = 0L,
    val mimeType: String = "image/jpeg",
    val status: UploadStatus = UploadStatus.PENDING,
    val uploadedUrl: String? = null,             // server URL after success
    val errorMessage: String? = null,            // error detail on failure
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    val readableSize: String
        get() = when {
            fileSizeBytes >= 1_048_576 -> "%.1f MB".format(fileSizeBytes / 1_048_576.0)
            fileSizeBytes >= 1_024    -> "%.1f KB".format(fileSizeBytes / 1_024.0)
            else                      -> "$fileSizeBytes B"
        }

    val statusLabel: String
        get() = when (status) {
            UploadStatus.PENDING   -> "Pending"
            UploadStatus.UPLOADING -> "Uploading…"
            UploadStatus.SUCCESS   -> "Uploaded"
            UploadStatus.FAILED    -> "Failed"
        }
}
