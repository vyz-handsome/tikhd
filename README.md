# TikHD

App Android (Kotlin + Jetpack Compose) buat bikin video TikTok upload jadi HD.

- **Compress**: re-encode H.264 (Media3 Transformer), resolusi dan fps tidak diubah, bitrate dipotong sekitar 50%.
- **Patch**: WebView ke https://transcode.vague-infinity.com/, auto-scroll ke tombol "Pilih video", hasil auto-download ke `Movies/TikHD`.
- **Upload**: WebView terpisah ke https://www.tiktok.com/tiktokstudio/upload (login tersimpan).
- **Cek VT**: tempel link TikTok, video diunduh lewat tikwm.com, dibaca resolusi / fps / MB / Mbps, lalu dihapus. HD = minimal 1080p dan 30 fps ke atas.

## Build APK lewat GitHub

1. Push semua isi folder ini ke repo GitHub (branch `main`).
2. Buka tab **Actions** lalu workflow **Build APK** (jalan otomatis tiap push, atau klik *Run workflow*).
3. Download artifact **TikHD-apk** di akhir run, unzip, lalu install `app-debug.apk`.

Minimal Android 8.0 (API 26).
