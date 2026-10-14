package com.example.localupload.domain.usecase

object PhotoValidationRules {
    const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L  // 10 MB
    const val MAX_PHOTOS_PER_BATCH = 10
    val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp", "image/gif")

    fun isSizeValid(bytes: Long): Boolean = bytes in 1..MAX_FILE_SIZE_BYTES
    fun isMimeTypeValid(mimeType: String): Boolean = mimeType in ALLOWED_MIME_TYPES
}
