package com.example.localupload.domain.model

/**
 * Domain entity — represents one completed upload attempt (permanent audit log entry).
 *
 * Pure Kotlin — no Room, no Android, no Retrofit dependency.
 * Mapped from UploadHistoryEntity (Room) via LocalDataSource in the Data layer.
 */
data class UploadHistory(
    val id: Int = 0,
    val uriString: String,
    val displayName: String = "",
    val mimeType: String = "image/jpeg",
    val status: UploadStatus,
    val serverMessage: String = "",
    val uploadedAt: Long = System.currentTimeMillis()
) {
    val isSuccess: Boolean get() = status == UploadStatus.SUCCESS
}

/**
 * Typed enum replacing the raw "success" / "failed" string stored in Room.
 */
enum class UploadStatus {
    SUCCESS, FAILED;

    fun toDbString(): String = when (this) {
        SUCCESS -> "success"
        FAILED  -> "failed"
    }

    companion object {
        fun fromDbString(value: String): UploadStatus =
            if (value == "success") SUCCESS else FAILED
    }
}
