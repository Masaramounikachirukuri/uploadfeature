package com.example.localupload.domain.model

import com.google.gson.annotations.SerializedName

/**
 * UploadResult represents the server's per-file upload result.
 */
data class UploadResult(
    @SerializedName("original_name") val originalName: String = "",
    @SerializedName("stored_name") val storedName: String = "",
    @SerializedName("file_url") val fileUrl: String = "",
    @SerializedName("mime_type") val mimeType: String = "",
    @SerializedName("size_bytes") val sizeBytes: Long = 0L,
    @SerializedName("upload_status") val uploadStatus: String = "success"
) {
    val isSuccess: Boolean get() = uploadStatus == "success"

    val readableSize: String
        get() = when {
            sizeBytes >= 1_048_576 -> "%.1f MB".format(sizeBytes / 1_048_576.0)
            sizeBytes >= 1_024    -> "%.1f KB".format(sizeBytes / 1_024.0)
            else                  -> "$sizeBytes B"
        }
}

/**
 * UploadResponse wraps the top-level JSON response from the upload endpoint.
 *
 * [originalBytes]    — total size of all source images before compression (bytes).
 * [compressedBytes]  — total size of all compressed JPEG files sent to server (bytes).
 *
 * These two fields are filled in by [RemoteDataSource] (not the server) because the
 * server only sees the compressed payload. The ViewModel surfaces them in the UI as
 * "Saved X MB (Y%)" so the user can see the benefit of compression.
 */
data class UploadResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String = "",
    @SerializedName("total_uploaded") val totalUploaded: Int = 0,
    @SerializedName("failed_count") val failedCount: Int = 0,
    @SerializedName("session_id") val sessionId: String? = null,
    @SerializedName("files") val files: List<UploadResult> = emptyList(),
    // Client-side compression stats (not from server JSON)
    val originalBytes:   Long = 0L,
    val compressedBytes: Long = 0L
) {
    /** Bytes saved by compression. Zero if no compression happened. */
    val savedBytes: Long get() = (originalBytes - compressedBytes).coerceAtLeast(0L)

    /** Percentage of original size removed by compression (0–100). */
    val savedPercent: Int
        get() = if (originalBytes > 0)
            ((savedBytes.toDouble() / originalBytes) * 100).toInt()
        else 0

    /** Human-readable compression summary, e.g. "Saved 3.2 MB (68%)". */
    val compressionSummary: String
        get() = when {
            savedBytes <= 0 -> ""
            savedBytes >= 1_048_576 ->
                "Saved %.1f MB (%d%%)".format(savedBytes / 1_048_576.0, savedPercent)
            savedBytes >= 1_024 ->
                "Saved %.1f KB (%d%%)".format(savedBytes / 1_024.0, savedPercent)
            else -> "Saved $savedBytes B (%d%%)".format(savedPercent)
        }
}
