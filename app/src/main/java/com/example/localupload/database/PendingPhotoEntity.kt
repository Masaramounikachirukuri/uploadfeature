package com.example.localupload.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room Entity — one row per photo selected but not yet uploaded.
 *
 * The URI stored here is a persistable content URI obtained via the
 * Android Photo Picker. Before inserting, MainActivity calls:
 *   contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)
 *
 * This makes the URI readable across app restarts and process kills —
 * so the user's selection survives even if the app is closed before uploading.
 *
 * Rows are deleted from this table when:
 *   - Upload succeeds (cleared after successful HTTP POST)
 *   - User manually removes a photo from the pending list
 */
@Entity(tableName = "pending_photos")
data class PendingPhotoEntity(

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    /**
     * Persistable content URI string.
     * e.g. "content://media/picker/0/com.android.providers.media.photopicker/media/1234"
     * Convert back to Uri with: Uri.parse(uriString)
     */
    @ColumnInfo(name = "uri_string")
    val uriString: String,

    /** MIME type detected from the URI, e.g. "image/jpeg" */
    @ColumnInfo(name = "mime_type")
    val mimeType: String = "image/jpeg",

    /** Original display name from the gallery, e.g. "IMG_20260929.jpg" */
    @ColumnInfo(name = "display_name")
    val displayName: String = "",

    /** Unix timestamp in milliseconds when the photo was added to the pending queue */
    @ColumnInfo(name = "added_at")
    val addedAt: Long = System.currentTimeMillis()
)
