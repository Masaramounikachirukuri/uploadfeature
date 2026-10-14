package com.example.localupload.domain.usecase

import com.example.localupload.domain.model.UploadHistory
import com.example.localupload.domain.repository.PhotoRepository

class GetPendingPhotosUseCase(private val repository: PhotoRepository) {
    operator fun invoke(): List<UploadHistory> = repository.getPendingPhotos()
}
