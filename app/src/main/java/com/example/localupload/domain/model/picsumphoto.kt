package com.example.localupload.domain.model



/**
 * Represents a remote photo returned by the Picsum API.
 *
 * This model is used only for displaying and selecting remote photos.
 * Once selected, the image is downloaded and converted into the existing
 * Photo model before being stored in Room.
 */
data class PicsumPhoto(
    val id: String,
    val author: String,
    val width: Int,
    val height: Int,
    val downloadUrl: String,
    val isSelected: Boolean = false
)
