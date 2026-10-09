package com.example.localupload.viewmodel

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.localupload.data.local.LocalDataSource
import com.example.localupload.data.remote.PicsumRemoteDataSource
import com.example.localupload.data.remote.RemoteDataSource
import com.example.localupload.data.repository.PhotoRepositoryImpl
import com.example.localupload.database.AppDatabase
import com.example.localupload.domain.model.Photo
import com.example.localupload.domain.model.PicsumPhoto
import com.example.localupload.domain.model.UploadError
import com.example.localupload.domain.model.UploadHistory
import com.example.localupload.domain.model.UploadResult
import com.example.localupload.domain.repository.PhotoRepository
import com.example.localupload.domain.usecase.ClearHistoryUseCase
import com.example.localupload.domain.usecase.DeleteHistoryUseCase
import com.example.localupload.domain.usecase.DeletePhotoUseCase
import com.example.localupload.domain.usecase.GetPendingPhotosUseCase
import com.example.localupload.domain.usecase.GetUploadHistoryUseCase
import com.example.localupload.domain.usecase.RetryUploadUseCase
import com.example.localupload.domain.usecase.UploadPhotoUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class UploadState {

    object Idle : UploadState()

    object Queued : UploadState()

    data class Uploading(
        val current: Int,
        val total: Int
    ) : UploadState()

    data class Success(
        val uploadedCount: Int
    ) : UploadState()

    data class Error(
        val message: String
    ) : UploadState()
}

sealed class PicsumState {

    object Idle : PicsumState()

    object Loading : PicsumState()

    data class Downloading(
        val selectedCount: Int
    ) : PicsumState()

    data class Success(
        val photoCount: Int
    ) : PicsumState()

    data class Error(
        val message: String
    ) : PicsumState()
}

class LocalUploadViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: PhotoRepository = run {
        val database = AppDatabase.getInstance(application)

        val localDataSource = LocalDataSource(database)
        val remoteDataSource = RemoteDataSource(application)
        val picsumRemoteDataSource =
            PicsumRemoteDataSource(application)

