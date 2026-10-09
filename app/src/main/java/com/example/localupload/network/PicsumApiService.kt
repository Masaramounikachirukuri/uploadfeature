package com.example.localupload.network

import com.example.localupload.network.dto.PicsumPhotoDto
import retrofit2.http.GET
import retrofit2.http.Query

interface PicsumApiService {

    @GET("v2/list")
    suspend fun getPhotos(
        @Query("page") page: Int,
        @Query("limit") limit: Int
    ): List<PicsumPhotoDto>
}