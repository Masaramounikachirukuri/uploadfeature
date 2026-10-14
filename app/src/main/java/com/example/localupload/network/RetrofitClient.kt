package com.example.localupload.network

import com.example.localupload.BuildConfig
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    /**
     * Picsum API
     */
    private const val GALLERY_BASE_URL =
        "https://picsum.photos/"

    /**
     * Local upload server
     *
     * Android Emulator:
     * 10.0.2.2 -> localhost of your Mac
     */
    private const val UPLOAD_BASE_URL =
        "http://10.0.2.2:8080/"

    // --------------------------------------------------------------------
    // Logging
    // --------------------------------------------------------------------

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level =
                if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
        }
    }

    // --------------------------------------------------------------------
    // Gallery Client
    // --------------------------------------------------------------------

    private val galleryClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(
                15,
                TimeUnit.SECONDS
            )
            .readTimeout(
                30,
                TimeUnit.SECONDS
            )
            .writeTimeout(
                30,
                TimeUnit.SECONDS
            )
            .addInterceptor(
                loggingInterceptor
            )
            .build()
    }

    // --------------------------------------------------------------------
    // Upload Client
    // --------------------------------------------------------------------

    private val uploadConnectionPool by lazy {
        ConnectionPool(
            maxIdleConnections = 10,
            keepAliveDuration = 5,
            timeUnit = TimeUnit.MINUTES
        )
    }

    private val uploadClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(
                uploadConnectionPool
            )
            .protocols(
                listOf(
                    Protocol.HTTP_1_1
                )
            )
            .connectTimeout(
                30,
                TimeUnit.SECONDS
            )
            .readTimeout(
                60,
                TimeUnit.SECONDS
            )
            .writeTimeout(
                90,
                TimeUnit.SECONDS
            )
            .retryOnConnectionFailure(true)
            .addInterceptor(
                loggingInterceptor
            )
            .build()
    }

    // --------------------------------------------------------------------
    // Gallery API
    // --------------------------------------------------------------------

    val galleryApi: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(GALLERY_BASE_URL)
            .client(galleryClient)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(ApiService::class.java)
    }

    // --------------------------------------------------------------------
    // Upload API
    // --------------------------------------------------------------------

    val uploadApi: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(UPLOAD_BASE_URL)
            .client(uploadClient)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(ApiService::class.java)
    }
}