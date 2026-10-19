package com.tikhd.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tikhd.app.VideoProbe

@Composable
fun InfoRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Palette.InkSoft, fontSize = 15.sp)
        Text(value, color = Palette.Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

fun resText(i: VideoProbe.Info): String = "${i.width}x${i.height} (${i.shortSide}p)"
fun fpsText(i: VideoProbe.Info): String = "${i.fpsRounded} fps"
fun sizeText(i: VideoProbe.Info): String = String.format(java.util.Locale.US, "%.2f MB", i.sizeMb)
fun mbpsText(i: VideoProbe.Info): String = String.format(java.util.Locale.US, "%.2f Mbps", i.mbps)
