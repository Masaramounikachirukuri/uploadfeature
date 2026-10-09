package com.example.localupload.domain.model

/**
 * Domain entity — represents a photo in the pending upload queue.
 *
 * This is a pure Kotlin data class with NO dependency on Room, Android, or Retrofit.
 * It lives in the Domain layer and is the shared language between Presentation and Data.
 *
 * Mapped from:
 *   Data layer  → PendingPhotoEntity  (Room)
 *   Domain      → Photo               (this class)
 *   Presentation→ displayed in PhotoPickerScreen
 */
data class Photo(
    /** Auto-generated primary key from Room (0 = not yet persisted) */
    val id: Int = 0,

    /** Persistable content URI string — readable across reboots after takePersistableUriPermission() */
    val uriString: String,

    /** MIME type e.g. "image/jpeg" */
    val mimeType: String = "image/jpeg",

    /** Original file name from the gallery e.g. "IMG_20260929.jpg" */
    val displayName: String = "",

    /** Unix timestamp (ms) when added to pending queue */
    val addedAt: Long = System.currentTimeMillis()
)
