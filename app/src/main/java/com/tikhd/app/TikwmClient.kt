package com.tikhd.app

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Downloads a TikTok video through the third-party tikwm.com API (may change or stop working). */
object TikwmClient {
    private const val UA =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    class DownloadException(msg: String) : Exception(msg)

    /** Resolves the video URL (HD version if the service offers one). */
    fun resolveVideoUrl(tiktokLink: String): String {
        val api = "https://www.tikwm.com/api/?hd=1&url=" + URLEncoder.encode(tiktokLink.trim(), "UTF-8")
        val conn = (URL(api).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 25000
            setRequestProperty("User-Agent", UA)
        }
        try {
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            if (json.optInt("code", -1) != 0) {
                throw DownloadException(json.optString("msg", "Link tidak valid atau video tidak ditemukan"))
            }
            val data = json.getJSONObject("data")
            val hd = data.optString("hdplay", "")
            val sd = data.optString("play", "")
            val chosen = if (hd.isNotBlank()) hd else sd
            if (chosen.isBlank()) throw DownloadException("Layanan tidak memberi link video")
            return absolute(chosen)
        } finally {
            conn.disconnect()
        }
    }

    private fun absolute(u: String): String =
        if (u.startsWith("http")) u else "https://www.tikwm.com" + (if (u.startsWith("/")) u else "/$u")

    /** Downloads [videoUrl] to [target]. [onProgress] gets 0..1 when the length is known. */
    fun download(videoUrl: String, target: File, onProgress: (Float) -> Unit = {}) {
        val conn = (URL(videoUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", UA)
        }
        try {
            if (conn.responseCode !in 200..299) {
                throw DownloadException("Download gagal (HTTP ${conn.responseCode})")
            }
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                FileOutputStream(target).use { out ->
                    val buf = ByteArray(64 * 1024)
                    var read: Int
                    var sum = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        out.write(buf, 0, read)
                        sum += read
                        if (total > 0) onProgress((sum.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
