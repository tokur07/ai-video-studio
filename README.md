# AI Video Studio

Starter Android APK + cloud-first backend. Proyek ini disiapkan agar dapat dikelola dari Android/Termux dan APK dibangun melalui GitHub Actions.

## Isi paket
- Android app Kotlin + Jetpack Compose: dashboard, pilihan jenis video, mode otomatis/manual, navigasi.
- GitHub Actions workflow untuk membangun debug APK.
- Supabase SQL awal dengan Row Level Security.
- Cloudflare Worker starter dengan endpoint `/health`.
- Enam AI Skills modular.
- Renderer slideshow FFmpeg minimal.

## Build APK dari Termux
1. Ekstrak ZIP.
2. Buka Termux:
   ```bash
   pkg update
   pkg install git
   termux-setup-storage
   ```
3. Masuk ke folder proyek, misalnya:
   ```bash
   cd ~/storage/downloads/ai-video-studio
   ```
4. Buat repository **Public** di GitHub bernama `ai-video-studio`.
5. Jalankan:
   ```bash
   git init
   git add .
   git commit -m "Initial AI Video Studio"
   git branch -M main
   git remote add origin https://github.com/USERNAME/ai-video-studio.git
   git push -u origin main
   ```
   Ganti `USERNAME` dengan username GitHub.
6. Buka GitHub → Actions → workflow **Build Android APK**.
7. Setelah berhasil, unduh artifact `AI-Video-Studio-debug-apk`. Di dalamnya ada `app-debug.apk`.

## Versi dan batasan
Build memakai JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.9.2, Kotlin 2.1.20, compileSdk 35.
Belum termasuk integrasi provider AI aktif, autentikasi cloud produksi, dan layanan rendering cloud permanen. Kuota gratis provider dapat berubah. API key tidak boleh ditanam di APK.
