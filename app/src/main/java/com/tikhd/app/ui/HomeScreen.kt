package com.tikhd.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Compress
import androidx.compose.material.icons.rounded.FactCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun HomeScreen(
    onCompress: () -> Unit,
    onPatch: () -> Unit,
    onUpload: () -> Unit,
    onCheckVt: () -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Appear(0) {
            Column {
                Text("TikHD", color = Palette.Ink, fontSize = 40.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Upload video TikTok jadi HD",
                    color = Palette.InkSoft,
                    fontSize = 16.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Appear(1, Modifier.weight(1f)) {
                Tile("Compress", "Kecilin ukuran, kualitas tetap", Icons.Rounded.Compress, Palette.Teal, onCompress)
            }
            Appear(2, Modifier.weight(1f)) {
                Tile("Patch", "Proses lewat Vague Transcode", Icons.Rounded.AutoFixHigh, Palette.Violet, onPatch)
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Appear(3, Modifier.weight(1f)) {
                Tile("Upload", "Buka TikTok Studio", Icons.Rounded.CloudUpload, Palette.Pink, onUpload)
            }
            Appear(4, Modifier.weight(1f)) {
                Tile("Cek VT", "Cek HD video dari link", Icons.Rounded.FactCheck, Palette.Blue, onCheckVt)
            }
        }

        Spacer(Modifier.height(24.dp))

        Appear(5) {
            Text(
                "Hasil compress dan patch disimpan di Movies/TikHD",
                color = Palette.InkSoft,
                fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Tile(title: String, subtitle: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.92f),
        onClick = onClick
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            IconBubble(color) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Column {
                Text(title, color = Palette.Ink, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    subtitle,
                    color = Palette.InkSoft,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
