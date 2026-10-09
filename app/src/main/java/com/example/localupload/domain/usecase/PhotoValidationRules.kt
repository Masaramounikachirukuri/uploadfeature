package com.example.localupload.domain.usecase

import com.example.localupload.domain.model.Photo

// PhotoValidationRules -- pure business rules for photo validation.
// Lives entirely in the Domain layer -- no Android, Room, or Retrofit dependency.
// Called by UploadPhotoUseCase before sending to the repository.
//
// Rules:
//   1. Maximum 50 photos per batch
//   2. Only image MIME types are allowed (image/*)
object PhotoValidationRules {

    private const val MAX_BATCH_SIZE = 50
    private const val ALLOWED_MIME_PREFIX = "image/"

    // Validates a batch of photos.
    // Returns a list of human-readable violation messages. Empty list means valid.
    fun validate(photos: List<Photo>): List<String> {
        val violations = mutableListOf<String>()

        // Rule 1: must have at least one photo
        if (photos.isEmpty()) {
            violations.add("No photos selected. Please add at least one photo.")
        }

        // Rule 1b: batch size limit
        if (photos.size > MAX_BATCH_SIZE) {
            violations.add(
                "Too many photos selected (${photos.size}). Maximum allowed per upload is $MAX_BATCH_SIZE."
            )
        }

        // Rule 2: MIME type must be an image
        val nonImagePhotos = photos.filter { !it.mimeType.startsWith(ALLOWED_MIME_PREFIX) }
        if (nonImagePhotos.isNotEmpty()) {
            val names = nonImagePhotos.joinToString(", ") { it.displayName.ifEmpty { it.uriString } }
            violations.add("Only image files are allowed. Non-image files: $names")
        }

        return violations
    }

    // Convenience -- returns true when all rules pass
    fun isValid(photos: List<Photo>): Boolean = validate(photos).isEmpty()
}
