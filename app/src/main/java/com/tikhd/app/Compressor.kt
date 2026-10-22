package com.tikhd.app

import android.content.Context
import android.media.MediaCodecInfo
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File

/**
 * Re-encodes to H.264 at a lower bitrate. No scaling or frame-rate effects are added,
 * so resolution and fps stay as in the source. Must be created and used on the main thread.
 */
class Compressor(private val ctx: Context) {

    private var transformer: Transformer? = null

    /** Target = 50% of the source bitrate. Falls back to ~0.1 bit per pixel per frame if the source bitrate is unknown. */
    fun targetBitrate(info: VideoProbe.Info): Int {
        val target = if (info.bitrateBps > 0) {
            info.bitrateBps / 2
        } else {
            val fps = info.fps.coerceIn(24f, 120f)
            val pixels = (info.width.toLong() * info.height.toLong()).coerceAtLeast(1L)
            (pixels * fps * 0.1f).toLong() / 2
        }
        return target.coerceIn(1_000_000L, 40_000_000L).toInt()
    }

    fun start(
        input: Uri,
        info: VideoProbe.Info,
        output: File,
        onDone: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (output.exists()) output.delete()

        val settings = VideoEncoderSettings.Builder()
            .setBitrate(targetBitrate(info))
            .setBitrateMode(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            .build()

        val encoderFactory = DefaultEncoderFactory.Builder(ctx)
            .setRequestedVideoEncoderSettings(settings)
            .build()

        val t = Transformer.Builder(ctx)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setEncoderFactory(encoderFactory)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onDone()
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    onError(exportException)
                }
            })
            .build()

        transformer = t
        t.start(MediaItem.fromUri(input), output.absolutePath)
    }

    /** Progress 0..1, or null if not available yet. */
    fun progress(): Float? {
        val t = transformer ?: return null
        val holder = ProgressHolder()
        return if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
            holder.progress / 100f
        } else null
    }

    fun cancel() {
        try { transformer?.cancel() } catch (_: Exception) {}
        transformer = null
    }
}
