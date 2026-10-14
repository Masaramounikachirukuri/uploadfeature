package com.example.localupload.util

import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToLong

/**
 * UploadSpeedTracker — lightweight rolling-window upload speed meter.
 *
 * ── Why a rolling window? ─────────────────────────────────────────────────────
 *
 *  A simple "total bytes / elapsed time" average gives misleading results:
 *   - The first batch completes after TCP slow-start, pulling the average down.
 *   - Later batches run at full pipe speed, but the average barely moves.
 *
 *  A rolling window of the last [WINDOW_MS] milliseconds gives a speed reading
 *  that reflects CURRENT throughput — the user sees the real upload rate, not
 *  a dragging historical average.
 *
 * ── Thread safety ─────────────────────────────────────────────────────────────
 *
 *  [recordBytes] is called from multiple [Dispatchers.IO] coroutines concurrently
 *  (one per parallel batch). [AtomicLong] for the total counter is lock-free.
 *  The sample list is protected by [synchronized] — tiny critical section, each
 *  call adds one [Sample] object and optionally removes stale entries.
 *
 * ── ETA calculation ───────────────────────────────────────────────────────────
 *
 *  ETA = remainingBytes / currentSpeedBps
 *
 *  [remainingBytes] must be supplied by the caller ([RemoteDataSource]) because
 *  only it knows what is left to upload. The tracker has no knowledge of the
 *  total payload — it is a pure speed meter.
 *
 * ── Usage ─────────────────────────────────────────────────────────────────────
 *
 *  ```kotlin
 *  val tracker = UploadSpeedTracker()
 *  tracker.start()
 *
 *  // After each batch completes:
 *  tracker.recordBytes(batchSizeBytes)
 *  val speedBps = tracker.speedBps()
 *  val eta      = tracker.etaSeconds(remainingBytes)
 *
 *  tracker.reset()    // before reuse
 *  ```
 */
class UploadSpeedTracker {

    companion object {
        /** Rolling window duration. Speed samples older than this are discarded. */
        private const val WINDOW_MS = 5_000L   // 5 seconds

        /** Minimum elapsed time before a meaningful speed reading is possible. */
        private const val MIN_ELAPSED_MS = 200L
    }

    // ── State ─────────────────────────────────────────────────────────────────

    @Volatile private var startTimeMs: Long = 0L

    /** Lock-free total for overall throughput fallback. */
    private val totalBytes = AtomicLong(0L)

    /** Rolling window samples — protected by [synchronized(samples)]. */
    private data class Sample(val timestampMs: Long, val bytes: Long)
    private val samples = ArrayDeque<Sample>()

    // ── API ───────────────────────────────────────────────────────────────────

    /**
     * Marks the start of a new upload session.
     * Resets all accumulated state.
     */
    fun start() {
        startTimeMs = System.currentTimeMillis()
        totalBytes.set(0L)
        synchronized(samples) { samples.clear() }
    }

    /** Alias for [start] — call before reusing the tracker for a new upload. */
    fun reset() = start()

    /**
     * Records that [bytes] were successfully uploaded right now.
     *
     * Thread-safe — called concurrently from parallel batch coroutines.
     */
    fun recordBytes(bytes: Long) {
        if (bytes <= 0L) return
        totalBytes.addAndGet(bytes)
        val now = System.currentTimeMillis()
        synchronized(samples) {
            samples.addLast(Sample(now, bytes))
            // Evict samples older than WINDOW_MS to keep the list small
            val cutoff = now - WINDOW_MS
            while (samples.isNotEmpty() && samples.first().timestampMs < cutoff) {
                samples.removeFirst()
            }
        }
    }

    /**
     * Returns the current upload speed in bytes per second.
     *
     * Uses the rolling [WINDOW_MS] window when enough data exists.
     * Falls back to overall average when the window is too fresh or too sparse.
     *
     * Returns 0 if no data has been recorded yet.
     */
    fun speedBps(): Long {
        val now     = System.currentTimeMillis()
        val elapsed = now - startTimeMs
        if (elapsed < MIN_ELAPSED_MS) return 0L

        val windowBytes: Long
        val windowDurationMs: Long

        synchronized(samples) {
            if (samples.isEmpty()) return 0L
            val cutoff = now - WINDOW_MS
            val windowSamples = samples.filter { it.timestampMs >= cutoff }
            windowBytes       = windowSamples.sumOf { it.bytes }
            val oldest        = windowSamples.minOfOrNull { it.timestampMs } ?: now
            windowDurationMs  = (now - oldest).coerceAtLeast(1L)
        }

        // Window speed — most accurate when ≥2 s of data are in the window
        if (windowDurationMs >= MIN_ELAPSED_MS && windowBytes > 0L) {
            return (windowBytes * 1000L / windowDurationMs)
        }

        // Fallback: simple overall average
        val total = totalBytes.get()
        if (total <= 0L) return 0L
        return (total * 1000L / elapsed.coerceAtLeast(1L))
    }

    /**
     * Returns estimated seconds remaining given [remainingBytes] to upload.
     *
     * Returns 0 if speed is not yet measurable (too little data).
     * Returns [Long.MAX_VALUE] / indicates "unknown" if speed is 0.
     *
     * Callers should treat values > 3600 (1 hour) as "unknown".
     */
    fun etaSeconds(remainingBytes: Long): Long {
        if (remainingBytes <= 0L) return 0L
        val speed = speedBps()
        if (speed <= 0L) return 0L
        return (remainingBytes.toDouble() / speed).roundToLong().coerceAtLeast(1L)
    }

    /**
     * Returns a human-readable speed string, e.g. "↑ 2.4 MB/s" or "↑ 850 KB/s".
     */
    fun speedLabel(): String {
        val bps = speedBps()
        return when {
            bps <= 0L              -> ""
            bps >= 1_048_576L      -> "↑ ${"%.1f".format(bps / 1_048_576.0)} MB/s"
            bps >= 1_024L          -> "↑ ${(bps / 1024L)} KB/s"
            else                   -> "↑ $bps B/s"
        }
    }

    /**
     * Returns a human-readable ETA string, e.g. "ETA 12s" or "ETA <1s".
     * Returns empty string when ETA is not yet computable.
     */
    fun etaLabel(remainingBytes: Long): String {
        val eta = etaSeconds(remainingBytes)
        return when {
            eta <= 0L    -> ""
            eta < 1L     -> "ETA <1s"
            eta < 60L    -> "ETA ${eta}s"
            eta < 3600L  -> "ETA ${eta / 60}m ${eta % 60}s"
            else         -> ""   // > 1 hour — show nothing; likely stalled
        }
    }
}
