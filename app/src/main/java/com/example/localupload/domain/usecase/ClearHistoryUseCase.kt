package com.example.localupload.domain.usecase

import com.example.localupload.domain.repository.PhotoRepository

class ClearHistoryUseCase(private val repository: PhotoRepository) {
    operator fun invoke() = repository.clearHistory()
}
