package com.tikhd.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikhd.app.Compressor
import com.tikhd.app.Storage
import com.tikhd.app.VideoProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class Phase { Idle, Reading, Ready, Running, Saving, Done, Error }

@Composable
fun CompressScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val compressor = remember { Compressor(ctx) }

    var phase by remember { mutableStateOf(Phase.Idle) }
    var srcUri by remember { mutableStateOf<Uri?>(null) }
    var before by remember { mutableStateOf<VideoProbe.Info?>(null) }
    var after by remember { mutableStateOf<VideoProbe.Info?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf("") }
    var cacheFile by remember { mutableStateOf<File?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            compressor.cancel()
            cacheFile?.delete()
        }
    }

    LaunchedEffect(phase) {
        if (phase == Phase.Running) {
            while (isActive) {
                compressor.progress()?.let { progress = it }
                delay(250)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            srcUri = uri
            phase = Phase.Reading
            scope.launch {
                try {
                    val info = withContext(Dispatchers.IO) { VideoProbe.probe(ctx, uri) }
                    before = info
                    after = null
                    progress = 0f
                    phase = Phase.Ready
                } catch (e: Exception) {
                    error = "Gagal membaca video: ${e.message}"
                    phase = Phase.Error
                }
            }
        }
    }

    fun startCompress() {
        val uri = srcUri ?: return
        val info = before ?: return
        val out = File(ctx.cacheDir, "compress_${Storage.timestamp()}.mp4")
        cacheFile = out
        progress = 0f
        phase = Phase.Running
        try {
            compressor.start(
                input = uri,
                info = info,
                output = out,
                onDone = {
                    phase = Phase.Saving
                    scope.launch {
                        try {
                            val saved = withContext(Dispatchers.IO) {
                                val o = Storage.open(ctx, "TikHD_compress_${Storage.timestamp()}.mp4")
                                try {
                                    out.inputStream().use { it.copyTo(o.stream, 64 * 1024) }
                                } catch (e: Exception) {
                                    try { o.stream.close() } catch (_: Exception) {}
                                    throw e
                                }
                                o.finish()
                                VideoProbe.probe(out)
                            }
                            after = saved
                            progress = 1f
                            phase = Phase.Done
                            Toast.makeText(ctx, "Compress selesai, tersimpan di Movies/${Storage.FOLDER}", Toast.LENGTH_LONG).show()
                        } catch (e: Exception) {
                            error = "Gagal menyimpan hasil: ${e.message}"
                            phase = Phase.Error
                        } finally {
                            out.delete()
                            cacheFile = null
                        }
                    }
                },
                onError = { e ->
                    out.delete()
                    cacheFile = null
                    error = "Compress gagal: ${e.message ?: e.javaClass.simpleName}"
                    phase = Phase.Error
                }
            )
        } catch (e: Exception) {
            out.delete()
            cacheFile = null
            error = "Compress gagal: ${e.message ?: e.javaClass.simpleName}"
            phase = Phase.Error
        }
    }

    Column(Modifier.fillMaxSize()) {
        GlassTopBar(title = "Compress", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Appear(0) {
                Text(
                    "Resolusi dan fps tidak diubah. Video dire-encode ke H.264 dengan bitrate yang lebih hemat.",
                    color = Palette.InkSoft,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            Spacer(Modifier.height(18.dp))

            AnimatedContent(
                targetState = phase,
                transitionSpec = {
                    (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 8 }) togetherWith fadeOut(tween(150))
                },
                label = "compressPhase"
            ) { p ->
                Column(Modifier.fillMaxWidth()) {
                    when (p) {
                        Phase.Idle -> {
                            GradientButton(
                                "Pilih video",
                                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = listOf(Palette.Teal, Palette.Blue)
                            )
                        }

                        Phase.Reading -> StatusCard("Membaca info video…")

                        Phase.Ready -> {
                            before?.let { SourceCard("Video asli", it) }
                            Spacer(Modifier.height(16.dp))
                            GradientButton(
                                "Mulai compress",
                                onClick = { startCompress() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = listOf(Palette.Teal, Palette.Blue)
                            )
                            Spacer(Modifier.height(12.dp))
                            GradientButton(
                                "Ganti video",
                                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = listOf(Palette.InkSoft, Palette.Ink)
                            )
                        }

                        Phase.Running -> {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(20.dp)) {
                                    Text("Lagi compress…", color = Palette.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(14.dp))
                                    GlassProgress(progress)
                                    Spacer(Modifier.height(8.dp))
                                    Text("${(progress * 100).toInt()}%", color = Palette.InkSoft, fontSize = 14.sp)
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            GradientButton(
                                "Batal",
                                onClick = {
                                    compressor.cancel()
                                    cacheFile?.delete()
                                    cacheFile = null
                                    phase = Phase.Ready
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = listOf(Palette.Red, Palette.Pink)
                            )
                        }

                        Phase.Saving -> StatusCard("Menyimpan ke Movies/${Storage.FOLDER}…")

                        Phase.Done -> {
                            val b = before
                            val a = after
                            if (b != null && a != null) {
                                ResultCard(b, a)
                            }
                            Spacer(Modifier.height(16.dp))
                            GradientButton(
                                "Compress video lain",
                                onClick = {
                                    phase = Phase.Idle
                                    before = null
                                    after = null
                                    srcUri = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = listOf(Palette.Teal, Palette.Blue)
                            )
                        }

                        Phase.Error -> {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Text(error, color = Palette.Red, fontSize = 15.sp, modifier = Modifier.padding(20.dp))
                            }
                            Spacer(Modifier.height(16.dp))
                            GradientButton(
                                "Coba lagi",
                                onClick = { phase = if (before != null) Phase.Ready else Phase.Idle },
                                modifier = Modifier.fillMaxWidth(),
                                colors = listOf(Palette.Teal, Palette.Blue)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(text: String) {
    GlassCard(Modifier.fillMaxWidth()) {
        Text(text, color = Palette.Ink, fontSize = 16.sp, modifier = Modifier.padding(20.dp))
    }
}

@Composable
private fun SourceCard(title: String, info: VideoProbe.Info) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(title, color = Palette.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            InfoRow("Resolusi", resText(info))
            InfoRow("FPS", fpsText(info))
            InfoRow("Ukuran", sizeText(info))
            InfoRow("Bitrate", mbpsText(info))
        }
    }
}

@Composable
private fun ResultCard(b: VideoProbe.Info, a: VideoProbe.Info) {
    val saved = if (b.sizeBytes > 0) ((1.0 - a.sizeBytes.toDouble() / b.sizeBytes) * 100).toInt() else 0
    val sameSpecs = a.width == b.width && a.height == b.height && a.fpsRounded == b.fpsRounded

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("Selesai", color = Palette.Green, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                "Tersimpan di Movies/${Storage.FOLDER}",
                color = Palette.InkSoft,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
            )
            InfoRow("Ukuran", "${sizeText(b)} → ${sizeText(a)}")
            InfoRow("Hemat", "$saved%")
            InfoRow("Resolusi", if (sameSpecs) resText(a) else "${resText(b)} → ${resText(a)}")
            InfoRow("FPS", if (a.fpsRounded == b.fpsRounded) fpsText(a) else "${fpsText(b)} → ${fpsText(a)}")
            InfoRow("Bitrate", "${mbpsText(b)} → ${mbpsText(a)}")
            if (!sameSpecs) {
                Text(
                    "Perhatian: resolusi/fps hasil beda dari aslinya (encoder HP ini tidak mendukung spek aslinya).",
                    color = Palette.Red,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