        PhotoRepositoryImpl(
            localDataSource = localDataSource,
            remoteDataSource = remoteDataSource,
            picsumRemoteDataSource = picsumRemoteDataSource
        )
    }

    private val uploadPhotoUseCase =
        UploadPhotoUseCase(repository)

    private val getPendingPhotosUseCase =
        GetPendingPhotosUseCase(repository)

    private val getUploadHistoryUseCase =
        GetUploadHistoryUseCase(repository)

    private val deletePhotoUseCase =
        DeletePhotoUseCase(repository)

    private val deleteHistoryUseCase =
        DeleteHistoryUseCase(repository)

    private val clearHistoryUseCase =
        ClearHistoryUseCase(repository)

    @Suppress("unused")
    private val retryUploadUseCase =
        RetryUploadUseCase(uploadPhotoUseCase)

    private val _pendingPhotos =
        MutableStateFlow<List<Photo>>(emptyList())

    val pendingPhotos: StateFlow<List<Photo>> =
        _pendingPhotos.asStateFlow()

    private val _uploadHistory =
        MutableStateFlow<List<UploadHistory>>(emptyList())

    val uploadHistory: StateFlow<List<UploadHistory>> =
        _uploadHistory.asStateFlow()

    private val _uploadState =
        MutableStateFlow<UploadState>(UploadState.Idle)

    val uploadState: StateFlow<UploadState> =
        _uploadState.asStateFlow()

    private val _picsumState =
        MutableStateFlow<PicsumState>(PicsumState.Idle)

    val picsumState: StateFlow<PicsumState> =
        _picsumState.asStateFlow()

    private val _picsumPhotos =
        MutableStateFlow<List<PicsumPhoto>>(emptyList())

    val picsumPhotos: StateFlow<List<PicsumPhoto>> =
        _picsumPhotos.asStateFlow()

    private val _currentPicsumPage =
        MutableStateFlow(1)

    val currentPicsumPage: StateFlow<Int> =
        _currentPicsumPage.asStateFlow()

    private var uploadJob: Job? = null

    init {
        observePendingPhotos()
        observeUploadHistory()
        loadPicsumPhotos(page = 1)
    }

    private fun observePendingPhotos() {
        viewModelScope.launch {
            getPendingPhotosUseCase().collect { photos ->
                _pendingPhotos.value = photos
            }
        }
    }

    private fun observeUploadHistory() {
        viewModelScope.launch {
            getUploadHistoryUseCase().collect { history ->
                _uploadHistory.value = history
            }
        }
    }

    fun addPendingPhotos(
        uris: List<Uri>
    ) {
        if (uris.isEmpty()) {
            return
        }

        val application = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            val photos = uris.map { uri ->
                val mimeType =
                    application.contentResolver.getType(uri)
                        ?: DEFAULT_IMAGE_MIME_TYPE

                val displayName =
                    getDisplayName(uri)
                        ?: uri.lastPathSegment
                        ?: DEFAULT_PHOTO_NAME

                Photo(
                    uriString = uri.toString(),
                    mimeType = mimeType,
                    displayName = displayName
                )
            }

            repository.addPendingPhotos(photos)
        }
    }

    fun loadPicsumPhotos(
        page: Int = 1
    ) {
        if (_picsumState.value is PicsumState.Loading) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _picsumState.value = PicsumState.Loading

            try {
                val photos = repository.getPicsumPhotos(
                    page = page,
                    limit = PICSUM_PAGE_SIZE
                )

                _currentPicsumPage.value = page
                _picsumPhotos.value = photos

                _picsumState.value = PicsumState.Success(
                    photoCount = photos.size
                )
            } catch (exception: Exception) {
                _picsumState.value = PicsumState.Error(
                    message = exception.message
                        ?: "Unable to load photos from Picsum."
                )
            }
        }
    }

    fun togglePicsumPhotoSelection(
        photoId: String
    ) {
        _picsumPhotos.value =
            _picsumPhotos.value.map { photo ->
                if (photo.id == photoId) {
                    photo.copy(
                        isSelected = !photo.isSelected
                    )
                } else {
                    photo
                }
            }
    }

    fun clearPicsumSelection() {
        _picsumPhotos.value =
            _picsumPhotos.value.map { photo ->
                photo.copy(isSelected = false)
            }
    }

    fun moveSelectedPicsumPhotosToUploadQueue() {
        val selectedPhotos =
            _picsumPhotos.value.filter { photo ->
                photo.isSelected
            }

        if (selectedPhotos.isEmpty()) {
            _picsumState.value = PicsumState.Error(
                message = "Select at least one photo."
            )
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _picsumState.value = PicsumState.Downloading(
                selectedCount = selectedPhotos.size
            )

            try {
                val downloadedPhotos =
                    repository.downloadPicsumPhotos(
                        photos = selectedPhotos
                    )

                if (downloadedPhotos.isEmpty()) {
                    _picsumState.value = PicsumState.Error(
                        message =
                            "The selected photos could not be downloaded."
                    )
                    return@launch
                }

                repository.addPendingPhotos(
                    photos = downloadedPhotos
                )

                clearPicsumSelection()

                _picsumState.value = PicsumState.Success(
                    photoCount = _picsumPhotos.value.size
                )

                _uploadState.value = UploadState.Idle
            } catch (exception: Exception) {
                _picsumState.value = PicsumState.Error(
                    message = exception.message
                        ?: "Failed to save selected photos."
                )
            }
        }
    }

    fun loadNextPicsumPage() {
        loadPicsumPhotos(
            page = _currentPicsumPage.value + 1
        )
    }

    fun loadPreviousPicsumPage() {
        val previousPage =
            (_currentPicsumPage.value - 1)
                .coerceAtLeast(1)

        loadPicsumPhotos(
            page = previousPage
        )
    }

    fun retryPicsumLoading() {
        loadPicsumPhotos(
            page = _currentPicsumPage.value
        )
    }

    fun removePendingPhoto(
        id: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            deletePhotoUseCase(id)
        }
    }

    fun uploadPhotos() {
        if (_uploadState.value is UploadState.Uploading) {
            return
        }

        val total = _pendingPhotos.value.size

        if (total == 0) {
            _uploadState.value = UploadState.Error(
                message = "No photos to upload."
            )
            return
        }

        uploadJob = viewModelScope.launch(Dispatchers.IO) {
            _uploadState.value = UploadState.Uploading(
                current = 0,
                total = total
            )

            val result = uploadPhotoUseCase(
                onProgress = { uploadedSoFar, photoTotal ->
                    _uploadState.value = UploadState.Uploading(
                        current = uploadedSoFar,
                        total = photoTotal
                    )
                }
            )

            when (result) {
                is UploadResult.Success -> {
                    _uploadState.value = UploadState.Success(
                        uploadedCount = result.uploadedCount
                    )

                    delay(SUCCESS_MESSAGE_DELAY)
                    _uploadState.value = UploadState.Idle
                }

                is UploadResult.Failure -> {
                    val errorMessage =
                        createUploadErrorMessage(result.reason)

                    _uploadState.value = UploadState.Error(
                        message = errorMessage
                    )

                    delay(ERROR_MESSAGE_DELAY)
                    _uploadState.value = UploadState.Idle
                }
            }
        }
    }

    private fun createUploadErrorMessage(
        uploadError: UploadError
    ): String {
        return when (uploadError) {
            is UploadError.NoPendingPhotos -> {
                "No photos to upload."
            }

            is UploadError.ValidationFailed -> {
                uploadError.violations.joinToString("; ")
            }

            is UploadError.NetworkError -> {
                "Network error: ${uploadError.message}"
            }

            is UploadError.ServerError -> {
                "Server error: ${uploadError.message}"
            }

            is UploadError.FileReadError -> {
                "File read error: ${uploadError.message}"
            }

            is UploadError.Unknown -> {
                "Upload failed: ${uploadError.message}"
            }
        }
    }

    fun cancelUpload() {
        uploadJob?.cancel()
        uploadJob = null
        _uploadState.value = UploadState.Idle
    }

    fun resetState() {
        _uploadState.value = UploadState.Idle
    }

    fun deleteHistoryRecord(
        id: Int
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            deleteHistoryUseCase(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            clearHistoryUseCase()
        }
    }

    private fun getDisplayName(
        uri: Uri
    ): String? {
        return try {
            val application = getApplication<Application>()

            application.contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(
                    OpenableColumns.DISPLAY_NAME
                )

                if (
                    cursor.moveToFirst() &&
                    nameIndex >= 0
                ) {
                    cursor.getString(nameIndex)
                } else {
                    null
                }
            }
        } catch (exception: Exception) {
            null
        }
    }

    companion object {
        private const val PICSUM_PAGE_SIZE = 30
        private const val DEFAULT_IMAGE_MIME_TYPE = "image/jpeg"
        private const val DEFAULT_PHOTO_NAME = "photo"
        private const val SUCCESS_MESSAGE_DELAY = 3_000L
        private const val ERROR_MESSAGE_DELAY = 5_000L
    }
}