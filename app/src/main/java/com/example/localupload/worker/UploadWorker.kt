package com.example.localupload.worker

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.localupload.data.local.LocalDataSource
import com.example.localupload.data.remote.RemoteDataSource
import com.example.localupload.domain.model.UploadStatus
import java.io.IOException

class UploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {

        private const val TAG = "UploadWorker"

        private const val MAX_RETRY_ATTEMPTS = 3

        // Input keys
        const val KEY_PHOTO_URIS = "PHOTO_URIS"
        const val KEY_HISTORY_IDS = "HISTORY_IDS"

        // Output keys
        const val KEY_FAILED_IDS = "FAILED_IDS"
        const val KEY_ERROR_MESSAGE = "ERROR_MESSAGE"

        const val KEY_COMPRESSION_SUMMARY =
            "COMPRESSION_SUMMARY"

        const val KEY_ORIGINAL_BYTES =
            "ORIGINAL_BYTES"

        const val KEY_COMPRESSED_BYTES =
            "COMPRESSED_BYTES"

        // WorkManager progress keys
        const val KEY_PROGRESS_PHASE =
            "PROGRESS_PHASE"

        const val KEY_PROGRESS_CURRENT =
            "PROGRESS_CURRENT"

        const val KEY_PROGRESS_TOTAL =
            "PROGRESS_TOTAL"

        const val KEY_UPLOAD_SPEED_BPS =
            "UPLOAD_SPEED_BPS"

