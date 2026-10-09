package com.example.localupload.domain.usecase

import com.example.localupload.domain.model.Photo
import com.example.localupload.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.Flow

/**
 * GetPendingPhotosUseCase — reactive stream of the pending photo queue.
 *
 * The ViewModel collects this Flow and exposes it as UI state.
 * Emits a new list every time a photo is added or removed from the queue.
 */
class GetPendingPhotosUseCase(private val repository: PhotoRepository) {

    operator fun invoke(): Flow<List<Photo>> = repository.getPendingPhotos()
}
