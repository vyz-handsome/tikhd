package com.tikhd.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikhd.app.Storage
import com.tikhd.app.TikwmClient
import com.tikhd.app.VideoProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class VtPhase { Input, Loading, Result, Error }

@Composable
fun CheckVtScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var phase by remember { mutableStateOf(VtPhase.Input) }
    var link by remember { mutableStateOf("") }
    var stage by remember { mutableStateOf("") }
    var dlProgress by remember { mutableFloatStateOf(0f) }
    var info by remember { mutableStateOf<VideoProbe.Info?>(null) }
    var error by remember { mutableStateOf("") }

    fun check() {
        val l = link.trim()
        if (!l.startsWith("http") || !l.contains("tiktok.com")) {
            error = "Masukin link video TikTok yang valid (tiktok.com atau vm.tiktok.com)."
            phase = VtPhase.Error
            return
        }
        phase = VtPhase.Loading
        dlProgress = 0f
        scope.launch {
            val tmp = File(ctx.cacheDir, "vt_${Storage.timestamp()}.mp4")
            try {
                val result = withContext(Dispatchers.IO) {
                    stage = "Mengambil link video…"
                    val url = TikwmClient.resolveVideoUrl(l)
                    stage = "Mengunduh video…"
                    TikwmClient.download(url, tmp) { dlProgress = it }
                    stage = "Membaca info video…"
                    VideoProbe.probe(tmp)
                }
                info = result
                phase = VtPhase.Result
            } catch (e: Exception) {
                error = e.message ?: "Gagal mengecek video"
                phase = VtPhase.Error
            } finally {
                tmp.delete()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        GlassTopBar(title = "Cek VT", onBack = onBack)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Appear(0) {
                Text(
                    "Tempel link video TikTok. Video diunduh sebentar, dicek, lalu otomatis dihapus.",
                    color = Palette.InkSoft,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            }
            Spacer(Modifier.height(18.dp))

            AnimatedContent(
                targetState = phase,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(150)) },
                label = "vtPhase"
            ) { p ->
                Column(Modifier.fillMaxWidth()) {
                    when (p) {
                        VtPhase.Input, VtPhase.Error -> {
                            GlassCard(Modifier.fillMaxWidth(), corner = 22.dp) {
                                TextField(
                                    value = link,
                                    onValueChange = { link = it },
                                    placeholder = { Text("https://vm.tiktok.com/…", color = Palette.InkSoft) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        disabledContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent,
                                        cursorColor = Palette.Blue,
                                        focusedTextColor = Palette.Ink,
                                        unfocusedTextColor = Palette.Ink
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Tempel dari clipboard",
                                color = Palette.Blue,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { clipboard.getText()?.text?.let { link = it.trim() } }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                            if (p == VtPhase.Error) {
                                Spacer(Modifier.height(8.dp))
                                Text(error, color = Palette.Red, fontSize = 14.sp)
                            }
                            Spacer(Modifier.height(14.dp))
                            GradientButton(
                                "Cek video",
                                onClick = { check() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = listOf(Palette.Blue, Palette.Violet),
                                enabled = link.isNotBlank()
                            )
                        }

                        VtPhase.Loading -> {
                            GlassCard(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(20.dp)) {
                                    Text(stage, color = Palette.Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(Modifier.height(14.dp))
                                    GlassProgress(dlProgress, colors = listOf(Palette.Blue, Palette.Violet))
                                }
                            }
                        }

                        VtPhase.Result -> {
                            info?.let { ResultView(it) }
                            Spacer(Modifier.height(16.dp))
                            GradientButton(
                                "Cek link lain",
                                onClick = { link = ""; phase = VtPhase.Input },
                                modifier = Modifier.fillMaxWidth(),
                                colors = listOf(Palette.Blue, Palette.Violet)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultView(i: VideoProbe.Info) {
    val hd = i.isHd
    val color = if (hd) Palette.Green else Palette.Red

    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            AnimatedContent(
                targetState = hd,
                transitionSpec = {
                    scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy), initialScale = 0.6f) + fadeIn() togetherWith fadeOut()
                },
                label = "badge"
            ) { isHd ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(color.copy(alpha = 0.14f))
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (isHd) "HD" else "SD",
                        color = color,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            InfoRow("Resolusi", resText(i))
            InfoRow("FPS", fpsText(i))
            InfoRow("Jumlah MB", sizeText(i))
            InfoRow("Jumlah Mbps", mbpsText(i))
            Text(
                "HD = minimal 1080p dan 30 fps ke atas. Angka dibaca dari file yang diberikan layanan downloader, bisa beda dari file asli di akun pembuatnya.",
                color = Palette.InkSoft,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 10.dp)
            )
        }
    }
}
