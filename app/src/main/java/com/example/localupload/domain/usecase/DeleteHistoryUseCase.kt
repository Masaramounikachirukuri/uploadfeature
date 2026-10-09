package com.example.localupload.domain.usecase

import com.example.localupload.domain.repository.PhotoRepository

/**
 * DeleteHistoryUseCase — removes a single upload history record by ID.
 * Called when the user taps the delete icon on a history card in PhotoHistoryScreen.
 */
class DeleteHistoryUseCase(private val repository: PhotoRepository) {

    suspend operator fun invoke(historyId: Int) {
        repository.deleteHistoryRecord(historyId)
    }
}
