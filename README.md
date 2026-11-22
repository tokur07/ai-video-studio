# AI Video Studio

Starter Android APK + cloud-first backend. Proyek ini disiapkan agar dapat dikelola dari Android/Termux dan APK dibangun melalui GitHub Actions.

## Isi paket
- Android app Kotlin + Jetpack Compose: dashboard UI baru, pilihan jenis video, mode otomatis/manual, navigasi, dan penyimpanan draft proyek lokal.
- GitHub Actions workflow untuk membangun debug APK.
- Supabase SQL awal dengan Row Level Security.
- Cloudflare Worker starter dengan endpoint `/health`.
- Enam AI Skills modular.
- Renderer slideshow FFmpeg minimal.

## Versi dan batasan
Build memakai JDK 17, Gradle 8.11.1, Android Gradle Plugin 8.9.2, Kotlin 2.1.20, compileSdk 35.
Versi 0.2 menyimpan draft proyek secara lokal di perangkat. Integrasi Supabase cloud, autentikasi, provider AI aktif, dan layanan rendering cloud belum diaktifkan. Kuota gratis provider dapat berubah. API key tidak boleh ditanam di APK.
