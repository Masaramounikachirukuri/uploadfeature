package com.example.localupload.network

import com.example.localupload.domain.model.Photo
import com.example.localupload.domain.model.UploadResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Query

/**
 * Retrofit endpoints used by the application.
 *
 * Gallery base URL:
 * https://picsum.photos/
 *
 * Upload base URL:
 * http://10.0.2.2:8080/api/
 */
interface ApiService {

    /**
     * Fetches Picsum photos.
     *
     * Final request:
     * GET https://picsum.photos/v2/list?page=1&limit=30
     */
    @GET("v2/list")
    suspend fun getPhotos(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 30
    ): List<Photo>

    /**
     * Uploads compressed photos to the local backend.
     *
     * Final request:
     * POST http://10.0.2.2:8080/api/upload
     *
     * The multipart request contains:
     * - metadata: JSON request body
     * - photos: multipart image files
     */
    @Multipart
    @POST("upload")
    suspend fun uploadPhotos(
        @Part("metadata") metadata: RequestBody,
        @Part photos: List<MultipartBody.Part>
    ): Response<UploadResponse>
}