package com.example.localupload.data.remote

import android.content.Context
import android.net.Uri
import com.example.localupload.domain.model.Photo
import com.example.localupload.domain.model.UploadError
import com.example.localupload.domain.model.UploadResult
import com.example.localupload.network.RetrofitClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class RemoteDataSource(
    private val context: Context
) {

    suspend fun uploadPhotos(
        photos: List<Photo>
    ): UploadResult {
        return try {
            val parts = mutableListOf<MultipartBody.Part>()

            photos.forEachIndexed { index, photo ->
                val bytes = readPhotoBytes(photo)

                if (bytes != null && bytes.isNotEmpty()) {
                    val requestBody = bytes.toRequestBody(
                        photo.mimeType.toMediaTypeOrNull()
                    )

                    val part = MultipartBody.Part.createFormData(
                        name = "files",
                        filename = photo.displayName.ifBlank {
                            "photo_${index + 1}.jpg"
                        },
                        body = requestBody
                    )

                    parts.add(part)
                }
            }

            if (parts.isEmpty()) {
                return UploadResult.Failure(
                    UploadError.FileReadError(
                        "Failed to read photo data from local storage."
                    )
                )
            }

            val response = RetrofitClient.apiService.uploadPhotos(parts)

            if (response.isSuccessful) {
                UploadResult.Success(
                    uploadedCount = parts.size
                )
            } else {
                UploadResult.Failure(
                    UploadError.ServerError(
                        "HTTP ${response.code()}: ${response.message()}"
                    )
                )
            }
        } catch (exception: java.net.ConnectException) {
            UploadResult.Failure(
                UploadError.NetworkError(
                    "Cannot connect to the upload server: " +
                            (exception.message ?: "Connection refused")
                )
            )
        } catch (exception: java.net.SocketTimeoutException) {
            UploadResult.Failure(
                UploadError.NetworkError(
                    "Upload connection timed out: " +
                            (exception.message ?: "Timeout")
                )
            )
        } catch (exception: retrofit2.HttpException) {
            UploadResult.Failure(
                UploadError.ServerError(
                    "HTTP ${exception.code()}: ${exception.message()}"
                )
            )
        } catch (exception: Exception) {
            UploadResult.Failure(
                UploadError.Unknown(
                    exception.message ?: "Unknown upload error"
                )
            )
        }
    }

    private fun readPhotoBytes(
        photo: Photo
    ): ByteArray? {
        return try {
            when {
                photo.uriString.startsWith("content://") -> {
                    val uri = Uri.parse(photo.uriString)

                    context.contentResolver
                        .openInputStream(uri)
                        ?.use { inputStream ->
                            inputStream.readBytes()
                        }
                }

                photo.uriString.startsWith("file://") -> {
                    val uri = Uri.parse(photo.uriString)
                    val path = uri.path ?: return null
                    val file = File(path)

                    if (file.exists() && file.isFile) {
                        file.readBytes()
                    } else {
                        null
                    }
                }

                else -> {
                    val file = File(photo.uriString)

                    if (file.exists() && file.isFile) {
                        file.readBytes()
                    } else {
                        null
                    }
                }
            }
        } catch (exception: Exception) {
            null
        }
    }
}