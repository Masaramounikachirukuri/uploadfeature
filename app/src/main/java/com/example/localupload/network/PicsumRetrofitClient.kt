package com.example.localupload.network



import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Separate Retrofit client for the Picsum API.
 *
 * This is intentionally separate from RetrofitClient because:
 *
 * RetrofitClient:
 *     Handles photo upload to the local backend server.
 *
 * PicsumRetrofitClient:
 *     Loads the remote photo list from Picsum.
 */
object PicsumRetrofitClient {

    private const val BASE_URL = "https://picsum.photos/"

    private val loggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }

    private val okHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(loggingInterceptor)
            .build()

    val apiService: PicsumApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PicsumApiService::class.java)
    }
}