package com.tikhd.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tikhd.app.ui.CheckVtScreen
import com.tikhd.app.ui.CompressScreen
import com.tikhd.app.ui.GlassBackground
import com.tikhd.app.ui.HomeScreen

private enum class Screen { Home, Compress, CheckVt }

class MainActivity : ComponentActivity() {

    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        // Android 8-9 needs this permission to write into Movies/TikHD.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        setContent {
            var screen by remember { mutableStateOf(Screen.Home) }
            BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

            GlassBackground {
                AnimatedContent(
                    targetState = screen,
                    transitionSpec = {
                        if (targetState == Screen.Home) {
                            (fadeIn(tween(280)) + slideInHorizontally(tween(320)) { -it / 6 }) togetherWith fadeOut(tween(180))
                        } else {
                            (fadeIn(tween(280)) + slideInHorizontally(tween(320)) { it / 6 }) togetherWith fadeOut(tween(180))
                        }
                    },
                    label = "screen"
                ) { s ->
                    when (s) {
                        Screen.Home -> HomeScreen(
                            onCompress = { screen = Screen.Compress },
                            onPatch = { WebActivity.launch(this@MainActivity, WebActivity.MODE_PATCH) },
                            onUpload = { WebActivity.launch(this@MainActivity, WebActivity.MODE_UPLOAD) },
                            onCheckVt = { screen = Screen.CheckVt }
                        )
                        Screen.Compress -> CompressScreen(onBack = { screen = Screen.Home })
                        Screen.CheckVt -> CheckVtScreen(onBack = { screen = Screen.Home })
                    }
                }
            }
        }
    }
}