        const val KEY_ETA_SECONDS =
            "ETA_SECONDS"
    }

    override suspend fun doWork(): Result {
        Log.d(
            TAG,
            "Worker started. Attempt: ${runAttemptCount + 1}"
        )

        val uriStrings = inputData.getStringArray(
            KEY_PHOTO_URIS
        ) ?: return failureResult(
            message = "Photo URIs are missing."
        )

        val historyIds = inputData.getStringArray(
            KEY_HISTORY_IDS
        ) ?: return failureResult(
            message = "Upload history IDs are missing."
        )

        Log.d(
            TAG,
            "Received ${uriStrings.size} photo(s)."
        )

        if (uriStrings.isEmpty()) {
            return failureResult(
                message = "No photos were provided for upload.",
                failedIds = historyIds
            )
        }

        if (uriStrings.size != historyIds.size) {
            return failureResult(
                message = "Photo and history counts do not match.",
                failedIds = historyIds
            )
        }

        val uris = uriStrings.mapNotNull { uriString ->
            runCatching {
                Uri.parse(uriString)
            }.getOrNull()
        }

        if (uris.size != uriStrings.size) {
            return failureResult(
                message = "One or more photo URIs are invalid.",
                failedIds = historyIds
            )
        }

        val total = uris.size

        val localDataSource = LocalDataSource()

        val remoteDataSource = RemoteDataSource(
            applicationContext
        )

        markItemsAsUploading(
            localDataSource = localDataSource,
            historyIds = historyIds
        )

        setProgress(
            workDataOf(
                KEY_PROGRESS_PHASE to "Compressing",
                KEY_PROGRESS_CURRENT to 0,
                KEY_PROGRESS_TOTAL to total,
                KEY_UPLOAD_SPEED_BPS to 0L,
                KEY_ETA_SECONDS to 0L
            )
        )

        return try {
            val uploadResponse =
                remoteDataSource.uploadPhotos(
                    uris = uris,
                    onProgress = {
                            phase,
                            completed,
                            progressTotal,
                            speedBps,
                            etaSeconds ->

                        /*
                         * RemoteDataSource progress callback is not a suspend
                         * lambda, so setProgressAsync is required here.
                         */
                        setProgressAsync(
                            workDataOf(
                                KEY_PROGRESS_PHASE to phase,

                                KEY_PROGRESS_CURRENT to
                                        completed.coerceIn(
                                            minimumValue = 0,
                                            maximumValue =
                                                progressTotal
                                                    .coerceAtLeast(1)
                                        ),

                                KEY_PROGRESS_TOTAL to
                                        progressTotal
                                            .coerceAtLeast(1),

                                KEY_UPLOAD_SPEED_BPS to
                                        speedBps.coerceAtLeast(0L),

                                KEY_ETA_SECONDS to
                                        etaSeconds.coerceAtLeast(0L)
                            )
                        )

                        Log.d(
                            TAG,
                            "$phase: $completed/$progressTotal"
                        )
                    }
                )

            val uploadedCount =
                uploadResponse.totalUploaded.coerceIn(
                    minimumValue = 0,
                    maximumValue = total
                )

            if (uploadedCount < total) {
                val message =
                    "$uploadedCount of $total photos uploaded."

                Log.e(TAG, message)

                markItemsAsFailed(
                    localDataSource = localDataSource,
                    historyIds = historyIds,
                    message = message
                )

                cleanCompressedCache()

                /*
                 * Do not delete Picsum source files here.
                 * They are reusable cache files.
                 */
                return failureResult(
                    message = message,
                    failedIds = historyIds
                )
            }

            markItemsAsSuccessful(
                localDataSource = localDataSource,
                historyIds = historyIds
            )

            setProgress(
                workDataOf(
                    KEY_PROGRESS_PHASE to "Uploading",
                    KEY_PROGRESS_CURRENT to total,
                    KEY_PROGRESS_TOTAL to total,
                    KEY_UPLOAD_SPEED_BPS to 0L,
                    KEY_ETA_SECONDS to 0L
                )
            )

            cleanCompressedCache()

            /*
             * Reusable Picsum cache files are intentionally retained.
             */
            Log.d(
                TAG,
                "Upload completed successfully: $uploadedCount/$total"
            )

            Result.success(
                workDataOf(
                    KEY_COMPRESSION_SUMMARY to
                            uploadResponse.compressionSummary,

                    KEY_ORIGINAL_BYTES to
                            uploadResponse.originalBytes,

                    KEY_COMPRESSED_BYTES to
                            uploadResponse.compressedBytes
                )
            )
        } catch (exception: IOException) {
            cleanCompressedCache()

            val errorMessage =
                exception.localizedMessage
                    ?: "Network connection failed."

            Log.e(
                TAG,
                "Network upload error on attempt " +
                        "${runAttemptCount + 1}: $errorMessage",
                exception
            )

            /*
             * Retry temporary network/server failures, but do not retry
             * indefinitely. runAttemptCount starts at zero.
             */
            if (runAttemptCount + 1 < MAX_RETRY_ATTEMPTS) {
                setProgress(
                    workDataOf(
                        KEY_PROGRESS_PHASE to "Retrying upload",
                        KEY_PROGRESS_CURRENT to 0,
                        KEY_PROGRESS_TOTAL to total,
                        KEY_UPLOAD_SPEED_BPS to 0L,
                        KEY_ETA_SECONDS to 0L
                    )
                )

                Result.retry()
            } else {
                val finalMessage =
                    "Upload failed after $MAX_RETRY_ATTEMPTS attempts: " +
                            errorMessage

                markItemsAsFailed(
                    localDataSource = localDataSource,
                    historyIds = historyIds,
                    message = finalMessage
                )

                failureResult(
                    message = finalMessage,
                    failedIds = historyIds
                )
            }
        } catch (exception: Exception) {
            cleanCompressedCache()

            val errorMessage =
                exception.localizedMessage
                    ?: "Upload failed."

            Log.e(
                TAG,
                "Permanent upload failure: $errorMessage",
                exception
            )

            markItemsAsFailed(
                localDataSource = localDataSource,
                historyIds = historyIds,
                message = errorMessage
            )

            /*
             * Do not delete reusable Picsum cache files after failure.
             */
            failureResult(
                message = errorMessage,
                failedIds = historyIds
            )
        }
    }

    private fun markItemsAsUploading(
        localDataSource: LocalDataSource,
        historyIds: Array<String>
    ) {
        val historyById =
            localDataSource
                .getAll()
                .associateBy { historyItem ->
                    historyItem.id
                }

        historyIds.forEach { historyId ->
            historyById[historyId]?.let { historyItem ->
                localDataSource.update(
                    historyItem.copy(
                        status = UploadStatus.UPLOADING,
                        errorMessage = null,
                        updatedAt = now()
                    )
                )
            }
        }
    }

    private fun markItemsAsSuccessful(
        localDataSource: LocalDataSource,
        historyIds: Array<String>
    ) {
        val historyById =
            localDataSource
                .getAll()
                .associateBy { historyItem ->
                    historyItem.id
                }

        historyIds.forEach { historyId ->
            historyById[historyId]?.let { historyItem ->
                localDataSource.update(
                    historyItem.copy(
                        status = UploadStatus.SUCCESS,
                        errorMessage = null,
                        updatedAt = now()
                    )
                )
            }
        }
    }

    private fun markItemsAsFailed(
        localDataSource: LocalDataSource,
        historyIds: Array<String>,
        message: String
    ) {
        val historyById =
            localDataSource
                .getAll()
                .associateBy { historyItem ->
                    historyItem.id
                }

        historyIds.forEach { historyId ->
            historyById[historyId]?.let { historyItem ->
                localDataSource.update(
                    historyItem.copy(
                        status = UploadStatus.FAILED,
                        errorMessage = message,
                        updatedAt = now()
                    )
                )
            }
        }
    }

    private fun failureResult(
        message: String,
        failedIds: Array<String> = emptyArray()
    ): Result {
        Log.e(TAG, message)

        return Result.failure(
            workDataOf(
                KEY_FAILED_IDS to failedIds,
                KEY_ERROR_MESSAGE to message
            )
        )
    }

    /**
     * Removes only temporary compressed images.
     *
     * Stable Picsum files inside cacheDir/picsum_uploads are not deleted.
     */
    private fun cleanCompressedCache() {
        applicationContext.cacheDir
            .listFiles()
            ?.filter { file ->
                file.isFile &&
                        file.name.startsWith("compressed_") &&
                        file.name.endsWith(".jpg")
            }
            ?.forEach { file ->
                runCatching {
                    file.delete()
                }
            }
    }

    private fun now(): Long {
        return System.currentTimeMillis()
    }
}