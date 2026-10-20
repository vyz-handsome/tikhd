package com.tikhd.app

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.tikhd.app.ui.GlassBackground
import com.tikhd.app.ui.GlassCard
import com.tikhd.app.ui.GlassProgress
import com.tikhd.app.ui.GlassTopBar
import com.tikhd.app.ui.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Hosts one WebView. MODE_PATCH opens tikquick (auto-scroll + auto-save), MODE_UPLOAD opens TikTok Studio. */
class WebActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_MODE = "mode"
        const val MODE_PATCH = "patch"
        const val MODE_UPLOAD = "upload"

        private const val PATCH_URL = "https://transcode.vague-infinity.com/"
        private const val UPLOAD_URL = "https://www.tiktok.com/tiktokstudio/upload"

        /** Desktop Chrome UA built from this device's real WebView version (no "wv" marker). */
        private fun desktopChromeUa(ctx: Context): String {
            val ver = Regex("Chrome/([\\d.]+)")
                .find(WebSettings.getDefaultUserAgent(ctx))?.groupValues?.get(1) ?: "124.0.0.0"
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$ver Safari/537.36"
        }

        fun launch(ctx: Context, mode: String) {
            ctx.startActivity(Intent(ctx, WebActivity::class.java).putExtra(EXTRA_MODE, mode))
        }

        /** Finds the "Pilih video" / dropzone element and scrolls it into view; retries while the page builds itself. */
        private const val SCROLL_JS = """
            (function() {
              var tries = 0;
              var RX = /Pilih video|Choose video|Select video|Drop a video here/i;
              function deepest(root) {
                var all = root.querySelectorAll('*');
                var hit = null;
                for (var i = 0; i < all.length; i++) {
                  var e = all[i];
                  var txt = (e.innerText || '');
                  if (txt.length < 200 && RX.test(txt)) {
                    var childHas = false;
                    for (var c = 0; c < e.children.length; c++) {
                      if (RX.test(e.children[c].innerText || '')) { childHas = true; break; }
                    }
                    if (!childHas) { hit = e; break; }
                  }
                }
                return hit;
              }
              var timer = setInterval(function() {
                var el = deepest(document.body);
                if (el) {
                  el.scrollIntoView({behavior: 'smooth', block: 'center'});
                  clearInterval(timer);
                } else if (++tries > 24) {
                  clearInterval(timer);
                }
              }, 500);
            })();
        """
    }

    private lateinit var mode: String
    private var webView: WebView? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private var pageProgress by mutableFloatStateOf(0f)
    private var banner by mutableStateOf<String?>(null)

    private val fileChooser =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            fileCallback?.onReceiveValue(uris)
            fileCallback = null
        }

    // ---- blob download bridge (called from JS, on a background thread) ----
    private var blobOut: Storage.Out? = null

    inner class BlobBridge {
        @JavascriptInterface
        fun begin(mime: String, size: Double) {
            try {
                blobOut = Storage.open(this@WebActivity, "TikHD_patch_${Storage.timestamp()}.mp4", "video/mp4")
                runOnUiThread { banner = "Mengunduh hasil patch…" }
            } catch (e: Exception) {
                fail(e.message ?: "gagal membuka file")
            }
        }

        @JavascriptInterface
        fun chunk(b64: String) {
            try {
                blobOut?.stream?.write(Base64.decode(b64, Base64.DEFAULT))
            } catch (e: Exception) {
                fail(e.message ?: "gagal menulis file")
            }
        }

        @JavascriptInterface
        fun end() {
            try {
                blobOut?.finish()
                blobOut = null
                savedOk()
            } catch (e: Exception) {
                fail(e.message ?: "gagal menyimpan file")
            }
        }

        @JavascriptInterface
        fun fail(msg: String) {
            try { blobOut?.stream?.close() } catch (_: Exception) {}
            blobOut = null
            runOnUiThread {
                banner = "Gagal menyimpan: $msg"
                Toast.makeText(this@WebActivity, "Gagal menyimpan: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun savedOk() {
        runOnUiThread {
            banner = "Selesai! Tersimpan di Movies/${Storage.FOLDER}"
            Toast.makeText(this, "Hasil patch tersimpan di Movies/${Storage.FOLDER}", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mode = intent.getStringExtra(EXTRA_MODE) ?: MODE_PATCH
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val isPatch = mode == MODE_PATCH
        val title = if (isPatch) "Patch" else "Upload"
        val url = if (isPatch) PATCH_URL else UPLOAD_URL

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val wv = webView
                if (wv != null && wv.canGoBack()) wv.goBack() else finish()
            }
        })

        setContent {
            GlassBackground {
                Column(Modifier.fillMaxSize()) {
                    GlassTopBar(
                        title = title,
                        onBack = { onBackPressedDispatcher.onBackPressed() },
                        actions = {
                            GlassCard(corner = 22.dp, onClick = { banner = null; webView?.reload() }, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    Icons.Rounded.Refresh,
                                    contentDescription = "Muat ulang",
                                    tint = Palette.Ink,
                                    modifier = Modifier.align(Alignment.Center).size(22.dp)
                                )
                            }
                        }
                    )

                    if (pageProgress in 0.01f..0.99f) {
                        GlassProgress(
                            progress = pageProgress,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                        )
                    }

                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx -> createWebView(ctx, isPatch).also { webView = it; it.loadUrl(url) } }
                        )

                        BannerHost(
                            banner = banner,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(16.dp),
                            onDismiss = { banner = null }
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun createWebView(ctx: Context, isPatch: Boolean): WebView {
        val wv = WebView(ctx)
        wv.setBackgroundColor(android.graphics.Color.WHITE)

        with(wv.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = false
            allowContentAccess = true
            databaseEnabled = true
            setSupportMultipleWindows(true)
            if (!isPatch) {
                userAgentString = desktopChromeUa(ctx)
                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
            }
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }

        wv.addJavascriptInterface(BlobBridge(), "TikBridge")

        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme ?: return true
                return !(scheme == "http" || scheme == "https" || scheme == "blob" || scheme == "about")
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                pageProgress = 0.05f
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: android.webkit.WebResourceError) {
                if (request.isForMainFrame) {
                    banner = "Gagal memuat halaman: ${error.description}. Cek koneksi lalu tekan muat ulang."
                }
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, errorResponse: android.webkit.WebResourceResponse) {
                if (request.isForMainFrame && errorResponse.statusCode >= 400) {
                    banner = "Halaman membalas error ${errorResponse.statusCode}. Coba muat ulang."
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                pageProgress = 1f
                CookieManager.getInstance().flush()
                if (isPatch) view.evaluateJavascript(SCROLL_JS, null)
            }
        }

        wv.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                pageProgress = newProgress / 100f
            }

            // Login / OAuth popups: load them in the same WebView instead of dropping them.
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message
            ): Boolean {
                val temp = WebView(view.context)
                temp.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, request: WebResourceRequest): Boolean {
                        view.loadUrl(request.url.toString())
                        v.destroy()
                        return true
                    }
                }
                (resultMsg.obj as WebView.WebViewTransport).webView = temp
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: WebChromeClient.FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = filePathCallback
                return try {
                    fileChooser.launch(fileChooserParams.createIntent())
                    true
                } catch (e: Exception) {
                    fileCallback = null
                    filePathCallback.onReceiveValue(null)
                    false
                }
            }
        }

        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            handleDownload(url, userAgent, contentDisposition, mimeType)
        }

        return wv
    }

    private fun handleDownload(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        if (url.startsWith("blob:")) {
            val js = """
                (function() {
                  var url = '${url.replace("'", "\\'")}';
                  fetch(url).then(function(r) { return r.blob(); }).then(function(b) {
                    var CHUNK = 3 * 1024 * 1024;
                    var off = 0;
                    TikBridge.begin(b.type || 'video/mp4', b.size);
                    function next() {
                      if (off >= b.size) { TikBridge.end(); return; }
                      var fr = new FileReader();
                      fr.onload = function() {
                        var s = fr.result;
                        TikBridge.chunk(s.substring(s.indexOf(',') + 1));
                        off += CHUNK;
                        next();
                      };
                      fr.onerror = function() { TikBridge.fail('gagal membaca blob'); };
                      fr.readAsDataURL(b.slice(off, off + CHUNK));
                    }
                    next();
                  }).catch(function(e) { TikBridge.fail(String(e)); });
                })();
            """.trimIndent()
            webView?.evaluateJavascript(js, null)
            return
        }

        val guessed = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val ext = guessed.substringAfterLast('.', "").lowercase()
        val finalExt = if (ext in setOf("mp4", "mov", "webm", "mkv", "m4v")) ext else "mp4"
        val mime = when (finalExt) {
            "mov" -> "video/quicktime"
            "webm" -> "video/webm"
            "mkv" -> "video/x-matroska"
            else -> "video/mp4"
        }
        val name = "TikHD_patch_${Storage.timestamp()}.$finalExt"

        banner = "Mengunduh hasil patch…"
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val conn = URL(url).openConnection() as HttpURLConnection
                    conn.connectTimeout = 20000
                    conn.readTimeout = 60000
                    userAgent?.let { conn.setRequestProperty("User-Agent", it) }
                    CookieManager.getInstance().getCookie(url)?.let { conn.setRequestProperty("Cookie", it) }
                    try {
                        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
                        val out = Storage.open(this@WebActivity, name, mime)
                        try {
                            conn.inputStream.use { it.copyTo(out.stream, 64 * 1024) }
                        } catch (e: Exception) {
                            try { out.stream.close() } catch (_: Exception) {}
                            throw e
                        }
                        out.finish()
                    } finally {
                        conn.disconnect()
                    }
                }
                savedOk()
            } catch (e: Exception) {
                banner = "Gagal mengunduh: ${e.message}"
                Toast.makeText(this@WebActivity, "Gagal mengunduh: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        CookieManager.getInstance().flush()
    }

    override fun onDestroy() {
        webView?.let {
            it.stopLoading()
            it.destroy()
        }
        webView = null
        super.onDestroy()
    }
}


/** Dipisah ke fungsi top-level biar AnimatedVisibility gak ketuker sama versi ColumnScope (receiver implisit dari Column). */
@androidx.compose.runtime.Composable
private fun BannerHost(banner: String?, modifier: Modifier, onDismiss: () -> Unit) {
    AnimatedVisibility(
        visible = banner != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it }
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth(), corner = 22.dp, onClick = onDismiss) {
            Text(
                banner ?: "",
                color = Palette.Ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)
            )
        }
    }
}
