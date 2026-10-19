package com.tikhd.app

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import kotlin.math.roundToInt

/** Reads resolution, fps, size and bitrate of a local video. */
object VideoProbe {

    data class Info(
        val width: Int,
        val height: Int,
        val fps: Float,
        val bitrateBps: Long,
        val durationMs: Long,
        val sizeBytes: Long
    ) {
        val fpsRounded: Int get() = fps.roundToInt()
        val shortSide: Int get() = minOf(width, height)
        val sizeMb: Double get() = sizeBytes / (1024.0 * 1024.0)
        val mbps: Double get() = bitrateBps / 1_000_000.0

        /** HD = short side at least 1080 px and at least 30 fps. */
        val isHd: Boolean get() = shortSide >= 1080 && fpsRounded >= 30
    }

    fun probe(ctx: Context, uri: Uri): Info {
        val size = try {
            ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        } catch (e: Exception) {
            -1L
        }
        return probeInternal(
            size,
            { it.setDataSource(ctx, uri) },
            { it.setDataSource(ctx, uri, null) }
        )
    }

    fun probe(file: File): Info {
        return probeInternal(
            file.length(),
            { it.setDataSource(file.absolutePath) },
            { it.setDataSource(file.absolutePath) }
        )
    }

    private fun probeInternal(
        sizeBytes: Long,
        mmrInit: (MediaMetadataRetriever) -> Unit,
        exInit: (MediaExtractor) -> Unit
    ): Info {
        var w = 0
        var h = 0
        var durationMs = 0L
        var metaBitrate = 0L

        val mmr = MediaMetadataRetriever()
        try {
            mmrInit(mmr)
            fun key(k: Int) = mmr.extractMetadata(k)
            w = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            h = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rot = key(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rot == 90 || rot == 270) {
                val t = w; w = h; h = t
            }
            durationMs = key(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            metaBitrate = key(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull() ?: 0L
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }

        var fps = 0f
        val ex = MediaExtractor()
        try {
            exInit(ex)
            for (i in 0 until ex.trackCount) {
                val fmt = ex.getTrackFormat(i)
                val mime = fmt.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("video/")) continue
                ex.selectTrack(i)

                var count = 0
                var minUs = Long.MAX_VALUE
                var maxUs = Long.MIN_VALUE
                while (true) {
                    val t = ex.sampleTime
                    if (t < 0) break
                    count++
                    if (t < minUs) minUs = t
                    if (t > maxUs) maxUs = t
                    if (!ex.advance()) break
                }
                if (count > 1 && maxUs > minUs) {
                    fps = (count - 1) / ((maxUs - minUs) / 1_000_000f)
                }
                if (fps <= 0f && fmt.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                    fps = try {
                        fmt.getInteger(MediaFormat.KEY_FRAME_RATE).toFloat()
                    } catch (_: Exception) {
                        try { fmt.getFloat(MediaFormat.KEY_FRAME_RATE) } catch (_: Exception) { 0f }
                    }
                }
                if (w == 0 && fmt.containsKey(MediaFormat.KEY_WIDTH)) w = fmt.getInteger(MediaFormat.KEY_WIDTH)
                if (h == 0 && fmt.containsKey(MediaFormat.KEY_HEIGHT)) h = fmt.getInteger(MediaFormat.KEY_HEIGHT)
                break
            }
        } finally {
            try { ex.release() } catch (_: Exception) {}
        }

        val bitrate = if (sizeBytes > 0 && durationMs > 0) {
            sizeBytes * 8L * 1000L / durationMs
        } else {
            metaBitrate
        }

        return Info(w, h, fps, bitrate, durationMs, if (sizeBytes > 0) sizeBytes else 0L)
    }
}
