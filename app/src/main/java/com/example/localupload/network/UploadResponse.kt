package com.example.localupload.network

import com.google.gson.annotations.SerializedName

/**
 * Server response model for the photo upload endpoint.
 *
 * Adapt field names to match your actual server API response JSON.
 * Example server response:
 * {
 *   "success": true,
 *   "message": "3 file(s) uploaded successfully",
 *   "uploaded_count": 3
 * }
 */
data class UploadResponse(

    @SerializedName("success")
    val success: Boolean = false,

    @SerializedName("message")
    val message: String = "",

    @SerializedName("uploaded_count")
    val uploadedCount: Int = 0
)
