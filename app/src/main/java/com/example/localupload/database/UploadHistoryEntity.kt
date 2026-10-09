package com.example.localupload.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity — one row per photo upload attempt (permanent audit log).
 *
 * Written when the user taps "Upload" — one row per photo, regardless of
 * whether the upload succeeded or failed.
 *
 * This table is NEVER cleared automatically. It is a permanent history
 * the user can view in the PhotoHistoryScreen.
 *
 * Status values:
 *   "success" — HTTP POST returned 2xx and server confirmed success
 *   "failed"  — network error, timeout, or server returned error
 */
@Entity(tableName = "upload_history")
data class UploadHistoryEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /**
     * The content URI string of the photo that was uploaded.
     * Stored for reference — the persistable URI permission may have been
     * released after a successful upload.
     */
    @ColumnInfo(name = "uri_string")
    val uriString: String,

    /** Original display name from the gallery, e.g. "IMG_20260929.jpg" */
    @ColumnInfo(name = "display_name")
    val displayName: String = "",

    /** MIME type of the uploaded file, e.g. "image/jpeg" */
    @ColumnInfo(name = "mime_type")
    val mimeType: String = "image/jpeg",

    /**
     * Upload result status: "success" or "failed"
     */
    @ColumnInfo(name = "status")
    val status: String,

    /**
     * Server response message on success, or error message on failure.
     * e.g. "Uploaded successfully" or "Connection timeout"
     */
    @ColumnInfo(name = "server_message")
    val serverMessage: String = "",

    /** Unix timestamp in milliseconds when the upload was attempted */
    @ColumnInfo(name = "uploaded_at")
    val uploadedAt: Long = System.currentTimeMillis()
) {
    /** Human-readable formatted upload time for display in history list */
    val isSuccess: Boolean
        get() = status == "success"
}
