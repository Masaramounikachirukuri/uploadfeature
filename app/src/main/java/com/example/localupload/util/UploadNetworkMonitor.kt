package com.example.localupload.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * UploadNetworkMonitor — real-time network-type detection for adaptive upload tuning.
 *
 * ── Why network-adaptive upload? ─────────────────────────────────────────────
 *
 *  Upload performance varies dramatically by connection type:
 *   • WiFi    : typically 20–100+ Mbps upload, low latency, unlimited data
 *   • 4G/LTE  : typically 5–20 Mbps upload, higher latency, metered data
 *   • 3G      : typically 0.5–5 Mbps upload, high latency, metered data
 *
 *  Using the same batch size and parallel count on all connection types wastes:
 *   - WiFi users: underutilises bandwidth (could push more batches in parallel)
 *   - Cellular users: wastes data (larger images = more bytes on metered plan)
 *
 *  This monitor exposes [networkProfile] which [RemoteDataSource] consults to:
 *   a. Tune [BATCH_SIZE] — how many images per multipart request
 *   b. Tune [MAX_PARALLEL_BATCHES] — how many requests run concurrently
 *   c. Choose JPEG quality (WiFi = 80, Cellular = 60) → less data on mobile
 *
 * ── [NetworkProfile] values ───────────────────────────────────────────────────
 *
 *  [NetworkProfile.WIFI]      — fast, unmetered → aggressive batching
 *  [NetworkProfile.CELLULAR]  — slower, metered  → conservative batching + lower quality
 *  [NetworkProfile.UNKNOWN]   — VPN / Ethernet / unknown — use conservative defaults
 *
 * ── Observable [networkFlow] ─────────────────────────────────────────────────
 *
 *  Emits the current [NetworkProfile] as a cold [Flow] via [callbackFlow].
 *  [distinctUntilChanged] prevents duplicate emissions when the profile hasn't changed.
 *  The [UploadWorker] and [RemoteDataSource] can call [networkProfile] (snapshot) without
 *  needing to collect the flow if they only need the current value at upload-start time.
 */
class UploadNetworkMonitor(context: Context) {

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    // ── Network profiles ──────────────────────────────────────────────────────

    enum class NetworkProfile {
        /** WiFi or unmetered — use aggressive batching + higher quality */
        WIFI,
        /** Cellular / metered — use conservative batching + lower quality */
        CELLULAR,
        /** VPN, Ethernet, or indeterminate — use conservative defaults */
        UNKNOWN
    }

    // ── Upload tuning constants per profile ───────────────────────────────────

    /**
     * Returns the number of images per multipart request for this profile.
     *
     *  WIFI     → 8 files/batch  — server receives fewer large requests;
     *                               each is filled to 8 images to reduce round-trips.
     *  CELLULAR → 3 files/batch  — smaller payload = less data per request;
     *                               also easier for server to parse on slow uplink.
     *  UNKNOWN  → 5 files/batch  — same as previous default; safe for any connection.
     */
    fun batchSize(profile: NetworkProfile = networkProfile()): Int = when (profile) {
        NetworkProfile.WIFI     -> 8
        NetworkProfile.CELLULAR -> 3
        NetworkProfile.UNKNOWN  -> 5
    }

    /**
     * Returns max parallel batch requests for this profile.
     *
     *  WIFI     → 4  — HTTP/2 multiplexes all 4 over one socket; OkHttp connection pool
     *                  has 10 slots so headroom is plentiful.
     *  CELLULAR → 2  — Keeps total in-flight data low; cell radios perform best with
     *                  fewer concurrent streams (avoids TCP window fragmentation).
     *  UNKNOWN  → 3  — Previous default; works acceptably for any connection type.
     */
    fun maxParallelBatches(profile: NetworkProfile = networkProfile()): Int = when (profile) {
        NetworkProfile.WIFI     -> 4
        NetworkProfile.CELLULAR -> 2
        NetworkProfile.UNKNOWN  -> 3
    }

    /**
     * Returns the JPEG compression quality for images uploaded on this profile.
     *
     *  WIFI     → 80  — higher quality; bandwidth is plentiful and unmetered.
     *  CELLULAR → 60  — lower quality; saves ~25% bytes on metered data.
     *                   Quality 60 is still visually acceptable for gallery photos
     *                   when displayed at ≤1024px — the resolution limit applied
     *                   by [ImageCompressor] before encoding.
     *  UNKNOWN  → 72  — previous default; balanced for any connection.
     */
    fun jpegQuality(profile: NetworkProfile = networkProfile()): Int = when (profile) {
        NetworkProfile.WIFI     -> 80
        NetworkProfile.CELLULAR -> 60
        NetworkProfile.UNKNOWN  -> 72
    }

    // ── Snapshot API ──────────────────────────────────────────────────────────

    /**
     * Returns the current [NetworkProfile] synchronously.
     *
     * Reads [ConnectivityManager.getNetworkCapabilities] on the calling thread.
     * Safe to call from any thread; ConnectivityManager is thread-safe.
     * Returns [NetworkProfile.UNKNOWN] if no active network or capabilities unavailable.
     */
    fun networkProfile(): NetworkProfile {
        val network  = connectivityManager.activeNetwork          ?: return NetworkProfile.UNKNOWN
        val caps     = connectivityManager.getNetworkCapabilities(network) ?: return NetworkProfile.UNKNOWN

        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)      -> NetworkProfile.WIFI
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)  -> NetworkProfile.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)  -> NetworkProfile.UNKNOWN
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)       -> NetworkProfile.UNKNOWN
            else                                                        -> NetworkProfile.UNKNOWN
        }
    }

    // ── Reactive API ──────────────────────────────────────────────────────────

    /**
     * Hot [Flow] that emits the [NetworkProfile] whenever the active network changes.
     *
     * Uses [callbackFlow] with [ConnectivityManager.NetworkCallback] so it integrates
     * with the Android system callback API without polling.
     *
     * Emits immediately on collection with the current profile so collectors always
     * have an initial value.
     *
     * Usage:
     * ```kotlin
     * networkMonitor.networkFlow
     *     .distinctUntilChanged()
     *     .collect { profile -> ... }
     * ```
     */
    val networkFlow: Flow<NetworkProfile> = callbackFlow {
        // Emit current state immediately
        trySend(networkProfile())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(networkProfile())
            }
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(networkProfile())
            }
            override fun onLost(network: Network) {
                trySend(networkProfile())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()
}
