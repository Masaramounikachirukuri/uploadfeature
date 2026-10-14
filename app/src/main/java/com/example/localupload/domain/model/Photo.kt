package com.example.localupload.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Photo represents a photo fetched from the gallery API.
 *
 * The gallery API is now served by the local test server at
 * http://10.0.2.2:8080/api/v2/list (ADB reverse tunnel) instead of
 * https://picsum.photos/ directly.
 *
 * Reason: picsum.photos external DNS lookup fails when Zscaler/VPN intercepts
 * the emulator's network. The ADB reverse tunnel (adb reverse tcp:8080 tcp:8080)
 * bypasses all proxy/VPN interception by routing through the USB/ADB channel.
 *
 * The local server returns the same JSON schema as picsum.photos /v2/list, with
 * [downloadUrl] pointing to https://picsum.photos/id/{id}/... CDN URLs.
 * Coil loads images from [downloadUrl] / [thumbnailUrl] — if the CDN is also
 * unreachable, Coil will show a broken-image placeholder (acceptable fallback).
 */
data class Photo(
    val id: String,
    val author: String,
    val width: Int,
    val height: Int,
    val url: String,
    @SerializedName("download_url") val downloadUrl: String
) {
    /**
     * Thumbnail URL for grid cells.
     * Uses [downloadUrl] directly — the local server supplies a valid CDN URL
     * (e.g. https://picsum.photos/id/10/1920/1080).
     * No hardcoded "picsum.photos" hostname in code — if the server returns a
     * different CDN URL in the future, thumbnails update without a code change.
     */
    val thumbnailUrl: String get() = downloadUrl

    /**
     * Full resolution URL for the detail view.
     * Same as [thumbnailUrl] — the server returns 1920×1080 images which are
     * sufficient for both grid and detail view at current screen densities.
     */
    val fullUrl: String get() = downloadUrl
}
