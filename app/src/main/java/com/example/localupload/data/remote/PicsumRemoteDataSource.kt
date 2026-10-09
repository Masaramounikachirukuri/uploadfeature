package com.example.localupload.data.remote

import android.content.Context
import com.example.localupload.domain.model.Photo
import com.example.localupload.domain.model.PicsumPhoto
import com.example.localupload.network.PicsumRetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

class PicsumRemoteDataSource(
    context: Context
) {

    private val applicationContext = context.applicationContext

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun getPhotos(
        page: Int,
        limit: Int
    ): List<PicsumPhoto> = withContext(Dispatchers.IO) {
        PicsumRetrofitClient.apiService
            .getPhotos(
                page = page,
                limit = limit
            )
            .map { dto ->
                PicsumPhoto(
                    id = dto.id,
                    author = dto.author,
                    width = dto.width,
                    height = dto.height,
                    downloadUrl = dto.downloadUrl,
                    isSelected = false
                )
            }
    }

    suspend fun downloadPhotos(
        photos: List<PicsumPhoto>
    ): List<Photo> = withContext(Dispatchers.IO) {
        val folder = File(
            applicationContext.filesDir,
            PICSUM_FOLDER_NAME
        )

        if (!folder.exists()) {
            val folderCreated = folder.mkdirs()

            if (!folderCreated && !folder.exists()) {
                return@withContext emptyList()
            }
        }

        photos.mapNotNull { photo ->
            downloadSinglePhoto(
                photo = photo,
                folder = folder
            )
        }
    }

    private fun downloadSinglePhoto(
        photo: PicsumPhoto,
        folder: File
    ): Photo? {
        return try {
            val safePhotoId = photo.id.replace(
                regex = Regex("[^a-zA-Z0-9_-]"),
                replacement = "_"
            )

            val fileName =
                "picsum_${safePhotoId}_${System.currentTimeMillis()}.jpg"

            val destinationFile = File(
                folder,
                fileName
            )

            val request = Request.Builder()
                .url(photo.downloadUrl)
                .get()
                .build()

            downloadClient.newCall(request)
                .execute()
                .use { response ->
                    if (!response.isSuccessful) {
                        return null
                    }

                    val responseBody = response.body
                        ?: return null

                    destinationFile.outputStream()
                        .buffered()
                        .use { outputStream ->
                            responseBody.byteStream()
                                .buffered()
                                .use { inputStream ->
                                    inputStream.copyTo(outputStream)
                                }
                        }
                }

            if (
                !destinationFile.exists() ||
                destinationFile.length() == 0L
            ) {
                destinationFile.delete()
                return null
            }

            Photo(
                uriString = destinationFile.absolutePath,
                mimeType = "image/jpeg",
                displayName = fileName
            )
        } catch (exception: Exception) {
            exception.printStackTrace()
            null
        }
    }

    companion object {
        private const val PICSUM_FOLDER_NAME = "picsum_photos"
    }
}