package com.example.localupload.domain.usecase

import com.example.localupload.domain.repository.PhotoRepository

/**
 * ClearHistoryUseCase — deletes all upload history records.
 * Called when the user confirms the "Clear All" dialog in PhotoHistoryScreen.
 */
class ClearHistoryUseCase(private val repository: PhotoRepository) {

    suspend operator fun invoke() {
        repository.clearAllHistory()
    }
}
