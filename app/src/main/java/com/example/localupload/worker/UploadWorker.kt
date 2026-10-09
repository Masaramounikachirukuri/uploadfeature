package com.example.localupload.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.localupload.data.local.LocalDataSource
import com.example.localupload.data.remote.PicsumRemoteDataSource
import com.example.localupload.data.remote.RemoteDataSource
import com.example.localupload.data.repository.PhotoRepositoryImpl
import com.example.localupload.database.AppDatabase
import com.example.localupload.domain.model.UploadError
import com.example.localupload.domain.model.UploadResult
import com.example.localupload.domain.usecase.UploadPhotoUseCase

class UploadWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "photo_upload_work"
        const val KEY_UPLOADED_COUNT = "uploaded_count"
        const val KEY_ERROR_MESSAGE = "error_message"
    }

    override suspend fun doWork(): Result {

        val db = AppDatabase.getInstance(context)

        val localSource = LocalDataSource(db)
        val remoteSource = RemoteDataSource(context)
        val picsumRemoteDataSource =
            PicsumRemoteDataSource(context)

        val repository = PhotoRepositoryImpl(
            localDataSource = localSource,
            remoteDataSource = remoteSource,
            picsumRemoteDataSource = picsumRemoteDataSource
        )

        val uploadUseCase =
            UploadPhotoUseCase(repository)

        return when (val result = uploadUseCase()) {

            is UploadResult.Success -> {
                Result.success(
                    workDataOf(
                        KEY_UPLOADED_COUNT to result.uploadedCount
                    )
                )
            }

            is UploadResult.Failure -> {

                val errorMessage =
                    when (val error = result.reason) {

                        is UploadError.NoPendingPhotos ->
                            "No photos to upload."

                        is UploadError.ValidationFailed ->
                            error.violations.joinToString("; ")

                        is UploadError.NetworkError ->
                            "Network error: ${error.message}"

                        is UploadError.ServerError ->
                            "Server error: ${error.message}"

                        is UploadError.FileReadError ->
                            "File read error: ${error.message}"

                        is UploadError.Unknown ->
                            "Upload failed: ${error.message}"
                    }

                Result.failure(
                    workDataOf(
                        KEY_ERROR_MESSAGE to errorMessage
                    )
                )
            }
        }
    }
}