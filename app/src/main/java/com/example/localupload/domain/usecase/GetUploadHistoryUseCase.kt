package com.example.localupload.domain.usecase

import com.example.localupload.domain.model.UploadHistory
import com.example.localupload.domain.repository.PhotoRepository
import kotlinx.coroutines.flow.Flow

/**
 * GetUploadHistoryUseCase — reactive stream of the permanent upload audit log.
 *
 * The ViewModel collects this Flow and exposes it as UI state for PhotoHistoryScreen.
 * Emits newest-first (ordered by uploaded_at DESC in the Room query).
 */
class GetUploadHistoryUseCase(private val repository: PhotoRepository) {

    operator fun invoke(): Flow<List<UploadHistory>> = repository.getUploadHistory()
}
