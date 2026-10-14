package com.example.localupload.data.remote

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.localupload.domain.model.Photo
import com.example.localupload.domain.model.UploadResponse
import com.example.localupload.domain.model.UploadResult
import com.example.localupload.network.RetrofitClient
import com.example.localupload.util.ImageCompressor
import com.example.localupload.util.UploadNetworkMonitor
import com.example.localupload.util.UploadSpeedTracker
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class RemoteDataSource(
    private val context: Context
) {

    private val galleryApi = RetrofitClient.galleryApi
    private val uploadApi = RetrofitClient.uploadApi
    private val gson = Gson()

    private val networkMonitor =
        UploadNetworkMonitor(context)

    // -------------------------------------------------------------------------
    // Picsum gallery
    // -------------------------------------------------------------------------

    suspend fun fetchPhotos(
        page: Int,
        limit: Int
    ): List<Photo> {
        return galleryApi.getPhotos(
            page = page,
            limit = limit
        )
    }

    // -------------------------------------------------------------------------
    // Picsum cache
    // -------------------------------------------------------------------------

    /**
     * Converts selected remote Picsum photos into local FileProvider URIs.
     *
     * If picsum_<id>.jpg already exists and is valid, the cached file is reused.
     * Otherwise, the photo is downloaded once and saved in the app cache.
     */
    suspend fun downloadPhotosToCache(
        photos: List<Photo>
    ): List<Uri> = withContext(Dispatchers.IO) {

        val uniquePhotos = photos.distinctBy { photo ->
            photo.id
        }

        if (uniquePhotos.isEmpty()) {
            return@withContext emptyList()
        }

        val cacheDirectory =
            getPicsumCacheDirectory()

        val downloadSemaphore =
            Semaphore(MAX_PARALLEL_DOWNLOADS)

        coroutineScope {
            uniquePhotos.map { photo ->
                async(Dispatchers.IO) {
                    downloadSemaphore.withPermit {
                        val cachedFile =
                            resolvePhotoCacheFile(
                                photo = photo,
                                cacheDirectory = cacheDirectory
                            )

                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            cachedFile
                        )
                    }
                }
            }.awaitAll()
        }
    }

    /**
     * Returns a valid cached file or downloads the image if it is missing.
     */
    private fun resolvePhotoCacheFile(
        photo: Photo,
        cacheDirectory: File
    ): File {
        val safePhotoId =
            sanitizePhotoId(photo.id)

        val cachedFile = File(
            cacheDirectory,
            "picsum_$safePhotoId.jpg"
        )

        if (isUsableCacheFile(cachedFile)) {
            cachedFile.setLastModified(
                System.currentTimeMillis()
            )

            return cachedFile
        }

        if (cachedFile.exists()) {
            runCatching {
                cachedFile.delete()
            }
        }

        return downloadPhoto(
            photo = photo,
            destinationFile = cachedFile
        )
    }

    /**
     * Downloads one Picsum image.
     *
     * The image is first written to a .downloading file. The final cache file
     * is created only after the complete image has been downloaded.
     */
    private fun downloadPhoto(
        photo: Photo,
        destinationFile: File
    ): File {
        val imageUrl =
            photo.thumbnailUrl.trim()

        if (imageUrl.isBlank()) {
            throw IllegalArgumentException(
                "Image URL is missing for Picsum photo ${photo.id}."
            )
        }

        if (
            !imageUrl.startsWith("https://") &&
            !imageUrl.startsWith("http://")
        ) {
            throw IllegalArgumentException(
                "Invalid image URL for Picsum photo ${photo.id}."
            )
        }

        val parentDirectory =
            destinationFile.parentFile
                ?: throw IOException(
                    "Picsum cache directory is unavailable."
                )

        val temporaryFile = File(
            parentDirectory,
            "${destinationFile.name}.downloading"
        )

        if (temporaryFile.exists()) {
            temporaryFile.delete()
        }

        var connection: HttpURLConnection? = null

        try {
            connection =
                URL(imageUrl).openConnection()
                        as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout =
                DOWNLOAD_CONNECT_TIMEOUT
            connection.readTimeout =
                DOWNLOAD_READ_TIMEOUT
            connection.instanceFollowRedirects = true
            connection.useCaches = false
            connection.doInput = true

            connection.setRequestProperty(
                "Accept",
                "image/*"
            )

            connection.setRequestProperty(
                "User-Agent",
                "LocalUploadFeature/1.0"
            )

            connection.connect()

            val responseCode =
                connection.responseCode

            if (responseCode !in 200..299) {
                throw IOException(
                    "Picsum returned HTTP $responseCode."
                )
            }

            val contentType =
                connection.contentType
                    ?.substringBefore(";")
                    ?.trim()
                    .orEmpty()

            if (
                contentType.isNotBlank() &&
                !contentType.startsWith("image/")
            ) {
                throw IOException(
                    "Picsum returned unsupported content: $contentType."
                )
            }

            BufferedInputStream(
                connection.inputStream
            ).use { inputStream ->

                FileOutputStream(
                    temporaryFile
                ).use { outputStream ->

                    inputStream.copyTo(
                        out = outputStream,
                        bufferSize = DOWNLOAD_BUFFER_SIZE
                    )

                    outputStream.flush()
                }
            }

            if (!isUsableCacheFile(temporaryFile)) {
                throw IOException(
                    "Downloaded Picsum image is empty."
                )
            }

            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val renamed =
                temporaryFile.renameTo(destinationFile)

            if (!renamed) {
                temporaryFile.inputStream().use { input ->
                    destinationFile.outputStream().use { output ->
                        input.copyTo(
                            out = output,
                            bufferSize = DOWNLOAD_BUFFER_SIZE
                        )
                    }
                }

                temporaryFile.delete()
            }

            if (!isUsableCacheFile(destinationFile)) {
                throw IOException(
                    "Unable to create the Picsum cache file."
                )
            }

            return destinationFile
        } catch (exception: Exception) {
            runCatching {
                temporaryFile.delete()
            }

            if (!isUsableCacheFile(destinationFile)) {
                runCatching {
                    destinationFile.delete()
                }
            }

            throw IOException(
                "Unable to prepare Picsum photo ${photo.id}: " +
                        (
                                exception.localizedMessage
                                    ?: "Unknown download error"
                                ),
                exception
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun getPicsumCacheDirectory(): File {
        val cacheDirectory = File(
            context.cacheDir,
            PICSUM_CACHE_DIRECTORY
        )

        if (!cacheDirectory.exists()) {
            val created =
                cacheDirectory.mkdirs()

            if (!created && !cacheDirectory.exists()) {
                throw IllegalStateException(
                    "Unable to create the Picsum cache directory."
                )
            }
        }

        return cacheDirectory
    }

    private fun sanitizePhotoId(
        photoId: String
    ): String {
        return photoId
            .replace(
                regex = Regex("[^a-zA-Z0-9_-]"),
                replacement = "_"
            )
            .take(MAX_CACHE_ID_LENGTH)
            .ifBlank {
                throw IllegalArgumentException(
                    "Picsum photo ID is missing."
                )
            }
    }

    private fun isUsableCacheFile(
        file: File
    ): Boolean {
        return file.exists() &&
                file.isFile &&
                file.canRead() &&
                file.length() > 0L
    }

    /**
     * Clears reusable Picsum cache files.
     *
     * Do not call this automatically after upload success.
     */
    suspend fun clearPicsumUploadCache() {
        withContext(Dispatchers.IO) {
            val cacheDirectory = File(
                context.cacheDir,
                PICSUM_CACHE_DIRECTORY
            )

            cacheDirectory
                .listFiles()
                ?.forEach { file ->
                    runCatching {
                        file.delete()
                    }
                }

            if (
                cacheDirectory
                    .listFiles()
                    .isNullOrEmpty()
            ) {
                runCatching {
                    cacheDirectory.delete()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Upload
    // -------------------------------------------------------------------------

    /**
     * Compresses local images and uploads them in limited parallel batches.
     */
    suspend fun uploadPhotos(
        uris: List<Uri>,
        onProgress: (
            (
            phase: String,
            completed: Int,
            total: Int,
            speedBps: Long,
            etaSeconds: Long
        ) -> Unit
        )? = null
    ): UploadResponse {

        val uniqueUris =
            uris.distinctBy { uri ->
                uri.toString()
            }

        if (uniqueUris.isEmpty()) {
            return UploadResponse(
                message = "No photos selected.",
                totalUploaded = 0,
                files = emptyList(),
                originalBytes = 0L,
                compressedBytes = 0L
            )
        }

        val total = uniqueUris.size

        val networkProfile =
            networkMonitor.networkProfile()

        val batchSize =
            networkMonitor
                .batchSize(networkProfile)
                .coerceAtLeast(1)

        val maxParallelBatches =
            networkMonitor
                .maxParallelBatches(networkProfile)
                .coerceAtLeast(1)

        val jpegQuality =
            networkMonitor.jpegQuality(
                networkProfile
            )

        val compressor = ImageCompressor(
            context = context,
            quality = jpegQuality
        )

        val originalBytes =
            calculateOriginalSize(uniqueUris)

        onProgress?.invoke(
            "Compressing",
            0,
            total,
            0L,
            0L
        )

        val temporaryFiles =
            compressor.compressAll(
                uniqueUris
            ) { completed, progressTotal ->

                onProgress?.invoke(
                    "Compressing",
                    completed.coerceIn(
                        minimumValue = 0,
                        maximumValue =
                            progressTotal.coerceAtLeast(1)
                    ),
                    progressTotal,
                    0L,
                    0L
                )
            }

        if (temporaryFiles.isEmpty()) {
            throw IllegalStateException(
                "Photo compression did not create any files."
            )
        }

        val compressedBytes =
            temporaryFiles.sumOf { file ->
                file.length().coerceAtLeast(0L)
            }

        val chunks =
            temporaryFiles.chunked(batchSize)

        var uploadedSoFar = 0
        var bytesSentSoFar = 0L

        val stateMutex = Mutex()

        val uploadResults =
            mutableListOf<UploadResult>()

        val speedTracker =
            UploadSpeedTracker()

        speedTracker.start()

        try {
            onProgress?.invoke(
                "Uploading",
                0,
                total,
                0L,
                0L
            )

            coroutineScope {
                chunks
                    .chunked(maxParallelBatches)
                    .forEach { parallelGroup ->

                        parallelGroup
                            .map { chunkFiles ->
                                async(Dispatchers.IO) {
                                    val chunkBytes =
                                        chunkFiles.sumOf { file ->
                                            file.length()
                                                .coerceAtLeast(0L)
                                        }

                                    val response =
                                        uploadBatch(chunkFiles)

                                    val progressSnapshot =
                                        stateMutex.withLock {
                                            uploadResults.addAll(
                                                response.files
                                            )

                                            val uploadedInBatch =
                                                response.totalUploaded
                                                    .takeIf { count ->
                                                        count > 0
                                                    }
                                                    ?: chunkFiles.size

                                            uploadedSoFar =
                                                (
                                                        uploadedSoFar +
                                                                uploadedInBatch
                                                        ).coerceAtMost(total)

                                            bytesSentSoFar =
                                                (
                                                        bytesSentSoFar +
                                                                chunkBytes
                                                        ).coerceAtMost(
                                                        compressedBytes
                                                    )

                                            uploadedSoFar to
                                                    bytesSentSoFar
                                        }

                                    speedTracker.recordBytes(
                                        chunkBytes
                                    )

                                    val completedCount =
                                        progressSnapshot.first

                                    val sentBytes =
                                        progressSnapshot.second

                                    val remainingBytes =
                                        (
                                                compressedBytes -
                                                        sentBytes
                                                ).coerceAtLeast(0L)

                                    val speedBps =
                                        speedTracker.speedBps()

                                    val etaSeconds =
                                        speedTracker.etaSeconds(
                                            remainingBytes
                                        )

                                    onProgress?.invoke(
                                        "Uploading",
                                        completedCount,
                                        total,
                                        speedBps,
                                        etaSeconds
                                    )
                                }
                            }
                            .awaitAll()
                    }
            }
        } finally {
            /*
             * These are compressed temporary files.
             * Reusable Picsum source files are not deleted here.
             */
            temporaryFiles.forEach { file ->
                runCatching {
                    file.delete()
                }
            }
        }

        return UploadResponse(
            message =
                "$uploadedSoFar of $total photo(s) uploaded successfully",
            totalUploaded = uploadedSoFar,
            files = uploadResults.toList(),
            originalBytes = originalBytes,
            compressedBytes = compressedBytes
        )
    }

    private suspend fun calculateOriginalSize(
        uris: List<Uri>
    ): Long {
        return withContext(Dispatchers.IO) {
            uris.sumOf { uri ->
                try {
                    context.contentResolver
                        .openFileDescriptor(uri, "r")
                        ?.use { descriptor ->
                            descriptor.statSize
                                .coerceAtLeast(0L)
                        }
                        ?: 0L
                } catch (_: Exception) {
                    0L
                }
            }
        }
    }

    suspend fun uploadSinglePhoto(
        uri: Uri
    ): UploadResponse {
        val jpegQuality =
            networkMonitor.jpegQuality()

        val compressor = ImageCompressor(
            context = context,
            quality = jpegQuality
        )

        val temporaryFile =
            withContext(Dispatchers.IO) {
                compressor.compress(uri)
            }

        return try {
            uploadBatch(
                files = listOf(temporaryFile)
            )
        } finally {
            runCatching {
                temporaryFile.delete()
            }
        }
    }

    // -------------------------------------------------------------------------
    // Multipart upload
    // -------------------------------------------------------------------------

    private suspend fun uploadBatch(
        files: List<File>
    ): UploadResponse {

        if (files.isEmpty()) {
            return UploadResponse(
                message = "No files in the upload batch.",
                totalUploaded = 0,
                files = emptyList(),
                originalBytes = 0L,
                compressedBytes = 0L
            )
        }

        val metadataJson =
            gson.toJson(
                mapOf(
                    "uploaded_by" to "user_123",
                    "album_name" to "My Album",
                    "tags" to listOf(
                        "android",
                        "upload"
                    )
                )
            )

        val metadataBody =
            metadataJson.toRequestBody(
                "application/json"
                    .toMediaTypeOrNull()
            )

        val multipartParts =
            files.mapIndexed { index, file ->

                if (
                    !file.exists() ||
                    file.length() <= 0L
                ) {
                    throw IllegalStateException(
                        "Upload file is missing or empty: ${file.name}"
                    )
                }

                MultipartBody.Part.createFormData(
                    name = "files[]",
                    filename =
                        "photo_${index + 1}.jpg",
                    body = file.asRequestBody(
                        "image/jpeg"
                            .toMediaTypeOrNull()
                    )
                )
            }

        val response =
            uploadApi.uploadPhotos(
                metadata = metadataBody,
                photos = multipartParts
            )

        if (response.isSuccessful) {
            return response.body()
                ?: throw IllegalStateException(
                    "The upload server returned an empty response."
                )
        }

        val errorMessage =
            runCatching {
                response.errorBody()?.string()
            }
                .getOrNull()
                ?.takeIf { message ->
                    message.isNotBlank()
                }
                ?: "Unknown server error"

        /*
         * Let WorkManager retry temporary server failures.
         */
        if (response.code() in 500..599) {
            throw IOException(
                "Temporary server error HTTP " +
                        "${response.code()}: $errorMessage"
            )
        }

        throw IllegalStateException(
            "Upload failed with HTTP " +
                    "${response.code()}: $errorMessage"
        )
    }

    companion object {

        private const val PICSUM_CACHE_DIRECTORY =
            "picsum_uploads"

        private const val MAX_PARALLEL_DOWNLOADS =
            3

        private const val MAX_CACHE_ID_LENGTH =
            80

        private const val DOWNLOAD_CONNECT_TIMEOUT =
            20_000

        private const val DOWNLOAD_READ_TIMEOUT =
            30_000

        private const val DOWNLOAD_BUFFER_SIZE =
            8 * 1024
    }
}