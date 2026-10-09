package com.example.localupload.network.dto

import com.google.gson.annotations.SerializedName

data class PicsumPhotoDto(
    val id: String,
    val author: String,
    val width: Int,
    val height: Int,
    val url: String,

    @SerializedName("download_url")
    val downloadUrl: String
)