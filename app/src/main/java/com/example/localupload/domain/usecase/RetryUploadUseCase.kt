package com.example.localupload.domain.usecase

import android.net.Uri
import com.example.localupload.domain.model.UploadHistory
import com.example.localupload.domain.model.UploadResponse
import com.example.localupload.domain.model.UploadStatus
import com.example.localupload.domain.repository.PhotoRepository

class RetryUploadUseCase(private val repository: PhotoRepository) {
    suspend operator fun invoke(item: UploadHistory): UploadResponse {
        // Mark item as UPLOADING before retry
        repository.updateHistory(item.copy(status = UploadStatus.UPLOADING))
        return repository.uploadPhotos(listOf(Uri.parse(item.localUri)))
    }
}
