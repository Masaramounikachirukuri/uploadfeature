package com.example.localupload.domain.usecase

import com.example.localupload.domain.repository.PhotoRepository

class DeleteHistoryUseCase(private val repository: PhotoRepository) {
    operator fun invoke(id: String) = repository.deleteHistory(id)
}
