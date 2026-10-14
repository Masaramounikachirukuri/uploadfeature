package com.example.localupload.domain.usecase

import android.net.Uri
import com.example.localupload.domain.model.UploadResponse
import com.example.localupload.domain.repository.PhotoRepository

class UploadPhotoUseCase(private val repository: PhotoRepository) {
    suspend operator fun invoke(uris: List<Uri>): UploadResponse =
        repository.uploadPhotos(uris)
}
