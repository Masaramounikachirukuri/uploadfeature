package com.example.localupload.domain.usecase

import com.example.localupload.domain.repository.PhotoRepository

/**
 * DeletePhotoUseCase — removes a single photo from the pending upload queue.
 * Called when the user taps the (×) remove button on a thumbnail.
 */
class DeletePhotoUseCase(private val repository: PhotoRepository) {

    suspend operator fun invoke(photoId: Int) {
        repository.removePendingPhoto(photoId)
    }
}
