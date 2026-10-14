package com.example.localupload.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.localupload.data.local.LocalDataSource
import com.example.localupload.data.remote.RemoteDataSource
import com.example.localupload.data.repository.PhotoRepositoryImpl
import com.example.localupload.domain.model.Photo
import com.example.localupload.domain.model.UploadHistory
import com.example.localupload.domain.model.UploadResult
import com.example.localupload.domain.model.UploadStatus
import com.example.localupload.worker.UploadWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

sealed class GalleryState {

    object Loading : GalleryState()

    data class Success(
        val photos: List<Photo>
    ) : GalleryState()

    data class Error(
        val message: String
    ) : GalleryState()
}

sealed class UploadUiState {

    object Idle : UploadUiState()

    data class Loading(
        val phase: String,
        val current: Int,
        val total: Int,
        val speedBps: Long = 0L,
        val etaSeconds: Long = 0L
    ) : UploadUiState()

    data class Success(
        val message: String,
        val successCount: Int,
        val total: Int,
        val uploadedFiles: List<UploadResult> = emptyList(),
        val compressionSummary: String = ""
    ) : UploadUiState()

    data class Error(
        val message: String
    ) : UploadUiState()
}

class LocalUploadViewModel(
    application: Application
) : AndroidViewModel(application) {

    companion object {
        const val UPLOAD_WORK_NAME = "local_photo_upload"

        private const val ERROR_MESSAGE_KEY = "ERROR_MESSAGE"
    }

    private val localDataSource = LocalDataSource()

    private val remoteDataSource = RemoteDataSource(
        application.applicationContext
    )

    private val repository = PhotoRepositoryImpl(
        remoteDataSource,
        localDataSource
    )

    private val workManager = WorkManager.getInstance(
        application.applicationContext
    )

    // -------------------------------------------------------------------------
    // Picsum gallery state
    // -------------------------------------------------------------------------

    private val _galleryState =
        MutableStateFlow<GalleryState>(
            GalleryState.Loading
        )

    val galleryState: StateFlow<GalleryState> =
        _galleryState.asStateFlow()

    private val _isRefreshing =
        MutableStateFlow(false)

    val isRefreshing: StateFlow<Boolean> =
        _isRefreshing.asStateFlow()

    private var cachedPhotos: List<Photo> =
        emptyList()

    // -------------------------------------------------------------------------
    // Shared upload selection
    // -------------------------------------------------------------------------

    private val _selectedUploadUris =
        MutableStateFlow<List<Uri>>(emptyList())

    val selectedUploadUris: StateFlow<List<Uri>> =
        _selectedUploadUris.asStateFlow()

    private val _isPreparingPicsumPhotos =
        MutableStateFlow(false)

    val isPreparingPicsumPhotos: StateFlow<Boolean> =
        _isPreparingPicsumPhotos.asStateFlow()

    private val _picsumPreparationError =
        MutableStateFlow<String?>(null)

    val picsumPreparationError: StateFlow<String?> =
        _picsumPreparationError.asStateFlow()

    private var activeUploadUris: Set<Uri> =
        emptySet()

    // -------------------------------------------------------------------------
    // Upload state
    // -------------------------------------------------------------------------

    private val _uploadState =
        MutableStateFlow<UploadUiState>(
            UploadUiState.Idle
        )

    val uploadState: StateFlow<UploadUiState> =
        _uploadState.asStateFlow()

    // -------------------------------------------------------------------------
    // Upload history
    // -------------------------------------------------------------------------

    private val _history =
        MutableStateFlow<List<UploadHistory>>(
            emptyList()
        )

    val history: StateFlow<List<UploadHistory>> =
        _history.asStateFlow()

    private var currentWorkObserver:
            Observer<WorkInfo?>? = null

    private var observedWorkId: UUID? = null

    init {
        fetchGallery()
        refreshHistory()
    }

    // -------------------------------------------------------------------------
    // Picsum gallery
    // -------------------------------------------------------------------------

    fun fetchGallery(
        forceRefresh: Boolean = false
    ) {
        if (!forceRefresh && cachedPhotos.isNotEmpty()) {
            _galleryState.value =
                GalleryState.Success(cachedPhotos)

            return
        }

        viewModelScope.launch {
            if (forceRefresh) {
                _isRefreshing.value = true
            } else {
                _galleryState.value =
                    GalleryState.Loading
            }

            try {
                val photos = repository.fetchPhotos(
                    page = 1,
                    limit = 30
                )

                cachedPhotos = photos

                _galleryState.value =
                    GalleryState.Success(photos)
            } catch (exception: Exception) {
                _galleryState.value =
                    if (
                        forceRefresh &&
                        cachedPhotos.isNotEmpty()
                    ) {
                        GalleryState.Success(cachedPhotos)
                    } else {
                        GalleryState.Error(
                            message =
                                exception.localizedMessage
                                    ?: "Unable to load Picsum photos."
                        )
                    }
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun getPhotoById(
        id: String
    ): Photo? {
        return cachedPhotos.find { photo ->
            photo.id == id
        }
    }

    // -------------------------------------------------------------------------
    // Prepare Picsum photos
    // -------------------------------------------------------------------------

    /**
     * Checks the reusable Picsum cache and downloads only missing photos.
     *
     * This method does not start the server upload.
     */
    fun preparePicsumPhotos(
        photos: List<Photo>,
        onReady: () -> Unit
    ) {
        val uniquePhotos =
            photos.distinctBy { photo ->
                photo.id
            }

        if (uniquePhotos.isEmpty()) {
            _picsumPreparationError.value =
                "Select at least one Picsum photo."

            return
        }

        if (_isPreparingPicsumPhotos.value) {
            return
        }

        if (_uploadState.value is UploadUiState.Loading) {
            _picsumPreparationError.value =
                "An upload is already running."

            return
        }

        viewModelScope.launch {
            _isPreparingPicsumPhotos.value = true
            _picsumPreparationError.value = null

            try {
                val localUris =
                    repository.downloadPhotosToCache(
                        photos = uniquePhotos
                    )

                if (localUris.isEmpty()) {
                    _picsumPreparationError.value =
                        "Unable to prepare the selected Picsum photos."

                    return@launch
                }

                addUploadUris(localUris)
                onReady()
            } catch (exception: Exception) {
                _picsumPreparationError.value =
                    exception.localizedMessage
                        ?: "Unable to prepare Picsum photos."
            } finally {
                _isPreparingPicsumPhotos.value = false
            }
        }
    }

    fun clearPicsumPreparationError() {
        _picsumPreparationError.value = null
    }

    // -------------------------------------------------------------------------
    // Shared selected photos
    // -------------------------------------------------------------------------

    fun addUploadUris(
        uris: List<Uri>
    ) {
        if (uris.isEmpty()) {
            return
        }

        _selectedUploadUris.value =
            (_selectedUploadUris.value + uris)
                .distinctBy { uri ->
                    uri.toString()
                }

        resetUploadState()
    }

    fun removeUploadUri(
        uri: Uri
    ) {
        if (_uploadState.value is UploadUiState.Loading) {
            return
        }

        _selectedUploadUris.value =
            _selectedUploadUris.value.filterNot { savedUri ->
                savedUri.toString() == uri.toString()
            }

        resetUploadState()
    }

    private fun removeUploadUris(
        uris: Collection<Uri>
    ) {
        if (uris.isEmpty()) {
            return
        }

        val valuesToRemove =
            uris.map { uri ->
                uri.toString()
            }.toSet()

        _selectedUploadUris.value =
            _selectedUploadUris.value.filterNot { uri ->
                uri.toString() in valuesToRemove
            }
    }

    fun clearSelectedUploadUris() {
        if (_uploadState.value is UploadUiState.Loading) {
            return
        }

        _selectedUploadUris.value = emptyList()
        activeUploadUris = emptySet()

        resetUploadState()
    }

    // -------------------------------------------------------------------------
    // Server upload
    // -------------------------------------------------------------------------

    fun uploadPhotos(
        uris: List<Uri>
    ) {
        val uniqueUris =
            uris.distinctBy { uri ->
                uri.toString()
            }

        if (uniqueUris.isEmpty()) {
            _uploadState.value =
                UploadUiState.Error(
                    message = "Select at least one photo."
                )

            return
        }

        if (_uploadState.value is UploadUiState.Loading) {
            return
        }

        activeUploadUris = uniqueUris.toSet()

        val total = uniqueUris.size

        _uploadState.value =
            UploadUiState.Loading(
                phase = "Starting upload",
                current = 0,
                total = total
            )

        val historyItems =
            uniqueUris.mapIndexed { index, uri ->
                UploadHistory(
                    id = UUID.randomUUID().toString(),
                    localUri = uri.toString(),
                    fileName = createFileName(
                        uri = uri,
                        position = index
                    ),
                    mimeType = getMimeType(uri),
                    status = UploadStatus.UPLOADING,
                    createdAt = now(),
                    updatedAt = now()
                )
            }

        historyItems.forEach { item ->
            repository.addToHistory(item)
        }

        refreshHistory()

        val inputData =
            workDataOf(
                UploadWorker.KEY_PHOTO_URIS to
                        uniqueUris
                            .map { uri ->
                                uri.toString()
                            }
                            .toTypedArray(),

                UploadWorker.KEY_HISTORY_IDS to
                        historyItems
                            .map { item ->
                                item.id
                            }
                            .toTypedArray()
            )

        /*
         * No NetworkType.CONNECTED constraint is used.
         *
         * This lets WorkManager start immediately. Retrofit will return the
         * actual connection or server error if the backend is unavailable.
         */
        val workRequest =
            OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(inputData)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15L,
                    TimeUnit.SECONDS
                )
                .build()

        workManager.enqueueUniqueWork(
            UPLOAD_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )

        observeWorkRequest(
            workId = workRequest.id,
            historyItems = historyItems,
            total = total
        )
    }

    private fun observeWorkRequest(
        workId: UUID,
        historyItems: List<UploadHistory>,
        total: Int
    ) {
        removeCurrentWorkObserver()

        val workInfoLiveData =
            workManager.getWorkInfoByIdLiveData(workId)

        val observer =
            Observer<WorkInfo?> { workInfo ->

                if (workInfo == null) {
                    return@Observer
                }

                when (workInfo.state) {
                    WorkInfo.State.ENQUEUED -> {
                        val retrying =
                            workInfo.runAttemptCount > 0

                        _uploadState.value =
                            UploadUiState.Loading(
                                phase =
                                    if (retrying) {
                                        "Retrying upload"
                                    } else {
                                        "Starting upload"
                                    },
                                current = 0,
                                total = total
                            )
                    }

                    WorkInfo.State.BLOCKED -> {
                        _uploadState.value =
                            UploadUiState.Loading(
                                phase = "Upload queued",
                                current = 0,
                                total = total
                            )
                    }

                    WorkInfo.State.RUNNING -> {
                        val phase =
                            workInfo.progress.getString(
                                UploadWorker.KEY_PROGRESS_PHASE
                            ) ?: "Uploading"

                        val progressTotal =
                            workInfo.progress.getInt(
                                UploadWorker.KEY_PROGRESS_TOTAL,
                                total
                            ).coerceAtLeast(1)

                        val current =
                            workInfo.progress.getInt(
                                UploadWorker.KEY_PROGRESS_CURRENT,
                                0
                            ).coerceIn(
                                minimumValue = 0,
                                maximumValue = progressTotal
                            )

                        val speedBps =
                            workInfo.progress.getLong(
                                UploadWorker.KEY_UPLOAD_SPEED_BPS,
                                0L
                            )

                        val etaSeconds =
                            workInfo.progress.getLong(
                                UploadWorker.KEY_ETA_SECONDS,
                                0L
                            )

                        _uploadState.value =
                            UploadUiState.Loading(
                                phase = phase,
                                current = current,
                                total = progressTotal,
                                speedBps = speedBps,
                                etaSeconds = etaSeconds
                            )
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        historyItems.forEach { item ->
                            repository.updateHistory(
                                item.copy(
                                    status = UploadStatus.SUCCESS,
                                    errorMessage = null,
                                    updatedAt = now()
                                )
                            )
                        }

                        refreshHistory()

                        val compressionSummary =
                            workInfo.outputData.getString(
                                UploadWorker.KEY_COMPRESSION_SUMMARY
                            ).orEmpty()

                        removeUploadUris(activeUploadUris)
                        activeUploadUris = emptySet()

                        _uploadState.value =
                            UploadUiState.Success(
                                message =
                                    "$total photo(s) uploaded successfully.",
                                successCount = total,
                                total = total,
                                compressionSummary =
                                    compressionSummary
                            )

                        removeCurrentWorkObserver()
                    }

                    WorkInfo.State.FAILED -> {
                        handleFailedUpload(
                            workInfo = workInfo,
                            historyItems = historyItems,
                            total = total
                        )

                        activeUploadUris = emptySet()
                        removeCurrentWorkObserver()
                    }

                    WorkInfo.State.CANCELLED -> {
                        historyItems.forEach { item ->
                            repository.updateHistory(
                                item.copy(
                                    status = UploadStatus.FAILED,
                                    errorMessage =
                                        "Upload cancelled",
                                    updatedAt = now()
                                )
                            )
                        }

                        refreshHistory()

                        activeUploadUris = emptySet()

                        _uploadState.value =
                            UploadUiState.Idle

                        removeCurrentWorkObserver()
                    }
                }
            }

        currentWorkObserver = observer
        observedWorkId = workId

        workInfoLiveData.observeForever(observer)
    }

    private fun handleFailedUpload(
        workInfo: WorkInfo,
        historyItems: List<UploadHistory>,
        total: Int
    ) {
        val failedIds =
            workInfo.outputData
                .getStringArray(
                    UploadWorker.KEY_FAILED_IDS
                )
                ?.toSet()
                .orEmpty()

        val workerErrorMessage =
            workInfo.outputData
                .getString(ERROR_MESSAGE_KEY)
                .orEmpty()

        val allItemsFailed =
            failedIds.isEmpty()

        historyItems.forEach { item ->
            val failed =
                allItemsFailed ||
                        item.id in failedIds

            repository.updateHistory(
                item.copy(
                    status =
                        if (failed) {
                            UploadStatus.FAILED
                        } else {
                            UploadStatus.SUCCESS
                        },
                    errorMessage =
                        if (failed) {
                            workerErrorMessage.ifBlank {
                                "Upload failed"
                            }
                        } else {
                            null
                        },
                    updatedAt = now()
                )
            )
        }

        refreshHistory()

        val successCount =
            if (allItemsFailed) {
                0
            } else {
                (total - failedIds.size)
                    .coerceAtLeast(0)
            }

        _uploadState.value =
            if (successCount > 0) {
                UploadUiState.Error(
                    message =
                        "$successCount of $total photos uploaded. " +
                                "${total - successCount} failed."
                )
            } else {
                UploadUiState.Error(
                    message =
                        workerErrorMessage.ifBlank {
                            "Upload failed. Confirm that the server is running at 10.0.2.2:8080."
                        }
                )
            }
    }

    fun cancelUpload() {
        workManager.cancelUniqueWork(
            UPLOAD_WORK_NAME
        )
    }

    fun resetUploadState() {
        if (_uploadState.value !is UploadUiState.Loading) {
            _uploadState.value =
                UploadUiState.Idle
        }
    }

    // -------------------------------------------------------------------------
    // History
    // -------------------------------------------------------------------------

    fun refreshHistory() {
        _history.value =
            repository.getUploadHistory()
    }

    fun deleteHistoryItem(
        id: String
    ) {
        repository.deleteHistory(id)
        refreshHistory()
    }

    fun clearHistory() {
        repository.clearHistory()
        refreshHistory()
    }

    fun retryUpload(
        item: UploadHistory
    ) {
        if (_uploadState.value is UploadUiState.Loading) {
            return
        }

        repository.deleteHistory(item.id)
        refreshHistory()

        val retryUri =
            Uri.parse(item.localUri)

        addUploadUris(
            uris = listOf(retryUri)
        )

        uploadPhotos(
            uris = listOf(retryUri)
        )
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun createFileName(
        uri: Uri,
        position: Int
    ): String {
        val extension =
            when (getMimeType(uri)) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                else -> "jpg"
            }

        return "photo_${position + 1}_${now()}.$extension"
    }

    private fun getMimeType(
        uri: Uri
    ): String {
        return getApplication<Application>()
            .contentResolver
            .getType(uri)
            ?: "image/jpeg"
    }

    private fun removeCurrentWorkObserver() {
        val workId = observedWorkId
        val observer = currentWorkObserver

        if (
            workId != null &&
            observer != null
        ) {
            workManager
                .getWorkInfoByIdLiveData(workId)
                .removeObserver(observer)
        }

        observedWorkId = null
        currentWorkObserver = null
    }

    private fun now(): Long {
        return System.currentTimeMillis()
    }

    override fun onCleared() {
        removeCurrentWorkObserver()
        super.onCleared()
    }
}